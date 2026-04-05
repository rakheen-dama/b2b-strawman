package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustReconciliationServiceMatchingTest {

  private static final String ORG_ID = "org_trust_match_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private BankStatementRepository bankStatementRepository;
  @Autowired private BankStatementLineRepository bankStatementLineRepository;
  @Autowired private TrustTransactionRepository trustTransactionRepository;

  private String tenantSchema;
  private String trustAccountId;
  private String customerId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Trust Matching Test Org", null).schemaName();

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_match_owner", "match_owner@test.com", "Match Owner", "owner");
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_match_admin", "match_admin@test.com", "Match Admin", "admin");

    // Enable the trust_accounting module
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var settings = orgSettingsService.getOrCreateForCurrentTenant();
                      settings.setEnabledModules(List.of("trust_accounting"));
                      orgSettingsRepository.save(settings);
                    }));

    // Create a trust account
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_match_owner");
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Matching Test Trust Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000001",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = TestEntityHelper.extractId(accountResult);

    // Create a customer for deposits
    customerId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Match Test Customer", "match_test_cust@test.com");
  }

  // --- Helper methods ---

  private String createDeposit(String reference, String amount, String date) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/deposit")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_match_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "amount": %s,
                          "reference": "%s",
                          "description": "Test deposit",
                          "transactionDate": "%s"
                        }
                        """
                            .formatted(customerId, amount, reference, date)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private String createPaymentApproved(String reference, String amount, String date)
      throws Exception {
    // First ensure sufficient balance with a deposit
    createDeposit("SETUP-" + reference, amount, date);

    // Create payment (AWAITING_APPROVAL)
    var payResult =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + trustAccountId + "/transactions/payment")
                    .with(TestJwtFactory.adminJwt(ORG_ID, "user_match_admin"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "customerId": "%s",
                          "amount": %s,
                          "reference": "%s",
                          "description": "Test payment",
                          "transactionDate": "%s"
                        }
                        """
                            .formatted(customerId, amount, reference, date)))
            .andExpect(status().isCreated())
            .andReturn();
    String paymentId =
        JsonPath.read(payResult.getResponse().getContentAsString(), "$.id").toString();

    // Approve with different user
    mockMvc
        .perform(
            post("/api/trust-transactions/" + paymentId + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_match_owner")))
        .andExpect(status().isOk());

    return paymentId;
  }

  private String importStatement(String csv) throws Exception {
    var csvFile =
        new MockMultipartFile(
            "file",
            "match-test-" + System.nanoTime() + ".csv",
            "text/csv",
            csv.getBytes(StandardCharsets.UTF_8));

    var result =
        mockMvc
            .perform(
                multipart("/api/trust-accounts/{accountId}/bank-statements", trustAccountId)
                    .file(csvFile)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_match_owner")))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  // --- 444.3: Auto-match tests ---

  @Test
  void autoMatch_exactReference_confidence100AndAutoMatched() throws Exception {
    // Create a deposit with known reference
    createDeposit("REF-EXACT-MATCH-001", "25000.00", "2026-03-05");

    // Import statement with matching reference
    String statementId =
        importStatement(
            """
            FNB Trust Account Statement - Account 62012345678
            Date,Description,Amount,Balance,Reference
            05/03/2026,Deposit received,25000.00,175000.00,REF-EXACT-MATCH-001
            """);

    // Run auto-match
    var result =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> reconciliationService.autoMatchStatement(UUID.fromString(statementId)));

    assertThat(result.autoMatched()).isEqualTo(1);

    // Verify the line is AUTO_MATCHED with confidence 1.00
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var lines =
                  bankStatementLineRepository.findByBankStatementIdOrderByLineNumber(
                      UUID.fromString(statementId));
              assertThat(lines).hasSize(1);
              var line = lines.getFirst();
              assertThat(line.getMatchStatus()).isEqualTo("AUTO_MATCHED");
              assertThat(line.getMatchConfidence()).isEqualByComparingTo(new BigDecimal("1.00"));
              assertThat(line.getTrustTransactionId()).isNotNull();
            });
  }

  @Test
  void autoMatch_amountAndExactDate_confidence080AndAutoMatched() throws Exception {
    // Create a deposit with unique amount and date
    createDeposit("REF-AMT-DATE-001", "31415.92", "2026-03-10");

    // Import statement with same amount and exact date, but different reference
    String statementId =
        importStatement(
            """
            FNB Trust Account Statement - Account 62012345678
            Date,Description,Amount,Balance,Reference
            10/03/2026,EFT deposit received,31415.92,206415.92,DIFFERENT-REF
            """);

    var result =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> reconciliationService.autoMatchStatement(UUID.fromString(statementId)));

    assertThat(result.autoMatched()).isEqualTo(1);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var lines =
                  bankStatementLineRepository.findByBankStatementIdOrderByLineNumber(
                      UUID.fromString(statementId));
              assertThat(lines).hasSize(1);
              var line = lines.getFirst();
              assertThat(line.getMatchStatus()).isEqualTo("AUTO_MATCHED");
              assertThat(line.getMatchConfidence()).isEqualByComparingTo(new BigDecimal("0.80"));
            });
  }

  @Test
  void autoMatch_amountAndCloseDate_confidence060StaysUnmatched() throws Exception {
    // Create a deposit with unique amount, date 2 days off from bank line
    createDeposit("REF-CLOSE-DATE-001", "17320.50", "2026-03-13");

    // Import statement with same amount but date 2 days away
    String statementId =
        importStatement(
            """
            FNB Trust Account Statement - Account 62012345678
            Date,Description,Amount,Balance,Reference
            15/03/2026,EFT deposit,17320.50,223736.42,SOME-OTHER-REF
            """);

    var result =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> reconciliationService.autoMatchStatement(UUID.fromString(statementId)));

    // Below threshold -- should NOT auto-match
    assertThat(result.autoMatched()).isEqualTo(0);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var lines =
                  bankStatementLineRepository.findByBankStatementIdOrderByLineNumber(
                      UUID.fromString(statementId));
              assertThat(lines).hasSize(1);
              var line = lines.getFirst();
              assertThat(line.getMatchStatus()).isEqualTo("UNMATCHED");
              assertThat(line.getMatchConfidence()).isEqualByComparingTo(new BigDecimal("0.60"));
            });
  }

  @Test
  void autoMatch_amountOnlyMultipleCandidates_confidence040StaysUnmatched() throws Exception {
    // Create TWO deposits with the same amount but different dates
    createDeposit("REF-DUP-AMT-A", "9999.99", "2026-03-20");
    createDeposit("REF-DUP-AMT-B", "9999.99", "2026-03-25");

    // Import statement with same amount
    String statementId =
        importStatement(
            """
            FNB Trust Account Statement - Account 62012345678
            Date,Description,Amount,Balance,Reference
            22/03/2026,Deposit received,9999.99,233736.41,NO-MATCH-REF
            """);

    var result =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> reconciliationService.autoMatchStatement(UUID.fromString(statementId)));

    assertThat(result.autoMatched()).isEqualTo(0);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var lines =
                  bankStatementLineRepository.findByBankStatementIdOrderByLineNumber(
                      UUID.fromString(statementId));
              assertThat(lines).hasSize(1);
              var line = lines.getFirst();
              assertThat(line.getMatchStatus()).isEqualTo("UNMATCHED");
              assertThat(line.getMatchConfidence()).isEqualByComparingTo(new BigDecimal("0.40"));
            });
  }

  @Test
  void autoMatch_signMismatch_excludedFromCandidates() throws Exception {
    // Create a PAYMENT (debit type) -- positive bank line should NOT match it
    createPaymentApproved("PAY-SIGN-MISMATCH", "5000.00", "2026-03-05");

    // Import statement with POSITIVE amount (credit) -- should NOT match the payment
    String statementId =
        importStatement(
            """
            FNB Trust Account Statement - Account 62012345678
            Date,Description,Amount,Balance,Reference
            05/03/2026,Credit received,5000.00,238736.41,PAY-SIGN-MISMATCH
            """);

    var result =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> reconciliationService.autoMatchStatement(UUID.fromString(statementId)));

    // The payment is a debit type -- positive bank line should not match it
    // However, the setup deposit (SETUP-PAY-SIGN-MISMATCH) IS a credit type and has
    // the same amount. The reference won't match but amount+date might.
    // The key assertion: the PAYMENT itself is not matched to the positive bank line.
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var lines =
                  bankStatementLineRepository.findByBankStatementIdOrderByLineNumber(
                      UUID.fromString(statementId));
              assertThat(lines).hasSize(1);
              var line = lines.getFirst();
              // If matched, it should NOT be matched to the payment
              if (line.getTrustTransactionId() != null) {
                var matchedTxn =
                    trustTransactionRepository.findById(line.getTrustTransactionId()).orElseThrow();
                assertThat(matchedTxn.getTransactionType()).isNotEqualTo("PAYMENT");
              }
            });
  }

  // --- 444.4: Manual match tests ---

  @Test
  void manualMatch_linksLineAndTransaction() throws Exception {
    // Create a deposit
    String depositId = createDeposit("REF-MANUAL-MATCH-001", "7777.77", "2026-03-18");

    // Import statement
    String statementId =
        importStatement(
            """
            FNB Trust Account Statement - Account 62012345678
            Date,Description,Amount,Balance,Reference
            18/03/2026,Deposit received,7777.77,246514.18,MANUAL-TEST-REF
            """);

    // Get the bank statement line ID
    UUID lineId =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () -> {
                  var lines =
                      bankStatementLineRepository.findByBankStatementIdOrderByLineNumber(
                          UUID.fromString(statementId));
                  return lines.getFirst().getId();
                });

    // Manual match
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(() -> reconciliationService.manualMatch(lineId, UUID.fromString(depositId)));

    // Verify both sides linked
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var line = bankStatementLineRepository.findById(lineId).orElseThrow();
              assertThat(line.getMatchStatus()).isEqualTo("MANUALLY_MATCHED");
              assertThat(line.getTrustTransactionId()).isEqualTo(UUID.fromString(depositId));

              var txn =
                  trustTransactionRepository.findById(UUID.fromString(depositId)).orElseThrow();
              assertThat(txn.getBankStatementLineId()).isEqualTo(lineId);

              // Verify matchedCount incremented
              var stmt =
                  bankStatementRepository.findById(UUID.fromString(statementId)).orElseThrow();
              assertThat(stmt.getMatchedCount()).isEqualTo(1);
            });
  }

  @Test
  void unmatch_clearsBothSidesAndDecrementsCount() throws Exception {
    // Create and manually match first
    String depositId = createDeposit("REF-UNMATCH-001", "3333.33", "2026-03-19");

    String statementId =
        importStatement(
            """
            FNB Trust Account Statement - Account 62012345678
            Date,Description,Amount,Balance,Reference
            19/03/2026,Deposit,3333.33,249847.51,UNMATCH-REF
            """);

    UUID lineId =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () -> {
                  var lines =
                      bankStatementLineRepository.findByBankStatementIdOrderByLineNumber(
                          UUID.fromString(statementId));
                  return lines.getFirst().getId();
                });

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(() -> reconciliationService.manualMatch(lineId, UUID.fromString(depositId)));

    // Now unmatch
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(() -> reconciliationService.unmatch(lineId));

    // Verify both sides cleared
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var line = bankStatementLineRepository.findById(lineId).orElseThrow();
              assertThat(line.getMatchStatus()).isEqualTo("UNMATCHED");
              assertThat(line.getTrustTransactionId()).isNull();
              assertThat(line.getMatchConfidence()).isNull();

              var txn =
                  trustTransactionRepository.findById(UUID.fromString(depositId)).orElseThrow();
              assertThat(txn.getBankStatementLineId()).isNull();

              // Verify matchedCount decremented
              var stmt =
                  bankStatementRepository.findById(UUID.fromString(statementId)).orElseThrow();
              assertThat(stmt.getMatchedCount()).isEqualTo(0);
            });
  }

  @Test
  void excludeLine_setsExcludedStatusAndReason() throws Exception {
    String statementId =
        importStatement(
            """
            FNB Trust Account Statement - Account 62012345678
            Date,Description,Amount,Balance,Reference
            20/03/2026,Bank fee,-50.00,249797.51,BANK-FEE-001
            """);

    UUID lineId =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () -> {
                  var lines =
                      bankStatementLineRepository.findByBankStatementIdOrderByLineNumber(
                          UUID.fromString(statementId));
                  return lines.getFirst().getId();
                });

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(() -> reconciliationService.excludeLine(lineId, "Bank fee - not a trust transaction"));

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var line = bankStatementLineRepository.findById(lineId).orElseThrow();
              assertThat(line.getMatchStatus()).isEqualTo("EXCLUDED");
              assertThat(line.getExcludedReason()).isEqualTo("Bank fee - not a trust transaction");
            });
  }

  // --- 444.5: Edge case tests ---

  @Test
  void autoMatch_skipsAlreadyMatchedTransactions() throws Exception {
    // Create a deposit with unique amount
    String depositId = createDeposit("REF-SKIP-MATCHED-001", "11111.11", "2026-03-22");

    // Import first statement and manually match the deposit
    String statementId1 =
        importStatement(
            """
            FNB Trust Account Statement - Account 62012345678
            Date,Description,Amount,Balance,Reference
            22/03/2026,Deposit,11111.11,260908.62,SKIP-MATCH-REF
            """);

    UUID lineId1 =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(
                () -> {
                  var lines =
                      bankStatementLineRepository.findByBankStatementIdOrderByLineNumber(
                          UUID.fromString(statementId1));
                  return lines.getFirst().getId();
                });

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(() -> reconciliationService.manualMatch(lineId1, UUID.fromString(depositId)));

    // Import SECOND statement with same amount -- the deposit is already matched
    String statementId2 =
        importStatement(
            """
            FNB Trust Account Statement - Account 62012345678
            Date,Description,Amount,Balance,Reference
            22/03/2026,Duplicate deposit entry,11111.11,272019.73,SKIP-MATCH-REF2
            """);

    var result =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> reconciliationService.autoMatchStatement(UUID.fromString(statementId2)));

    // Should not match because the only candidate transaction is already matched
    assertThat(result.autoMatched()).isEqualTo(0);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var lines =
                  bankStatementLineRepository.findByBankStatementIdOrderByLineNumber(
                      UUID.fromString(statementId2));
              assertThat(lines).hasSize(1);
              assertThat(lines.getFirst().getMatchStatus()).isEqualTo("UNMATCHED");
            });
  }

  @Test
  void autoMatch_handlesEmptyCandidatePoolGracefully() throws Exception {
    // Import statement with an amount that has no matching transactions
    String statementId =
        importStatement(
            """
            FNB Trust Account Statement - Account 62012345678
            Date,Description,Amount,Balance,Reference
            28/03/2026,Unknown deposit,99887766.55,372796.28,NO-CANDIDATES
            """);

    var result =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> reconciliationService.autoMatchStatement(UUID.fromString(statementId)));

    assertThat(result.autoMatched()).isEqualTo(0);
    assertThat(result.unmatched()).isGreaterThanOrEqualTo(1);

    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () -> {
              var lines =
                  bankStatementLineRepository.findByBankStatementIdOrderByLineNumber(
                      UUID.fromString(statementId));
              assertThat(lines).hasSize(1);
              assertThat(lines.getFirst().getMatchStatus()).isEqualTo("UNMATCHED");
            });
  }
}
