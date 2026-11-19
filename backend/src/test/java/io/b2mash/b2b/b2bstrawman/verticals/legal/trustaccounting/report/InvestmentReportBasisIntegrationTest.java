package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import static org.assertj.core.api.Assertions.assertThat;
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
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustInvestmentAccountHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvestmentReportBasisIntegrationTest {

  private static final String ORG_ID = "org_inv_report_basis_test";
  private static final String APPROVER_SUB = "user_inv_basis_approver";

  @Autowired private MockMvc mockMvc;
  @Autowired private io.b2mash.b2b.b2bstrawman.project.ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustInvestmentService investmentService;

  @Autowired private InvestmentRegisterQuery investmentRegisterQuery;
  @Autowired private Section35DataPackQuery section35DataPackQuery;

  private String tenantSchema;
  private UUID trustAccountId;
  private UUID firmInvestmentAccountId;
  private UUID clientInvestmentAccountId;
  private String customerId1;
  private String customerId2;
  private UUID ownerMemberId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Inv Report Basis Test Org", null).schemaName();

    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_inv_basis_owner",
                "inv_basis_owner@test.com",
                "Inv Basis Owner",
                "owner"));
    // 648E: single-approval mode refuses self-approval — a second owner approves placements
    TestMemberHelper.syncMember(
        mockMvc,
        ORG_ID,
        APPROVER_SUB,
        "inv_basis_approver@test.com",
        "Inv Basis Approver",
        "owner");

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

    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_inv_basis_owner");

    // Create a trust account
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Basis Test Trust Account",
                          "bankName": "Standard Bank",
                          "branchCode": "051001",
                          "accountNumber": "90100012345",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2025-04-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    trustAccountId = UUID.fromString(TestEntityHelper.extractId(accountResult));
    // 648A: investments are held in INVESTMENT accounts of their basis
    firmInvestmentAccountId =
        TestTrustInvestmentAccountHelper.openInvestmentAccount(
            mockMvc,
            ownerJwt,
            "Section 86(3) Investments",
            "INV86-3-" + ORG_ID.hashCode(),
            InvestmentBasis.FIRM_DISCRETION);
    clientInvestmentAccountId =
        TestTrustInvestmentAccountHelper.openInvestmentAccount(
            mockMvc,
            ownerJwt,
            "Section 86(4) Investments",
            "INV86-4-" + ORG_ID.hashCode(),
            InvestmentBasis.CLIENT_INSTRUCTION);

    // Create customers
    customerId1 =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Basis Client Alpha", "basis_alpha@test.com");
    customerId2 =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Basis Client Beta", "basis_beta@test.com");

    // Create deposits for both customers
    createDeposit(
        trustAccountId, customerId1, new BigDecimal("100000.00"), "DEP-BAS-001", "2025-04-15");
    createDeposit(
        trustAccountId, customerId2, new BigDecimal("50000.00"), "DEP-BAS-002", "2025-04-20");

    // Create LPFF rate (75% LPFF share)
    mockMvc
        .perform(
            post("/api/trust-accounts/" + trustAccountId + "/lpff-rates")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "effectiveFrom": "2025-04-01",
                      "ratePercent": 0.0750,
                      "lpffSharePercent": 0.7500,
                      "notes": "Basis test rate"
                    }
                    """))
        .andExpect(status().isCreated());

    // Place a FIRM_DISCRETION investment for customer1
    var firmPlaced =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    investmentService.placeInvestment(
                        trustAccountId,
                        new TrustInvestmentService.PlaceInvestmentRequest(
                            firmInvestmentAccountId,
                            null,
                            null,
                            "ABSA Bank",
                            "9012345678",
                            new BigDecimal("20000.00"),
                            new BigDecimal("0.0650"),
                            LocalDate.of(2025, 5, 1),
                            LocalDate.of(2025, 11, 1),
                            "Firm discretion fixed deposit",
                            InvestmentBasis.FIRM_DISCRETION)));

    // Place a CLIENT_INSTRUCTION investment for customer2
    var clientPlaced =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    investmentService.placeInvestment(
                        trustAccountId,
                        new TrustInvestmentService.PlaceInvestmentRequest(
                            clientInvestmentAccountId,
                            UUID.fromString(customerId2),
                            trustMatterOf(customerId2),
                            "Nedbank",
                            "1122334455",
                            new BigDecimal("15000.00"),
                            new BigDecimal("0.0500"),
                            LocalDate.of(2025, 5, 15),
                            LocalDate.of(2025, 11, 15),
                            "Client instruction fixed deposit",
                            InvestmentBasis.CLIENT_INSTRUCTION)));

    // 650C (D-14): the Section 35 pack lists the investments on the books at the year end, so
    // both placements are approved (posted) by a member other than their maker
    for (var placed : List.of(firmPlaced, clientPlaced)) {
      mockMvc
          .perform(
              post("/api/trust-transactions/" + placed.depositTransactionId() + "/approve")
                  .with(TestJwtFactory.ownerJwt(ORG_ID, APPROVER_SUB)))
          .andExpect(status().isOk());
    }
  }

  // --- Helper Methods ---

  private void createDeposit(
      UUID accountId, String custId, BigDecimal amount, String reference, String date)
      throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_inv_basis_owner");
    mockMvc
        .perform(
            post("/api/trust-accounts/" + accountId + "/transactions/deposit")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "clientId": "%s",
                      "matterId": "%s",
                      "amount": %s,
                      "reference": "%s",
                      "description": "Test deposit for basis report",
                      "transactionDate": "%s"
                    }
                    """
                        .formatted(
                            custId,
                            trustMatterOf(custId),
                            amount.toPlainString(),
                            reference,
                            date)))
        .andExpect(status().isCreated());
  }

  private void runInTenant(Runnable action) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .where(RequestScopes.ORG_ID, ORG_ID)
        .where(RequestScopes.MEMBER_ID, ownerMemberId)
        .where(RequestScopes.ORG_ROLE, "owner")
        .run(() -> transactionTemplate.executeWithoutResult(tx -> action.run()));
  }

  // --- Task 454.4: Investment Register Report Tests ---

  @Test
  void investmentRegister_firmDiscretionOnly_showsGeneralRate() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("investmentBasis", "FIRM_DISCRETION");

          var result = investmentRegisterQuery.executeAll(params);

          assertThat(result.rows()).hasSize(1);
          var row = result.rows().getFirst();
          assertThat(row.get("investmentBasis")).isEqualTo("FIRM_DISCRETION");
          assertThat(row.get("applicableLpffRate")).isEqualTo("75%");
          assertThat(row.get("institution")).isEqualTo("ABSA Bank");
        });
  }

  @Test
  void investmentRegister_clientInstructionOnly_showsStatutoryRate() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("investmentBasis", "CLIENT_INSTRUCTION");

          var result = investmentRegisterQuery.executeAll(params);

          assertThat(result.rows()).hasSize(1);
          var row = result.rows().getFirst();
          assertThat(row.get("investmentBasis")).isEqualTo("CLIENT_INSTRUCTION");
          assertThat(row.get("applicableLpffRate")).isEqualTo("5% (statutory)");
          assertThat(row.get("institution")).isEqualTo("Nedbank");
        });
  }

  @Test
  void investmentRegister_mixedBasis_showsCorrectRatesPerRow() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);

          var result = investmentRegisterQuery.executeAll(params);

          assertThat(result.rows()).hasSize(2);

          var firmRow =
              result.rows().stream()
                  .filter(r -> "FIRM_DISCRETION".equals(r.get("investmentBasis")))
                  .findFirst()
                  .orElseThrow();
          assertThat(firmRow.get("applicableLpffRate")).isEqualTo("75%");

          var clientRow =
              result.rows().stream()
                  .filter(r -> "CLIENT_INSTRUCTION".equals(r.get("investmentBasis")))
                  .findFirst()
                  .orElseThrow();
          assertThat(clientRow.get("applicableLpffRate")).isEqualTo("5% (statutory)");
        });
  }

  @Test
  void investmentRegister_filterByBasis_returnsSubset() {
    runInTenant(
        () -> {
          // Unfiltered: both investments
          var allParams = new HashMap<String, Object>();
          allParams.put("trust_account_id", trustAccountId);
          var allResult = investmentRegisterQuery.executeAll(allParams);
          assertThat(allResult.rows()).hasSize(2);

          // Filtered to FIRM_DISCRETION: only one
          var firmParams = new HashMap<String, Object>();
          firmParams.put("trust_account_id", trustAccountId);
          firmParams.put("investmentBasis", "FIRM_DISCRETION");
          var firmResult = investmentRegisterQuery.executeAll(firmParams);
          assertThat(firmResult.rows()).hasSize(1);
          assertThat(firmResult.rows().getFirst().get("investmentBasis"))
              .isEqualTo("FIRM_DISCRETION");

          // Filtered to CLIENT_INSTRUCTION: only one
          var clientParams = new HashMap<String, Object>();
          clientParams.put("trust_account_id", trustAccountId);
          clientParams.put("investmentBasis", "CLIENT_INSTRUCTION");
          var clientResult = investmentRegisterQuery.executeAll(clientParams);
          assertThat(clientResult.rows()).hasSize(1);
          assertThat(clientResult.rows().getFirst().get("investmentBasis"))
              .isEqualTo("CLIENT_INSTRUCTION");
        });
  }

  // --- Task 454.5: Section 35 Data Pack Tests ---

  @Test
  @SuppressWarnings("unchecked")
  void section35DataPack_separatesFirmAndClientInvestments() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("financial_year_end", "2026-03-31");

          var result = section35DataPackQuery.executeAll(params);
          var sections = (List<Map<String, Object>>) result.summary().get("sections");

          var sectionNames = sections.stream().map(s -> (String) s.get("sectionName")).toList();
          assertThat(sectionNames)
              .contains("Section 86(3) Investments (Firm Discretion)")
              .contains("Section 86(4) Investments (Client Instruction)");

          // Verify the old single "Investment Register" section is gone
          assertThat(sectionNames).doesNotContain("Investment Register");
        });
  }

  @Test
  @SuppressWarnings("unchecked")
  void section35DataPack_firmDiscretionSection_showsGeneralRate() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("financial_year_end", "2026-03-31");

          var result = section35DataPackQuery.executeAll(params);

          // Find the firm discretion investment rows
          var firmRows =
              result.rows().stream()
                  .filter(
                      r -> "Section 86(3) Investments (Firm Discretion)".equals(r.get("_section")))
                  // G89-F18: each additive section ends with a "Total" row
                  .filter(r -> r.get("_total") == null)
                  .toList();
          assertThat(firmRows).hasSize(1);
          assertThat(firmRows.getFirst().get("applicableLpffRate")).isEqualTo("75%");
          // 650C (D-14): the amount is the investment's balance on the books at the year end
          assertThat((BigDecimal) firmRows.getFirst().get("amount"))
              .isEqualByComparingTo("20000.00");
          assertThat((String) firmRows.getFirst().get("label"))
              .isEqualTo("Firm (section 86(3)) · ABSA Bank 9012345678");
        });
  }

  @Test
  @SuppressWarnings("unchecked")
  void section35DataPack_clientInstructionSection_showsStatutoryRate() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("financial_year_end", "2026-03-31");

          var result = section35DataPackQuery.executeAll(params);

          // Find the client instruction investment rows
          var clientRows =
              result.rows().stream()
                  .filter(
                      r ->
                          "Section 86(4) Investments (Client Instruction)"
                              .equals(r.get("_section")))
                  // G89-F18: each additive section ends with a "Total" row
                  .filter(r -> r.get("_total") == null)
                  .toList();
          assertThat(clientRows).hasSize(1);
          assertThat(clientRows.getFirst().get("applicableLpffRate")).isEqualTo("5% (statutory)");
        });
  }

  @Test
  @SuppressWarnings("unchecked")
  void section35DataPack_bothBasisPresent_includesBothSectionsAndNoLegacyFallback() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("financial_year_end", "2026-03-31");

          var result = section35DataPackQuery.executeAll(params);
          var sections = (List<Map<String, Object>>) result.summary().get("sections");

          // Both sections exist because we have both types of investments
          var firmSection =
              sections.stream()
                  .filter(
                      s ->
                          "Section 86(3) Investments (Firm Discretion)"
                              .equals(s.get("sectionName")))
                  .findFirst();
          assertThat(firmSection).isPresent();
          assertThat((int) firmSection.get().get("rowCount")).isEqualTo(1);

          var clientSection =
              sections.stream()
                  .filter(
                      s ->
                          "Section 86(4) Investments (Client Instruction)"
                              .equals(s.get("sectionName")))
                  .findFirst();
          assertThat(clientSection).isPresent();
          assertThat((int) clientSection.get().get("rowCount")).isEqualTo(1);

          // Verify there is NO legacy "Investment Register" fallback section
          var emptyRegister =
              sections.stream()
                  .filter(s -> "Investment Register".equals(s.get("sectionName")))
                  .findFirst();
          assertThat(emptyRegister).isEmpty();
        });
  }

  // --- 648E: the register's balance is derived from posted rows ---

  @Test
  void
      investmentRegister_rowBalance_isDerivedFromPostedRows_principalOnceApproved_zeroWhilePending()
          throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_inv_basis_owner");
    // A second banking account of its own, so the rows on trustAccountId are untouched
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Basis 648E Trust Account",
                          "bankName": "Standard Bank",
                          "branchCode": "051001",
                          "accountNumber": "90100012346",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2025-04-01"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    var f2 = UUID.fromString(TestEntityHelper.extractId(accountResult));
    createDeposit(f2, customerId1, new BigDecimal("40000.00"), "DEP-BAS-648E", "2025-04-15");
    var approved = placeClientInvestment(f2, "648E-APPROVED", "25000.00");
    placeClientInvestment(f2, "648E-PENDING", "5000.00");
    mockMvc
        .perform(
            post("/api/trust-transactions/" + approved.depositTransactionId() + "/approve")
                .with(TestJwtFactory.ownerJwt(ORG_ID, APPROVER_SUB)))
        .andExpect(status().isOk());

    runInTenant(
        () -> {
          var result = investmentRegisterQuery.executeAll(Map.of("trust_account_id", f2));
          var approvedRow = registerRow(result.rows(), "648E-APPROVED");
          assertThat((BigDecimal) approvedRow.get("balance")).isEqualByComparingTo("25000.00");
          assertThat((BigDecimal) approvedRow.get("principal")).isEqualByComparingTo("25000.00");
          var pendingRow = registerRow(result.rows(), "648E-PENDING");
          assertThat((BigDecimal) pendingRow.get("balance")).isEqualByComparingTo("0");
          assertThat((BigDecimal) pendingRow.get("principal")).isEqualByComparingTo("5000.00");
          // v4 (650C, D-19): the register's summary carries balance and interest totals
          assertThat(result.summary().keySet())
              .containsExactly(
                  "totalPrincipal",
                  "totalBalance",
                  "totalGrossInterest",
                  "totalFundShare",
                  "totalInterestEarned",
                  "activeCount",
                  "totalCount",
                  "scopeNote");
          assertThat((BigDecimal) result.summary().get("totalBalance"))
              .isEqualByComparingTo("25000.00");
        });

    // 648B (OQ-8 a): interestEarned is Σ net of the POSTED entries; a reversed entry drops out
    recordNetInterest(approved.id(), "2025-05-01", "100.00");
    var reversed = recordNetInterest(approved.id(), "2025-05-02", "40.00");
    mockMvc
        .perform(
            post("/api/trust-investment-interest/" + reversed + "/reverse")
                .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_basis_owner")))
        .andExpect(status().isOk());
    runInTenant(
        () -> {
          var result = investmentRegisterQuery.executeAll(Map.of("trust_account_id", f2));
          var approvedRow = registerRow(result.rows(), "648E-APPROVED");
          assertThat((BigDecimal) approvedRow.get("interestEarned")).isEqualByComparingTo("95.00");
          assertThat((BigDecimal) approvedRow.get("balance")).isEqualByComparingTo("25095.00");
          assertThat((BigDecimal) registerRow(result.rows(), "648E-PENDING").get("interestEarned"))
              .isEqualByComparingTo("0");
          assertThat((BigDecimal) result.summary().get("totalInterestEarned"))
              .isEqualByComparingTo("95.00");
          // v4 (650C, D-19): gross, the Fund's 5% and net; basis, status and holding account in
          // words (the raw keys stay for callers, D-17)
          assertThat((BigDecimal) approvedRow.get("grossInterest")).isEqualByComparingTo("100.00");
          assertThat((BigDecimal) approvedRow.get("fundShare")).isEqualByComparingTo("5.00");
          assertThat(approvedRow.get("basisLabel")).isEqualTo("Section 86(4)");
          assertThat(approvedRow.get("statusLabel")).isEqualTo("Active");
          assertThat(approvedRow.get("investmentAccountName"))
              .isEqualTo("Section 86(4) Investments");
          assertThat((BigDecimal) result.summary().get("totalGrossInterest"))
              .isEqualByComparingTo("100.00");
          assertThat((BigDecimal) result.summary().get("totalFundShare"))
              .isEqualByComparingTo("5.00");
        });

    mockMvc
        .perform(
            post("/api/trust-investments/" + approved.id() + "/withdraw")
                .with(ownerJwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"withdrawalDate\": \"2025-06-01\"}"))
        .andExpect(status().isOk());

    runInTenant(
        () -> {
          var result = investmentRegisterQuery.executeAll(Map.of("trust_account_id", f2));
          var withdrawnRow = registerRow(result.rows(), "648E-APPROVED");
          assertThat(withdrawnRow.get("status")).isEqualTo("WITHDRAWN");
          assertThat(withdrawnRow.get("statusLabel")).isEqualTo("Withdrawn");
          assertThat((BigDecimal) withdrawnRow.get("balance")).isEqualByComparingTo("0");
        });
  }

  private record Placed(String id, String depositTransactionId) {}

  /** A bank-advice NET interest entry of {@code gross} on {@code date} (648B); its id. */
  private String recordNetInterest(String investmentId, String date, String gross)
      throws Exception {
    var body =
        mockMvc
            .perform(
                post("/api/trust-investments/" + investmentId + "/interest")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_basis_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"entryDate\": \"%s\", \"grossAmount\": %s, \"presentation\": \"NET\"}"
                            .formatted(date, gross)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return com.jayway.jsonpath.JsonPath.read(body, "$.id").toString();
  }

  /** A section 86(4) placement for customer1 from {@code fundingAccountId}, awaiting approval. */
  private Placed placeClientInvestment(
      UUID fundingAccountId, String accountNumber, String principal) throws Exception {
    var body =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + fundingAccountId + "/investments")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_inv_basis_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "investmentAccountId": "%s",
                          "clientId": "%s",
                          "matterId": "%s",
                          "institution": "Investec",
                          "accountNumber": "%s",
                          "principal": %s,
                          "interestRate": 0.0650,
                          "depositDate": "2025-05-01",
                          "investmentBasis": "CLIENT_INSTRUCTION"
                        }
                        """
                            .formatted(
                                clientInvestmentAccountId,
                                customerId1,
                                trustMatterOf(customerId1),
                                accountNumber,
                                principal)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return new Placed(JsonPath.read(body, "$.id"), JsonPath.read(body, "$.depositTransactionId"));
  }

  private static Map<String, Object> registerRow(
      List<Map<String, Object>> rows, String accountNumber) {
    var matching = rows.stream().filter(r -> accountNumber.equals(r.get("accountNumber"))).toList();
    assertThat(matching).as("register row " + accountNumber).hasSize(1);
    return matching.getFirst();
  }

  private final java.util.Map<String, UUID> trustMatters = new java.util.HashMap<>();

  /** G89-F28: a client's trust postings sit on a matter of its own (one per client). */
  private UUID trustMatterOf(String clientId) {
    return trustMatters.computeIfAbsent(
        clientId,
        id ->
            TestTrustMatterHelper.openClientMatter(
                tenantSchema,
                transactionTemplate,
                projectRepository,
                UUID.fromString(id),
                "Trust matter " + id,
                ownerMemberId));
  }
}
