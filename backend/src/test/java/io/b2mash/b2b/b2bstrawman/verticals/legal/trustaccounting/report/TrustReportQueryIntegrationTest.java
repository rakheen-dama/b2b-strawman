package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
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
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.InterestPresentation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.ReconciliationStatus;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
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
class TrustReportQueryIntegrationTest {

  private static final String ORG_ID = "org_trust_report_test";

  /** A raw type / status code ("FUND_SWEEP", "CLIENT_INSTRUCTION") — never on a report (F-15). */
  private static final java.util.regex.Pattern RAW_CODE =
      java.util.regex.Pattern.compile(".*\\b[A-Z]{2,}_[A-Z_]{2,}\\b.*");

  @Autowired private MockMvc mockMvc;
  @Autowired private io.b2mash.b2b.b2bstrawman.project.ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustReconciliationRepository reconciliationRepository;
  @Autowired private TrustInvestmentService investmentService;
  @Autowired private TrustBooksFixture fixture;

  // Report queries under test
  @Autowired private TrustReceiptsPaymentsQuery receiptsPaymentsQuery;
  @Autowired private ClientTrustBalancesQuery clientBalancesQuery;
  @Autowired private ClientLedgerStatementQuery ledgerStatementQuery;
  @Autowired private TrustReconciliationReportQuery reconciliationReportQuery;
  @Autowired private InvestmentRegisterQuery investmentRegisterQuery;
  @Autowired private Section35DataPackQuery section35DataPackQuery;
  @Autowired private TrustPositionQuery trustPositionQuery;
  @Autowired private TrustIntegrityService trustIntegrityService;
  @Autowired private TrustReconciliationService trustReconciliationService;
  @Autowired private List<io.b2mash.b2b.b2bstrawman.reporting.ReportQuery> reportQueries;
  @Autowired private io.b2mash.b2b.b2bstrawman.reporting.ReportRenderingService renderingService;

  private String tenantSchema;
  private UUID trustAccountId;
  private UUID firmInvestmentAccountId;
  private UUID clientInvestmentAccountId;
  private String customerId1;
  private String customerId2;
  private UUID ownerMemberId;
  private UUID approverMemberId;
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

          // v4 (650C, D-15): receipt / payment and the running balance by cashbook effect; each
          // row under its ledger heading, in words; per-heading totals in the summary
          BigDecimal running = BigDecimal.ZERO;
          for (var row : result.rows()) {
            running =
                running.add((BigDecimal) row.get("credit")).subtract((BigDecimal) row.get("debit"));
            assertThat((BigDecimal) row.get("balance")).isEqualByComparingTo(running);
            assertThat((String) row.get("ledgerHeading")).doesNotMatch(RAW_CODE);
          }
          assertThat(result.rows().getFirst()).containsEntry("ledgerHeading", "Client");
          @SuppressWarnings("unchecked")
          var headings = (List<Map<String, Object>>) result.summary().get("headings");
          assertThat(headings).extracting(h -> h.get("heading")).contains("Client");
          assertThat(
                  headings.stream()
                      .map(h -> (BigDecimal) h.get("receipts"))
                      .reduce(BigDecimal.ZERO, BigDecimal::add))
              .isEqualByComparingTo(totalReceipts);
          assertThat(result.summary().get("scopeNote"))
              .isEqualTo(TrustReportWords.TRUST_ASSETS_NOTE);
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

          // Should have 2 clients (650A: the Rule 54.15 list also carries the Fund line)
          assertThat(result.rows()).filteredOn(r -> "CLIENT".equals(r.get("kind"))).hasSize(2);

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
  // 650A -- Rule 54.15 list (client-trust-balances internals) and trust-position
  // ==========================================================================

  /**
   * 650A (§16.8.1): for an account whose 31 March list is locked, the report prints the locked list
   * — a deposit back-dated into March after the lock is not in the rows' balances — and the
   * separate "entered after this list was locked" schedule carries it.
   */
  @Test
  @SuppressWarnings("unchecked")
  void clientTrustBalances_locked_printsTheLockedList_andTheAfterLockSchedule() {
    var books = fixture.tenant(tenantSchema, ORG_ID, ownerMemberId);
    UUID account = books.freshAccount(false);
    var client = books.newClient("report locked");
    var deposit = books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            books,
            account,
            "rq-locked-march.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,RQL");
    var listId = new UUID[1];
    books.as(
        ownerMemberId,
        () -> {
          trustReconciliationService.manualMatch(march.lines().getFirst().id(), deposit);
          var created =
              trustReconciliationService.createReconciliation(
                  account, LocalDate.of(2026, 3, 31), march.id());
          trustReconciliationService.calculateReconciliation(created.id());
          listId[0] =
              trustReconciliationService.completeReconciliation(created.id()).monthlyListId();
        });
    var backdated = books.deposit(account, client, "250.00");

