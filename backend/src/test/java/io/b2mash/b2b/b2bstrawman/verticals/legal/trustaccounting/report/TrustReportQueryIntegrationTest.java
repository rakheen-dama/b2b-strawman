package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest.InterestService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.InterestPresentation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.ReconciliationStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationRepository;
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
class TrustReportQueryIntegrationTest {

  private static final String ORG_ID = "org_trust_report_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private io.b2mash.b2b.b2bstrawman.project.ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustReconciliationRepository reconciliationRepository;
  @Autowired private InterestService interestService;
  @Autowired private TrustInvestmentService investmentService;
  @Autowired private TrustBooksFixture fixture;

  // Report queries under test
  @Autowired private TrustReceiptsPaymentsQuery receiptsPaymentsQuery;
  @Autowired private ClientTrustBalancesQuery clientBalancesQuery;
  @Autowired private ClientLedgerStatementQuery ledgerStatementQuery;
  @Autowired private TrustReconciliationReportQuery reconciliationReportQuery;
  @Autowired private InvestmentRegisterQuery investmentRegisterQuery;
  @Autowired private InterestAllocationReportQuery interestAllocationQuery;
  @Autowired private Section35DataPackQuery section35DataPackQuery;

  private String tenantSchema;
  private UUID trustAccountId;
  private UUID firmInvestmentAccountId;
  private UUID clientInvestmentAccountId;
  private String customerId1;
  private String customerId2;
  private UUID ownerMemberId;
  private UUID approverMemberId;
  private UUID interestRunId;
  private UUID reconciliationId;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Trust Report Test Org", null).schemaName();

    ownerMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_report_owner",
                "report_owner@test.com",
                "Report Owner",
                "owner"));
    // 648F: a section 86(4) placement is approved by a member other than its maker
    approverMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_report_approver",
                "report_approver@test.com",
                "Report Approver",
                "admin"));

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

    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_report_owner");

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
                          "accountName": "Report Test Trust Account",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62000000300",
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
            mockMvc, ownerJwt, "Report Client Alpha", "report_alpha@test.com");
    customerId2 =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Report Client Beta", "report_beta@test.com");

    // Create deposits for both customers
    createDeposit(
        trustAccountId, customerId1, new BigDecimal("100000.00"), "DEP-RPT-001", "2025-04-15");
    createDeposit(
        trustAccountId, customerId2, new BigDecimal("50000.00"), "DEP-RPT-002", "2025-04-20");
    // Second deposit for customer1 in a different month
    createDeposit(
        trustAccountId, customerId1, new BigDecimal("25000.00"), "DEP-RPT-003", "2025-06-01");

    // Create LPFF rate
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
                      "notes": "Report test rate"
                    }
                    """))
        .andExpect(status().isCreated());

    // Create an interest run for a quarter
    var runResponse =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, ownerMemberId)
            .call(
                () ->
                    interestService.createInterestRun(
                        trustAccountId, LocalDate.of(2025, 4, 1), LocalDate.of(2025, 6, 30)));
    interestRunId = runResponse.id();

    // Calculate interest
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(() -> interestService.calculateInterest(interestRunId));

    // Place an investment for customer1
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
                        "6-month fixed deposit for report test",
                        InvestmentBasis.FIRM_DISCRETION)));

    // Create a reconciliation directly via repository (API requires bank statement upload)
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(
            () ->
                transactionTemplate.executeWithoutResult(
                    tx -> {
                      var recon =
                          new TrustReconciliation(trustAccountId, LocalDate.of(2025, 6, 30), null);
                      recon.setBankBalance(new BigDecimal("155000.00"));
                      recon.setCashbookBalance(new BigDecimal("155000.00"));
                      recon.setClientLedgerTotal(new BigDecimal("155000.00"));
                      recon.setOutstandingDeposits(BigDecimal.ZERO);
                      recon.setOutstandingPayments(BigDecimal.ZERO);
                      recon.setAdjustedBankBalance(new BigDecimal("155000.00"));
                      recon.setBalanced(true);
                      recon.setStatus(ReconciliationStatus.COMPLETED);
                      var saved = reconciliationRepository.save(recon);
                      reconciliationId = saved.getId();
                    }));
  }

  // --- Helper Methods ---

  private void createDeposit(
      UUID accountId, String custId, BigDecimal amount, String reference, String date)
      throws Exception {
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_report_owner");
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
                      "description": "Test deposit for reports",
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

  // ==========================================================================
  // 447.4 -- Trust Receipts & Payments Report Test
  // ==========================================================================

  @Test
  void trustReceiptsPayments_returnsChronologicalJournal() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("dateFrom", "2025-04-01");
          params.put("dateTo", "2025-06-30");

          var result = receiptsPaymentsQuery.executeAll(params);

          // Should have 3 deposit transactions (the investment creates a payment too,
          // but payments are AWAITING_APPROVAL so won't appear)
          assertThat(result.rows()).hasSizeGreaterThanOrEqualTo(3);
          assertThat(result.rows().getFirst()).containsKey("date");
          assertThat(result.rows().getFirst()).containsKey("reference");
          assertThat(result.rows().getFirst()).containsKey("type");
          // F7 (646E): the firm report prints the registry label, never the stored code
          assertThat(result.rows().getFirst()).containsEntry("type", "Deposit");
          assertThat(result.rows().getFirst()).containsKey("clientName");
          assertThat(result.rows().getFirst()).containsKey("credit");
          assertThat(result.rows().getFirst()).containsKey("debit");
          assertThat(result.rows().getFirst()).containsKey("balance");

          // Verify summary
          assertThat(result.summary()).containsKey("totalReceipts");
          assertThat(result.summary()).containsKey("totalPayments");
          assertThat(result.summary()).containsKey("netMovement");

          var totalReceipts = (BigDecimal) result.summary().get("totalReceipts");
          assertThat(totalReceipts).isGreaterThan(BigDecimal.ZERO);
        });
  }

  // ==========================================================================
  // 447.4 -- Client Trust Balances Report Test
  // ==========================================================================

  @Test
  void clientTrustBalances_returnsBalancesPerClient() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("asOfDate", "2025-06-30");

          var result = clientBalancesQuery.executeAll(params);

          // Should have 2 clients
          assertThat(result.rows()).hasSize(2);

          var firstRow = result.rows().getFirst();
          assertThat(firstRow).containsKey("clientName");
          assertThat(firstRow).containsKey("balance");
          assertThat(firstRow).containsKey("totalDeposits");
          assertThat(firstRow).containsKey("totalPayments");
          assertThat(firstRow).containsKey("totalFeeTransfers");
          assertThat(firstRow).containsKey("totalInterestCredited");

          // Verify summary
          assertThat(result.summary()).containsKey("totalTrustBalance");
          assertThat(result.summary()).containsKey("clientCount");
          assertThat((int) result.summary().get("clientCount")).isEqualTo(2);
        });
  }

  // ==========================================================================
  // 447.4 -- Client Ledger Statement Report Test
  // ==========================================================================

  @Test
  void clientLedgerStatement_returnsTransactionsWithRunningBalance() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("customer_id", UUID.fromString(customerId1));
          params.put("dateFrom", "2025-04-01");
          params.put("dateTo", "2025-06-30");

          var result = ledgerStatementQuery.executeAll(params);

          // Customer1 has 2 deposits in the period
          assertThat(result.rows()).hasSizeGreaterThanOrEqualTo(2);

          var firstRow = result.rows().getFirst();
          assertThat(firstRow).containsKey("date");
          assertThat(firstRow).containsKey("reference");
          assertThat(firstRow).containsKey("type");
          // F7 (646E): the registry label, never the stored code
          assertThat(firstRow).containsEntry("type", "Deposit");
          assertThat(firstRow).containsKey("credit");
          assertThat(firstRow).containsKey("debit");
          assertThat(firstRow).containsKey("runningBalance");

          // Verify summary
          assertThat(result.summary()).containsKey("openingBalance");
          assertThat(result.summary()).containsKey("closingBalance");
          assertThat(result.summary()).containsKey("totalDebits");
          assertThat(result.summary()).containsKey("totalCredits");

          var closingBalance = (BigDecimal) result.summary().get("closingBalance");
          assertThat(closingBalance).isGreaterThan(BigDecimal.ZERO);
        });
  }

  /**
   * 648F (§16.8.5, task 648.13): for a client with a section 86(4) investment the report's summary
   * carries an {@code invested} block — capital placed, the period's interest (gross, the Fund's
   * 5%, net; a reversed entry nets out), capital returned and the invested balance at either end —
   * on investments placed from the selected account (the funding-account rule). The rows, columns
   * and general balances are unchanged; a client without investments has no {@code invested} key.
   */
  @Test
  @SuppressWarnings("unchecked")
  void clientLedgerStatement_investedBlock_forASection86_4Client() {
    var books = fixture.tenant(tenantSchema, ORG_ID, ownerMemberId);
    UUID funding = books.freshAccount(false);
    UUID investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var investor = books.newClient("report s86(4) investor");
    books.deposit(funding, investor, "50000.00");
    var placed = books.requestPlacement(funding, investmentAccount, investor, "30000.00");
    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-30000.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("30000.00"),
        placed.depositTransactionId(),
        approverMemberId);
    // Another general account holds a deposit for the same investor but funds no investment
    UUID otherAccount = books.freshAccount(false);
    books.deposit(otherAccount, investor, "1000.00");
    books.recordInterest(
        investmentAccount,
        Expected.cashbookAndInvestment("95.00"),
        placed.id(),
        netInterest(LocalDate.of(2026, 3, 10), "100.00"));
    var reversed =
        books.recordInterest(
            investmentAccount,
            Expected.cashbookAndInvestment("47.50"),
            placed.id(),
            netInterest(LocalDate.of(2026, 3, 15), "50.00"));
    books.reverseInterest(
        investmentAccount, Expected.cashbookAndInvestment("-47.50"), reversed.id());
    books.recordInterest(
        investmentAccount,
        Expected.cashbookAndInvestment("190.00"),
        placed.id(),
        netInterest(LocalDate.of(2026, 4, 10), "200.00"));
    books.withdraw(
        funding,
        Expected.cashbookAndClient("30285.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("-30285.00"),
        placed.id(),
        LocalDate.of(2026, 4, 20));

    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", funding);
          params.put("customer_id", investor.id());
          params.put("dateFrom", "2026-03-01");
          params.put("dateTo", "2026-04-30");

          var result = ledgerStatementQuery.executeAll(params);

          // The general ledger is unchanged: deposit, placement out, return in
          assertThat(result.rows())
              .extracting(row -> row.get("type"))
              .containsExactly(
                  "Deposit", "Placed in section 86(4) investment", "Investment returned to trust");
          assertThat((BigDecimal) result.summary().get("closingBalance"))
              .isEqualByComparingTo("50285.00");

          assertThat(result.summary()).containsKey("invested");
          var invested = (Map<String, Object>) result.summary().get("invested");
          assertThat((BigDecimal) invested.get("openingBalance")).isEqualByComparingTo("0");
          assertThat((BigDecimal) invested.get("capitalPlaced")).isEqualByComparingTo("30000.00");
          assertThat((BigDecimal) invested.get("interestGross")).isEqualByComparingTo("300.00");
          assertThat((BigDecimal) invested.get("interestFundShare")).isEqualByComparingTo("15.00");
          assertThat((BigDecimal) invested.get("interestNet")).isEqualByComparingTo("285.00");
          assertThat((BigDecimal) invested.get("returnedToTrust")).isEqualByComparingTo("30285.00");
          assertThat((BigDecimal) invested.get("closingBalance")).isEqualByComparingTo("0");

          // March alone: still invested at the end of the range
          params.put("dateTo", "2026-03-31");
          var march =
              (Map<String, Object>)
                  ledgerStatementQuery.executeAll(params).summary().get("invested");
          assertThat((BigDecimal) march.get("interestNet")).isEqualByComparingTo("95.00");
          assertThat((BigDecimal) march.get("closingBalance")).isEqualByComparingTo("30095.00");

          // Same investor and range on the other account: its deposit, no invested block (the
          // funding-account rule — the investment was placed from `funding` only)
          var other = new HashMap<String, Object>();
          other.put("trust_account_id", otherAccount);
          other.put("customer_id", investor.id());
          other.put("dateFrom", "2026-03-01");
          other.put("dateTo", "2026-04-30");
          var otherResult = ledgerStatementQuery.executeAll(other);
          assertThat(otherResult.rows()).hasSize(1);
          assertThat(otherResult.rows().getFirst()).containsEntry("type", "Deposit");
          assertThat((BigDecimal) otherResult.rows().getFirst().get("credit"))
              .isEqualByComparingTo("1000.00");
          assertThat(otherResult.summary()).doesNotContainKey("invested");

          // A client without a section 86(4) investment has no invested block
          var plain = new HashMap<String, Object>();
          plain.put("trust_account_id", trustAccountId);
          plain.put("customer_id", UUID.fromString(customerId1));
          plain.put("dateFrom", "2025-04-01");
          plain.put("dateTo", "2025-06-30");
          assertThat(ledgerStatementQuery.executeAll(plain).summary())
              .doesNotContainKey("invested");
        });
  }

  /**
   * 648F review F2: an inverted range ({@code dateTo} before {@code dateFrom}) is not rejected
   * upstream; it returns no rows and, for an s86(4) client, no {@code invested} block — as before
   * 648F, never the service's "Invalid ledger statement range".
   */
  @Test
  void clientLedgerStatement_invertedRange_noRowsAndNoInvestedBlock() {
    var books = fixture.tenant(tenantSchema, ORG_ID, ownerMemberId);
    UUID funding = books.freshAccount(false);
    UUID investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var investor = books.newClient("report inverted-range investor");
    books.deposit(funding, investor, "50000.00");
    var placed = books.requestPlacement(funding, investmentAccount, investor, "30000.00");
    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-30000.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("30000.00"),
        placed.depositTransactionId(),
        approverMemberId);

    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", funding);
          params.put("customer_id", investor.id());
          params.put("dateFrom", "2026-04-30");
          params.put("dateTo", "2026-03-01");

          var result = ledgerStatementQuery.executeAll(params);

          assertThat(result.rows()).isEmpty();
          assertThat(result.summary())
              .containsEntry("transactionCount", 0)
              .doesNotContainKey("invested");
          // the opening balance is the general balance as at dateFrom − 1, as before
          assertThat((BigDecimal) result.summary().get("openingBalance"))
              .isEqualByComparingTo("20000.00");
        });
  }

  private static TrustInvestmentService.RecordInvestmentInterestRequest netInterest(
      LocalDate date, String gross) {
    return new TrustInvestmentService.RecordInvestmentInterestRequest(
        date, new BigDecimal(gross), null, InterestPresentation.NET, null);
  }

  // ==========================================================================
  // 447.4 -- Trust Reconciliation Report Test
  // ==========================================================================

  @Test
  void trustReconciliation_returnsThreeWayReconciliation() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("reconciliation_id", reconciliationId);

          var result = reconciliationReportQuery.executeAll(params);

          // Should have 6 rows (bank, outstanding deposits, outstanding payments, adjusted,
          // cashbook, client ledger)
          assertThat(result.rows()).hasSize(6);

          // Verify section structure
          assertThat(result.rows().get(0).get("section")).isEqualTo("BANK");
          assertThat(result.rows().get(1).get("section")).isEqualTo("ADJUSTMENTS");
          assertThat(result.rows().get(3).get("section")).isEqualTo("ADJUSTED");
          assertThat(result.rows().get(4).get("section")).isEqualTo("COMPARISON");

          // Verify summary
          assertThat(result.summary()).containsKey("bankBalance");
          assertThat(result.summary()).containsKey("cashbookBalance");
          assertThat(result.summary()).containsKey("isBalanced");
          assertThat((boolean) result.summary().get("isBalanced")).isTrue();
        });
  }

  // ==========================================================================
  // 447.4 -- Investment Register Report Test
  // ==========================================================================

  @Test
  void investmentRegister_returnsInvestments() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);

          var result = investmentRegisterQuery.executeAll(params);

          // Should have at least 1 investment
          assertThat(result.rows()).hasSizeGreaterThanOrEqualTo(1);

          var firstRow = result.rows().getFirst();
          assertThat(firstRow).containsKey("clientName");
          assertThat(firstRow).containsKey("institution");
          assertThat(firstRow).containsKey("accountNumber");
          assertThat(firstRow).containsKey("principal");
          assertThat(firstRow).containsKey("interestRate");
          assertThat(firstRow).containsKey("depositDate");
          assertThat(firstRow).containsKey("status");

          // Verify summary
          assertThat(result.summary()).containsKey("totalPrincipal");
          assertThat(result.summary()).containsKey("activeCount");

          var totalPrincipal = (BigDecimal) result.summary().get("totalPrincipal");
          assertThat(totalPrincipal).isEqualByComparingTo(new BigDecimal("20000.00"));
          assertThat((int) result.summary().get("activeCount")).isEqualTo(1);
        });
  }

  // ==========================================================================
  // 447.4 -- Interest Allocation Report Test
  // ==========================================================================

  @Test
  void interestAllocation_returnsPerClientBreakdown() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("interest_run_id", interestRunId);

          var result = interestAllocationQuery.executeAll(params);

          // Should have allocations for 2 clients
          assertThat(result.rows()).hasSize(2);

          var firstRow = result.rows().getFirst();
          assertThat(firstRow).containsKey("clientName");
          assertThat(firstRow).containsKey("averageDailyBalance");
          assertThat(firstRow).containsKey("daysInPeriod");
          assertThat(firstRow).containsKey("grossInterest");
          assertThat(firstRow).containsKey("lpffShare");
          assertThat(firstRow).containsKey("clientShare");

          // Verify summary
          assertThat(result.summary()).containsKey("totalInterest");
          assertThat(result.summary()).containsKey("totalLpffShare");
          assertThat(result.summary()).containsKey("totalClientShare");

          var totalInterest = (BigDecimal) result.summary().get("totalInterest");
          assertThat(totalInterest).isGreaterThan(BigDecimal.ZERO);
        });
  }

  // ==========================================================================
  // 447.4 -- Trust Receipts & Payments with pagination
  // ==========================================================================

  @Test
  void trustReceiptsPayments_paginationWorks() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("dateFrom", "2025-04-01");
          params.put("dateTo", "2025-06-30");

          var result =
              receiptsPaymentsQuery.execute(
                  params, org.springframework.data.domain.PageRequest.of(0, 2));

          assertThat(result.rows()).hasSizeLessThanOrEqualTo(2);
          assertThat(result.totalElements()).isGreaterThanOrEqualTo(3);
          assertThat(result.totalPages()).isGreaterThanOrEqualTo(2);
        });
  }

  // ==========================================================================
  // 447.5 -- Section 35 Data Pack Test
  // ==========================================================================

  @Test
  void section35DataPack_returnsCompositeWithAllSections() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("financial_year_end", "2026-03-31");

          var result = section35DataPackQuery.executeAll(params);

          // Should have rows from multiple sections
          assertThat(result.rows()).isNotEmpty();

          // Verify summary contains section metadata
          assertThat(result.summary()).containsKey("sectionCount");
          assertThat(result.summary()).containsKey("financialYearStart");
          assertThat(result.summary()).containsKey("financialYearEnd");
          assertThat(result.summary()).containsKey("sections");

          @SuppressWarnings("unchecked")
          var sections = (List<Map<String, Object>>) result.summary().get("sections");
          assertThat(sections).hasSizeGreaterThanOrEqualTo(5);

          // Verify section names
          var sectionNames = sections.stream().map(s -> (String) s.get("sectionName")).toList();
          assertThat(sectionNames).contains("Trust Receipts & Payments");
          assertThat(sectionNames).contains("Client Trust Balances");
          assertThat(sectionNames).contains("Trust Reconciliation");
          assertThat(sectionNames).contains("Section 86(3) Investments (Firm Discretion)");

          // Verify rows have section markers
          var rowSections =
              result.rows().stream().map(r -> (String) r.get("_section")).distinct().toList();
          assertThat(rowSections).hasSizeGreaterThanOrEqualTo(2);

          // Verify financial year summary
          assertThat(result.summary().get("financialYearStart")).isEqualTo("2025-04-01");
          assertThat(result.summary().get("financialYearEnd")).isEqualTo("2026-03-31");
        });
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