    var result =
        inTenant(
            () ->
                clientBalancesQuery.executeAll(
                    Map.of("trust_account_id", account.toString(), "asOfDate", "2026-03-31")));

    var person = onlyRow(result, r -> client.id().equals(r.get("customerId")));
    assertThat((BigDecimal) person.get("balance"))
        .as("the locked list, without the back-dated deposit")
        .isEqualByComparingTo("1000.00");
    assertThat((BigDecimal) person.get("totalDeposits"))
        .as("the locked list's totals, not today's ledger (650A review C4)")
        .isEqualByComparingTo("1000.00");
    assertThat(person.get("lastTransactionDate")).isEqualTo("2026-03-01");
    assertThat(person.get("kind")).isEqualTo("CLIENT");
    assertThat(person.get("source")).isEqualTo("LOCKED");
    assertThat((String) person.get("sourceNote"))
        .startsWith("Locked ")
        .endsWith(" by Report Owner");
    var fund = onlyRow(result, r -> "FUND".equals(r.get("kind")));
    assertThat((BigDecimal) fund.get("balance")).isEqualByComparingTo("0.00");
    assertThat(fund.get("source")).isEqualTo("LOCKED");

    var summary = result.summary();
    assertThat(summary.get("fullyLocked")).isEqualTo(true);
    assertThat((BigDecimal) summary.get("creditorTotal")).isEqualByComparingTo("1000.00");
    assertThat((BigDecimal) summary.get("bankTotal")).isEqualByComparingTo("1000.00");
    assertThat((BigDecimal) summary.get("difference")).isEqualByComparingTo("0.00");
    assertThat(summary.get("trustCashNote")).isEqualTo("Trust cash is not recorded in Kazi");
    var accounts = (List<Map<String, Object>>) summary.get("accounts");
    assertThat(accounts)
        .singleElement()
        .satisfies(a -> assertThat(a.get("monthlyListId")).isEqualTo(listId[0]));
    var afterLock = (List<Map<String, Object>>) summary.get("afterLock");
    assertThat(afterLock)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.get("transactionDate")).isEqualTo("2026-03-01");
              assertThat(e.get("typeLabel")).isEqualTo("Deposit");
              assertThat(e.get("clientName")).isEqualTo(client.name());
              assertThat((BigDecimal) e.get("cashbookEffect")).isEqualByComparingTo("250.00");
            });
    assertThat((BigDecimal) summary.get("afterLockNet")).isEqualByComparingTo("250.00");
    assertThat(books.statusOf(backdated)).isEqualTo("RECORDED");
  }

  /**
   * 650A (§16.8.1): firm-wide (no account) and with no list for the date, every account's list is
   * live; a person is one row across accounts with the general, invested and total balances; a Fund
   * line per account (even at zero); each open unidentified deposit; section 86(3) placements are a
   * note, not a creditor. Per account the comparison balances. A reconciliation completed before
   * 650A (2025-06-30, written by the repository) has no list: the source stays live.
   */
  @Test
  @SuppressWarnings("unchecked")
  void
      clientTrustBalances_live_firmWide_mergesGeneralAndInvested_andListsTheFundAndUnidentifiedLines() {
    var books = fixture.tenant(tenantSchema, ORG_ID, ownerMemberId);
    // The tenant is shared by the class: the firm-wide totals are checked as baseline + this
    // scenario's accounts
    var baseline = inTenant(() -> clientBalancesQuery.executeAll(Map.of())).summary();
    UUID funding = books.freshAccount(false);
    UUID investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    UUID firmInvestment = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var investor = books.newClient("report live investor");
    books.deposit(funding, investor, "5000.00");
    var placed = books.requestPlacement(funding, investmentAccount, investor, "2000.00");
    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-2000.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("2000.00"),
        placed.depositTransactionId(),
        approverMemberId);
    var firmPlaced = books.requestPlacement(funding, firmInvestment, null, "500.00");
    books.approvePlacement(
        funding,
        Expected.cashbookAndInterAccount("-500.00"),
        firmInvestment,
        Expected.cashbookAndInterAccount("500.00"),
        firmPlaced.depositTransactionId(),
        approverMemberId);
    UUID other = books.freshAccount(false);
    books.deposit(other, investor, "700.00");
    books.unidentifiedDepositDirect(other, "125.00", LocalDate.of(2026, 3, 5));

    var firm = inTenant(() -> clientBalancesQuery.executeAll(Map.of()));
    var person = onlyRow(firm, r -> investor.id().equals(r.get("customerId")));
    assertThat(person.get("kind")).isEqualTo("CLIENT");
    assertThat((BigDecimal) person.get("generalBalance")).isEqualByComparingTo("3700.00");
    assertThat((BigDecimal) person.get("investedBalance")).isEqualByComparingTo("2000.00");
    assertThat((BigDecimal) person.get("totalBalance")).isEqualByComparingTo("5700.00");
    assertThat((BigDecimal) person.get("balance")).isEqualByComparingTo("5700.00");
    assertThat(person.get("trustAccountId")).as("merged across accounts").isNull();
    assertThat(person.get("source")).isEqualTo("LIVE");
    for (var account : List.of(funding, investmentAccount, firmInvestment, other)) {
      var fund =
          onlyRow(
              firm, r -> "FUND".equals(r.get("kind")) && account.equals(r.get("trustAccountId")));
      assertThat((BigDecimal) fund.get("balance")).isEqualByComparingTo("0.00");
      assertThat(fund.get("sourceNote"))
          .isEqualTo("Not locked — this account's reconciliation for the month is not complete");
    }
    var item =
        onlyRow(
            firm,
            r -> "UNIDENTIFIED".equals(r.get("kind")) && other.equals(r.get("trustAccountId")));
    assertThat((BigDecimal) item.get("balance")).isEqualByComparingTo("125.00");
    assertThat(item.get("receivedDate")).isEqualTo("2026-03-05");
    assertThat(firm.rows()).noneMatch(r -> "INTER_ACCOUNT".equals(r.get("kind")));
    assertThat(firm.summary().get("fullyLocked")).isEqualTo(false);
    assertThat(firm.summary()).containsKey("interAccountNote");
    // Firm-wide comparison (650A review M3). Creditors: the investor R5 700 (general R3 000 on the
    // funding account + R700 on the other, R2 000 invested) and the R125 item; the four Fund lines
    // are nil and the section 86(3) placement nets out. Money side (no reconciliation has today's
    // period end, so each account's cashbook): banking accounts R2 500 (funding: 5 000 − 2 000 −
    // 500) + R825 (other: 700 + 125); investment accounts R2 000 + R500.
    assertThat((BigDecimal) firm.summary().get("creditorTotal"))
        .isEqualByComparingTo(
            ((BigDecimal) baseline.get("creditorTotal")).add(new BigDecimal("5825.00")));
    assertThat((BigDecimal) firm.summary().get("bankTotal"))
        .isEqualByComparingTo(
            ((BigDecimal) baseline.get("bankTotal")).add(new BigDecimal("3325.00")));
    assertThat((BigDecimal) firm.summary().get("investmentAccountTotal"))
        .isEqualByComparingTo(
            ((BigDecimal) baseline.get("investmentAccountTotal")).add(new BigDecimal("2500.00")));
    assertThat((BigDecimal) firm.summary().get("difference")).isEqualByComparingTo("0.00");
    assertThat((List<Map<String, Object>>) firm.summary().get("accounts"))
        .filteredOn(a -> funding.equals(a.get("trustAccountId")))
        .singleElement()
        .satisfies(a -> assertThat(a.get("source")).isEqualTo("LIVE"));

    // One account: the section 86(3) line is listed and the comparison balances
    var fundingOnly =
        inTenant(() -> clientBalancesQuery.executeAll(Map.of("trust_account_id", funding)));
    var placedLine = onlyRow(fundingOnly, r -> "INTER_ACCOUNT".equals(r.get("kind")));
    assertThat((BigDecimal) placedLine.get("balance")).isEqualByComparingTo("-500.00");
    assertThat(placedLine.get("clientName"))
        .isEqualTo("Placed in section 86(3) investment accounts");
    assertThat((BigDecimal) fundingOnly.summary().get("creditorTotal"))
        .isEqualByComparingTo("2500.00");
    assertThat((BigDecimal) fundingOnly.summary().get("difference")).isEqualByComparingTo("0.00");
    var invested =
        inTenant(
            () -> clientBalancesQuery.executeAll(Map.of("trust_account_id", investmentAccount)));
    assertThat(
            (BigDecimal)
                onlyRow(invested, r -> investor.id().equals(r.get("customerId")))
                    .get("investedBalance"))
        .isEqualByComparingTo("2000.00");
    assertThat((BigDecimal) invested.summary().get("investmentAccountTotal"))
        .isEqualByComparingTo("2000.00");
    assertThat((BigDecimal) invested.summary().get("difference")).isEqualByComparingTo("0.00");

    // No back-fill: the pre-650A completed reconciliation (2025-06-30) has no list
    var legacy =
        inTenant(
            () ->
                clientBalancesQuery.executeAll(
                    Map.of("trust_account_id", trustAccountId, "asOfDate", "2025-06-30")));
    assertThat((List<Map<String, Object>>) legacy.summary().get("accounts"))
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.get("source")).isEqualTo("LIVE");
              assertThat(a.get("monthlyListId")).isNull();
            });
  }

  /**
   * 650A (§16.8.2): the trust position as at a date is {@code checkFirm(date)} per account — the
   * cashbook, each creditor term and the difference — with the bank side only where a
   * reconciliation has that period end; {@code asOfDate} is required.
   */
  @Test
  @SuppressWarnings("unchecked")
  void trustPosition_perAccountTermsAndFirmTotals_fromTheIntegrityCheck() {
    var books = fixture.tenant(tenantSchema, ORG_ID, ownerMemberId);
    UUID account = books.freshAccount(false);
    books.deposit(account, books.newClient("report position"), "1500.00");
    var date = LocalDate.of(2026, 3, 20);

    var result =
        inTenant(
            () ->
                trustPositionQuery.executeAll(
                    Map.of("asOfDate", date.toString(), "trust_account_id", account.toString())));
    var row = onlyRow(result, r -> account.equals(r.get("trustAccountId")));
    // 650C (D-20): the section and the integrity flags in words for the report columns
    assertThat(row.get("sectionLabel")).isEqualTo("Section 86(2) trust banking account");
    assertThat((String) row.get("integrity")).isNotBlank().doesNotMatch(RAW_CODE);
    assertThat(result.summary().get("scopeNote")).isEqualTo(TrustReportWords.TRUST_ASSETS_NOTE);
    var expected =
        inTenant(
            () ->
                trustIntegrityService.checkFirm(date).accounts().stream()
                    .filter(a -> a.trustAccountId().equals(account))
                    .findFirst()
                    .orElseThrow());
    assertThat((BigDecimal) row.get("cashbook"))
        .isEqualByComparingTo(expected.position().cashbook())
        .isEqualByComparingTo("1500.00");
    assertThat((BigDecimal) row.get("clientLedgers")).isEqualByComparingTo("1500.00");
    assertThat((BigDecimal) row.get("fundLedger")).isEqualByComparingTo("0.00");
    assertThat((BigDecimal) row.get("creditorTotal"))
        .isEqualByComparingTo(expected.position().creditorTotal());
    assertThat((BigDecimal) row.get("difference")).isEqualByComparingTo("0.00");
    assertThat(row.get("bankBalance")).isNull();
    assertThat(row.get("bankNote"))
        .isEqualTo(
            "No reconciliation has this period end — the cashbook is compared with trust creditors"
                + " only");
    assertThat(row.get("balanced")).isEqualTo(true);
    assertThat((List<Map<String, Object>>) row.get("flags")).isEmpty();
    assertThat(result.summary())
        .containsKeys("cashbook", "creditorTotal", "interAccount", "balanced");

    var firmWide = inTenant(() -> trustPositionQuery.executeAll(Map.of("asOfDate", date)));
    assertThat(firmWide.rows()).anyMatch(r -> account.equals(r.get("trustAccountId")));

    assertThatThrownBy(() -> inTenant(() -> trustPositionQuery.executeAll(Map.of())))
        .isInstanceOfSatisfying(
            InvalidStateException.class,
            e -> {
              assertThat(e.getBody().getStatus()).isEqualTo(400);
              assertThat(e.getBody().getTitle()).isEqualTo("Missing required parameter");
              assertThat(e.getBody().getDetail()).isEqualTo("Parameter 'asOfDate' is required");
            });
    var unknownAccount = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                inTenant(
                    () ->
                        trustPositionQuery.executeAll(
                            Map.of("asOfDate", date, "trust_account_id", unknownAccount))))
        .isInstanceOfSatisfying(
            ResourceNotFoundException.class,
            e -> {
              assertThat(e.getBody().getStatus()).isEqualTo(404);
              assertThat(e.getBody().getTitle()).isEqualTo("TrustAccount not found");
              assertThat(e.getBody().getDetail())
                  .isEqualTo("No trustaccount found with id " + unknownAccount);
            });
  }

  private <T> T inTenant(Supplier<T> query) {
    var result = new java.util.concurrent.atomic.AtomicReference<T>();
    runInTenant(() -> result.set(query.get()));
    return result.get();
  }

  private static Map<String, Object> onlyRow(
      ReportResult result, java.util.function.Predicate<Map<String, Object>> matching) {
    var rows = result.rows().stream().filter(matching).toList();
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }

  private BankStatementResponse importStatement(
      TrustBooksFixture.Tenant books, UUID account, String fileName, String... lines) {
    var csv =
        """
        FNB Trust Account Statement - Account 62700000000
        Date,Description,Amount,Balance,Reference
        %s
        """
            .formatted(String.join("\n", lines));
    var file =
        new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    var statement = new BankStatementResponse[1];
    books.as(
        ownerMemberId,
        () -> statement[0] = trustReconciliationService.importBankStatement(account, file));
    return statement[0];
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

          // v4 (650C, D-20): both comparisons and the stored creditor breakdown, in words
          assertThat(result.rows())
              .extracting(r -> r.get("section") + " | " + r.get("label"))
              .containsExactly(
                  "Bank | Bank balance per statement",
                  "Adjustments | Add: outstanding deposits",
                  "Adjustments | Less: outstanding payments",
                  "Adjustments | Adjusted bank balance",
                  "Comparison with the cashbook | Cashbook balance",
                  "Comparison with the cashbook | Difference (adjusted bank – cashbook)",
                  "Trust creditors | Client ledgers",
                  "Trust creditors | Section 86(4) investment creditors",
                  "Trust creditors | Legal Practitioners' Fidelity Fund — interest and charges",
                  "Trust creditors | Unidentified deposits",
                  "Trust creditors | Section 86(3) placements (inter-account)",
                  "Trust creditors | Trust creditors total",
                  "Comparison with trust creditors | Difference (adjusted bank – trust creditors)",
                  "Comparison with trust creditors | Difference (cashbook – trust creditors)");
          var recon = reconciliationRepository.findById(reconciliationId).orElseThrow();
          var amounts =
              result.rows().stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          r -> (String) r.get("label"), r -> (BigDecimal) r.get("amount")));
          assertThat(amounts.get("Trust creditors total"))
              .isEqualByComparingTo(recon.getCreditorTotal());
          assertThat(amounts.get("Client ledgers"))
              .isEqualByComparingTo(recon.getClientLedgerTotal());
          assertThat(amounts.get("Difference (adjusted bank – trust creditors)"))
              .isEqualByComparingTo(recon.getDifference());
          assertThat(amounts.get("Difference (adjusted bank – cashbook)"))
              .isEqualByComparingTo(
                  recon.getAdjustedBankBalance().subtract(recon.getCashbookBalance()));
          assertThat(result.rows())
              .allSatisfy(r -> assertThat((String) r.get("section")).doesNotMatch(RAW_CODE));
          assertThat(result.summary().get("status")).isEqualTo("Completed");
          assertThat(result.summary().get("scopeNote"))
              .isEqualTo(TrustReportWords.TRUST_ASSETS_NOTE);

          // Verify summary
          assertThat(result.summary()).containsKey("bankBalance");
          assertThat(result.summary()).containsKey("cashbookBalance");
          assertThat(result.summary()).containsKey("isBalanced");
          assertThat((boolean) result.summary().get("isBalanced")).isTrue();
        });
  }

  /**
   * F-650C-OBS-2: the PDF falls back to the standard fonts (WinAnsi), which have no U+2212 minus —
   * it printed "#". Every label is WinAnsi-safe and the PDF prints the comparisons as written.
   */
  @Test
  void trustReconciliationPdf_printsTheComparisonLabelsWithoutAReplacementGlyph() {
    runInTenant(
        () -> {
          var params = new HashMap<String, Object>();
          params.put("trust_account_id", trustAccountId);
          params.put("reconciliation_id", reconciliationId);
          var result = reconciliationReportQuery.executeAll(params);
          var winAnsi = java.nio.charset.Charset.forName("windows-1252").newEncoder();
          assertThat(result.rows())
              .extracting(r -> (String) r.get("label"))
              .allSatisfy(label -> assertThat(winAnsi.canEncode(label)).as(label).isTrue());

          byte[] pdf =
              renderingService.renderPdf(
                  TrustReportPackSeeder.shipped("trust-reconciliation"), result, params);
          String text;
          try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
          } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
          }
          assertThat(text)
              .contains(
                  "Difference (adjusted bank – cashbook)",
                  "Difference (adjusted bank – trust creditors)",
                  "Difference (cashbook – trust creditors)")
              .doesNotContain("#");
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
          assertThat(sectionNames).contains("Trust creditors (Rule 54.15)");
          assertThat(sectionNames).doesNotContain("Client Trust Balances", "Interest Allocation");
          assertThat(sectionNames)
              .contains(
                  "Interest and charges schedule (Rule 54.28)",
                  "Fund sweeps and their statement lines (Rule 54.14.16)",
                  "Fund sweeps with no statement line",
                  "Client Ledger Statements");
          assertThat(sectionNames).contains("Trust Reconciliation");
          // 650C (D-14): investments on the books at the year end — the section 86(3) placement
          // still awaits approval (never posted), so it is not on the books and has no section
          assertThat(sectionNames).doesNotContain("Section 86(3) Investments (Firm Discretion)");

          // Verify rows have section markers
          var rowSections =
              result.rows().stream().map(r -> (String) r.get("_section")).distinct().toList();
          assertThat(rowSections).hasSizeGreaterThanOrEqualTo(2);

          // Verify financial year summary
          assertThat(result.summary().get("financialYearStart")).isEqualTo("2025-04-01");
          assertThat(result.summary().get("financialYearEnd")).isEqualTo("2026-03-31");
        });
  }

  /**
   * 650C (§16.8, D-18, F-15): every one of the nine v4 report templates renders on real figures — a
   * null number would 500 the PDF export — prints the scope note in its header and never a raw type
   * or status code; every slug has its query bean and the retired one has none.
   */
  @Test
  void everyV4Template_rendersWithTheScopeNote_andNoRawCode() {
    var params = new java.util.LinkedHashMap<String, Map<String, Object>>();
    params.put(
        "trust-receipts-payments",
        Map.of(
            "trust_account_id", trustAccountId, "dateFrom", "2025-04-01", "dateTo", "2025-06-30"));
    params.put(
        "client-trust-balances",
        Map.of("trust_account_id", trustAccountId, "asOfDate", "2025-06-30"));
    params.put("trust-position", Map.of("asOfDate", "2025-06-30"));
    params.put(
        "client-ledger-statement",
        Map.of(
            "trust_account_id",
            trustAccountId,
            "customer_id",
            customerId1,
            "dateFrom",
            "2025-04-01",
            "dateTo",
            "2025-06-30"));
    params.put(
        "trust-reconciliation",
        Map.of("trust_account_id", trustAccountId, "reconciliation_id", reconciliationId));
    params.put("investment-register", Map.of("trust_account_id", trustAccountId));
    params.put(
        "trust-interest-charges-schedule",
        Map.of(
            "trust_account_id", trustAccountId, "dateFrom", "2025-04-01", "dateTo", "2025-06-30"));
    params.put("unidentified-deposits-age-analysis", Map.of("asOfDate", "2025-06-30"));
    params.put(
        "section-35-data-pack",
        Map.of("trust_account_id", trustAccountId, "financial_year_end", "2026-03-31"));
    assertThat(params.keySet()).containsExactlyElementsOf(TrustReportPackSeeder.SLUGS);
    var bySlug =
        reportQueries.stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    io.b2mash.b2b.b2bstrawman.reporting.ReportQuery::getSlug, q -> q));
    assertThat(bySlug).doesNotContainKey("interest-allocation");

    runInTenant(
        () ->
            params.forEach(
                (slug, p) -> {
                  assertThat(bySlug).as(slug).containsKey(slug);
                  var result = bySlug.get(slug).executeAll(new HashMap<>(p));
                  assertThat(result.summary().get("scopeNote"))
                      .as(slug)
                      .isEqualTo(TrustReportWords.TRUST_ASSETS_NOTE);
                  var html =
                      renderingService.renderHtml(TrustReportPackSeeder.shipped(slug), result, p);
                  var text =
                      html.replaceAll("(?s)<style>.*?</style>", " ").replaceAll("<[^>]+>", " ");
                  assertThat(text).as(slug).contains(TrustReportWords.TRUST_ASSETS_NOTE);
                  assertThat(text).as(slug).doesNotMatch("(?s).*\\b[A-Z]{2,}_[A-Z_]{2,}\\b.*");
                }));
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
