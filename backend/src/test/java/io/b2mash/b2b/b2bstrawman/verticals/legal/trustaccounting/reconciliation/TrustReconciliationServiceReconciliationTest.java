package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsService;
import io.b2mash.b2b.b2bstrawman.testutil.TestEntityHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TestTrustMatterHelper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
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
class TrustReconciliationServiceReconciliationTest {

  private static final String ORG_ID = "org_trust_recon_calc_test";

  @Autowired private MockMvc mockMvc;
  @Autowired private io.b2mash.b2b.b2bstrawman.customer.CustomerRepository customerRepository;
  @Autowired private io.b2mash.b2b.b2bstrawman.project.ProjectRepository projectRepository;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private OrgSettingsRepository orgSettingsRepository;
  @Autowired private OrgSettingsService orgSettingsService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private TrustReconciliationRepository trustReconciliationRepository;

  @Autowired private Clock clock;

  private String tenantSchema;
  private String customerId;

  /** Counter to generate unique account numbers for isolated trust accounts. */
  private int isolatedAccountCounter = 0;

  @BeforeAll
  void setup() throws Exception {
    tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Trust Recon Calc Test Org", null).schemaName();

    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, "user_rcalc_owner", "rcalc_owner@test.com", "Rcalc Owner", "owner");

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

    // Create a customer for deposits
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_rcalc_owner");
    customerId =
        TestEntityHelper.createCustomer(
            mockMvc, ownerJwt, "Recon Calc Test Customer", "rcalc_cust@test.com");
  }

  /**
   * Creates a fresh trust account isolated from other tests (646C: every test — the period rules
   * look at every statement of the account, so a shared account's statements would refuse each
   * other's reconciliations).
   */
  private UUID createIsolatedTrustAccount() throws Exception {
    isolatedAccountCounter++;
    var ownerJwt = TestJwtFactory.ownerJwt(ORG_ID, "user_rcalc_owner");
    var accountResult =
        mockMvc
            .perform(
                post("/api/trust-accounts")
                    .with(ownerJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "accountName": "Isolated Trust Account %d",
                          "bankName": "First National Bank",
                          "branchCode": "250655",
                          "accountNumber": "62100000%03d",
                          "accountType": "GENERAL",
                          "isPrimary": false,
                          "requireDualApproval": false,
                          "openedDate": "2026-01-15"
                        }
                        """
                            .formatted(isolatedAccountCounter, isolatedAccountCounter)))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(TestEntityHelper.extractId(accountResult));
  }

  private String createDepositForAccount(
      UUID accountId, String reference, String amount, String date) throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/trust-accounts/" + accountId + "/transactions/deposit")
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rcalc_owner"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "clientId": "%s",
                          "matterId": "%s",
                          "amount": %s,
                          "reference": "%s",
                          "description": "Test deposit",
                          "transactionDate": "%s"
                        }
                        """
                            .formatted(
                                customerId, trustMatterOf(customerId), amount, reference, date)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private String importStatementForAccount(UUID accountId, String csv) throws Exception {
    return importStatementForAccount(
        accountId, "recon-calc-test-" + System.nanoTime() + ".csv", csv);
  }

  private String importStatementForAccount(UUID accountId, String fileName, String csv)
      throws Exception {
    var csvFile =
        new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    var result =
        mockMvc
            .perform(
                multipart("/api/trust-accounts/{accountId}/bank-statements", accountId)
                    .file(csvFile)
                    .with(TestJwtFactory.ownerJwt(ORG_ID, "user_rcalc_owner")))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id").toString();
  }

  private TrustReconciliationService.TrustReconciliationResponse create(
      UUID accountId, LocalDate periodEnd, String statementId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(
            () ->
                reconciliationService.createReconciliation(
                    accountId,
                    periodEnd,
                    statementId == null ? null : UUID.fromString(statementId)));
  }

  private TrustReconciliationService.TrustReconciliationResponse calculate(UUID reconciliationId) {
    return ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .call(() -> reconciliationService.calculateReconciliation(reconciliationId));
  }

  private void autoMatch(String statementId) {
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(() -> reconciliationService.autoMatchStatement(UUID.fromString(statementId)));
  }

  /**
   * {@code createReconciliation} is refused with a 400 ProblemDetail {@code title}/{@code detail}.
   */
  private void assertCreateRefused(
      UUID accountId, LocalDate periodEnd, String statementId, String title, String detail) {
    assertThatThrownBy(() -> create(accountId, periodEnd, statementId))
        .isInstanceOfSatisfying(
            InvalidStateException.class,
            e -> {
              assertThat(e.getBody().getStatus()).isEqualTo(400);
              assertThat(e.getBody().getTitle()).isEqualTo(title);
              assertThat(e.getBody().getDetail()).isEqualTo(detail);
            });
  }

  // --- Task 444.9: Reconciliation Calculation Tests ---

  @Test
  void calculateReconciliation_populatesAllBalanceFields() throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    // Create a deposit (will appear as unmatched cashbook entry)
    createDepositForAccount(accountId, "DEP-CALC-001", "50000.00", "2026-03-01");

    // Import a bank statement with closing balance of 50000
    var statementId =
        importStatementForAccount(
            accountId,
            """
            FNB Trust Account Statement - Account 62000000002
            Date,Description,Amount,Balance,Reference
            01/03/2026,Opening Balance,0.00,0.00,
            01/03/2026,Deposit received,50000.00,50000.00,DEP-CALC-001
            """);

    // Auto-match to link the deposit to the bank statement line
    autoMatch(statementId);

    // Create a reconciliation and calculate
    var reconResponse = create(accountId, LocalDate.of(2026, 3, 31), statementId);
    var calculated = calculate(reconResponse.id());

    assertThat(calculated.bankBalance()).isEqualByComparingTo(new BigDecimal("50000.00"));
    assertThat(calculated.cashbookBalance()).isNotNull();
    assertThat(calculated.clientLedgerTotal()).isNotNull();
    assertThat(calculated.outstandingDeposits()).isNotNull();
    assertThat(calculated.outstandingPayments()).isNotNull();
    assertThat(calculated.adjustedBankBalance()).isNotNull();
    assertThat(calculated.status()).isEqualTo("DRAFT");
  }

  @Test
  void calculateReconciliation_isBalancedTrueWhenAllThreeAgree() throws Exception {
    // Use an isolated trust account so the test is deterministic (no shared state from other tests)
    UUID isolatedAccountId = createIsolatedTrustAccount();

    // Create a single deposit — the only transaction on this account
    createDepositForAccount(isolatedAccountId, "DEP-BAL-001", "25000.00", "2026-03-05");

    // Import a statement whose closing balance matches the single deposit (25000)
    var statementId =
        importStatementForAccount(
            isolatedAccountId,
            """
            FNB Trust Account Statement - Account 62100000001
            Date,Description,Amount,Balance,Reference
            05/03/2026,Deposit received,25000.00,25000.00,DEP-BAL-001
            """);

    // Auto-match the deposit to the bank statement line (eliminates outstanding items)
    autoMatch(statementId);

    var reconResponse = create(isolatedAccountId, LocalDate.of(2026, 3, 31), statementId);
    var calculated = calculate(reconResponse.id());

    // All three values must agree for isBalanced to be true:
    // adjustedBankBalance == cashbookBalance == clientLedgerTotal
    assertThat(calculated.adjustedBankBalance().compareTo(calculated.cashbookBalance()))
        .as("adjustedBankBalance should equal cashbookBalance")
        .isZero();
    assertThat(calculated.cashbookBalance().compareTo(calculated.clientLedgerTotal()))
        .as("cashbookBalance should equal clientLedgerTotal")
        .isZero();
    assertThat(calculated.isBalanced()).isTrue();
    assertThat(calculated.bankBalance()).isEqualByComparingTo(new BigDecimal("25000.00"));

    // 646C: the creditor side broken down, both comparisons and the line figures
    var breakdown = calculated.creditorBreakdown();
    assertThat(breakdown.clientLedgers()).isEqualByComparingTo("25000.00");
    assertThat(breakdown.investmentCreditors()).isEqualByComparingTo("0");
    assertThat(breakdown.fundInterestAndCharges()).isEqualByComparingTo("0");
    assertThat(breakdown.unidentifiedDeposits()).isEqualByComparingTo("0");
    assertThat(breakdown.otherNonClient()).isEqualByComparingTo("0");
    assertThat(breakdown.interAccount()).isEqualByComparingTo("0");
    assertThat(breakdown.total()).isEqualByComparingTo("25000.00");
    assertThat(calculated.bankToCashbookDifference()).isEqualByComparingTo("0");
    assertThat(calculated.cashbookToCreditorsDifference()).isEqualByComparingTo("0");
    assertThat(calculated.difference()).isEqualByComparingTo("0");
    assertThat(calculated.excludedLinesTotal()).isEqualByComparingTo("0");
    assertThat(calculated.unresolvedLineCount()).isZero();

    // The stored breakdown is what a later read returns
    var reread =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> reconciliationService.getReconciliation(reconResponse.id()));
    assertThat(reread.creditorBreakdown())
        .usingRecursiveComparison()
        .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
        .isEqualTo(calculated.creditorBreakdown());
    assertThat(reread.difference()).isEqualByComparingTo(calculated.difference());
  }

  /**
   * 646C: an excluded line adjusts nothing — excluding a real bank movement leaves exactly that
   * amount as the bank-to-cashbook difference; the response shows the excluded total and the count
   * of lines neither matched nor excluded.
   */
  @Test
  void calculateReconciliation_reportsExcludedTotalAndUnresolvedLines() throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    createDepositForAccount(accountId, "DEP-EXC-001", "25000.00", "2026-03-05");
    var statementId =
        importStatementForAccount(
            accountId,
            """
            FNB Trust Account Statement - Account 62100000000
            Date,Description,Amount,Balance,Reference
            05/03/2026,Deposit received,25000.00,25000.00,DEP-EXC-001
            10/03/2026,Bank charge,-15.00,24985.00,CHG-EXC
            12/03/2026,Unknown credit,100.00,25085.00,UNK-EXC
            """);
    autoMatch(statementId);
    var chargeLine =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .call(() -> reconciliationService.getBankStatement(UUID.fromString(statementId)))
            .lines()
            .stream()
            .filter(l -> "CHG-EXC".equals(l.reference()))
            .findFirst()
            .orElseThrow();
    ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
        .run(() -> reconciliationService.excludeLine(chargeLine.id(), "bank charge test"));

    var calculated = calculate(create(accountId, LocalDate.of(2026, 3, 31), statementId).id());

    assertThat(calculated.bankBalance()).isEqualByComparingTo("25085.00");
    assertThat(calculated.cashbookBalance()).isEqualByComparingTo("25000.00");
    assertThat(calculated.creditorBreakdown().total()).isEqualByComparingTo("25000.00");
    assertThat(calculated.bankToCashbookDifference()).isEqualByComparingTo("85.00");
    assertThat(calculated.cashbookToCreditorsDifference()).isEqualByComparingTo("0");
    assertThat(calculated.difference()).isEqualByComparingTo("85.00");
    assertThat(calculated.excludedLinesTotal()).isEqualByComparingTo("-15.00");
    assertThat(calculated.unresolvedLineCount()).isEqualTo(1);
    assertThat(calculated.isBalanced()).isFalse();
  }

  @Test
  void calculateReconciliation_isBalancedFalseWithDiscrepancy() throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    // Create a deposit that won't be on any bank statement line (unmatched = outstanding)
    createDepositForAccount(accountId, "DEP-DISC-001", "10000.00", "2026-03-10");

    // Import a statement that does NOT include this deposit
    var statementId =
        importStatementForAccount(
            accountId,
            """
            FNB Trust Account Statement - Account 62000000002
            Date,Description,Amount,Balance,Reference
            10/03/2026,Other transaction,5000.00,80000.00,OTHER-001
            """);

    var reconResponse = create(accountId, LocalDate.of(2026, 3, 31), statementId);
    var calculated = calculate(reconResponse.id());

    // Bank 80 000 + outstanding deposit 10 000 = 90 000 against a cashbook of 10 000
    assertThat(calculated.outstandingDeposits()).isEqualByComparingTo("10000.00");
    assertThat(calculated.adjustedBankBalance()).isEqualByComparingTo("90000.00");
    assertThat(calculated.cashbookBalance()).isEqualByComparingTo("10000.00");
    assertThat(calculated.isBalanced()).isFalse();
  }

  @Test
  void calculateReconciliation_identifiesOutstandingItems() throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    // Create an unmatched deposit (outstanding deposit)
    createDepositForAccount(accountId, "DEP-OUT-001", "15000.00", "2026-03-15");

    // Import a statement that doesn't include this deposit
    var statementId =
        importStatementForAccount(
            accountId,
            """
            FNB Trust Account Statement - Account 62000000002
            Date,Description,Amount,Balance,Reference
            15/03/2026,Bank charge,-100.00,79900.00,CHARGE-001
            """);

    var reconResponse = create(accountId, LocalDate.of(2026, 3, 31), statementId);
    var calculated = calculate(reconResponse.id());

    // The unmatched DEP-OUT-001 (15000) is the only outstanding deposit
    assertThat(calculated.outstandingDeposits()).isEqualByComparingTo(new BigDecimal("15000.00"));
  }

  // --- 646C: the period rules of createReconciliation (§16.3.4) ---

  /** R1 — a reconciliation needs its bank statement. */
  @Test
  void createReconciliation_refusedWithoutABankStatement() throws Exception {
    UUID accountId = createIsolatedTrustAccount();

    assertCreateRefused(
        accountId,
        LocalDate.of(2026, 3, 31),
        null,
        "Missing bank statement",
        "Import the bank statement for this period before starting the reconciliation.");
  }

  /** R2 — the statement may hold no line dated after the period end. */
  @Test
  void createReconciliation_refusedWhenTheStatementHasALineAfterThePeriodEnd() throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    var statementId =
        importStatementForAccount(
            accountId,
            """
            FNB Trust Account Statement - Account 62100000000
            Date,Description,Amount,Balance,Reference
            20/03/2026,Deposit received,1000.00,1000.00,R2-001
            02/04/2026,Deposit received,500.00,1500.00,R2-002
            """);

    assertCreateRefused(
        accountId,
        LocalDate.of(2026, 3, 31),
        statementId,
        "Statement does not fit the period end",
        "This statement has lines dated after 31 March 2026. Choose a later period end, or import"
            + " a statement that ends on or before it.");
  }

  /**
   * R3 — another imported statement of the account with movements between this statement's last
   * line and the period end refuses the reconciliation (the bank balance as at the period end is
   * not this statement's closing balance).
   */
  @Test
  void createReconciliation_refusedWhenAnotherStatementCoversTheGap() throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    var earlyId =
        importStatementForAccount(
            accountId,
            "r3-early.csv",
            """
            FNB Trust Account Statement - Account 62100000000
            Date,Description,Amount,Balance,Reference
            05/03/2026,Deposit received,1000.00,1000.00,R3-001
            """);
    importStatementForAccount(
        accountId,
        "r3-late.csv",
        """
        FNB Trust Account Statement - Account 62100000000
        Date,Description,Amount,Balance,Reference
        20/03/2026,Deposit received,500.00,1500.00,R3-002
        """);

    assertCreateRefused(
        accountId,
        LocalDate.of(2026, 3, 31),
        earlyId,
        "Statement does not fit the period end",
        "Statement r3-late.csv has movements between this statement's last line and 31 March 2026."
            + " Reconcile against the statement that covers the period end.");
  }

  /** R4 — the period end cannot be in the future (SA business date, the injected Clock). */
  @Test
  void createReconciliation_refusedForAFuturePeriodEnd() throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    var statementId =
        importStatementForAccount(
            accountId,
            """
            FNB Trust Account Statement - Account 62100000000
            Date,Description,Amount,Balance,Reference
            05/03/2026,Deposit received,1000.00,1000.00,R4-001
            """);

    assertCreateRefused(
        accountId,
        LocalDate.now(clock).plusDays(1),
        statementId,
        "Invalid period end",
        "The period end cannot be in the future");
  }

  /** Rule 1 — the statement must belong to the account being reconciled. */
  @Test
  void createReconciliation_refusedWhenTheStatementBelongsToAnotherAccount() throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    UUID otherAccountId = createIsolatedTrustAccount();
    var otherStatementId =
        importStatementForAccount(
            otherAccountId,
            """
            FNB Trust Account Statement - Account 62100000000
            Date,Description,Amount,Balance,Reference
            05/03/2026,Deposit received,1000.00,1000.00,RULE1-001
            """);

    assertCreateRefused(
        accountId,
        LocalDate.of(2026, 3, 31),
        otherStatementId,
        "Trust account mismatch",
        "Bank statement does not belong to the specified trust account");
  }

  /** Rule 2 boundary — a line dated exactly on the period end fits it. */
  @Test
  void createReconciliation_acceptsAStatementLineDatedOnThePeriodEnd() throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    var statementId =
        importStatementForAccount(
            accountId,
            """
            FNB Trust Account Statement - Account 62100000000
            Date,Description,Amount,Balance,Reference
            05/03/2026,Deposit received,1000.00,1000.00,RULE2-001
            31/03/2026,Deposit received,500.00,1500.00,RULE2-002
            """);

    var created = create(accountId, LocalDate.of(2026, 3, 31), statementId);

    assertThat(created.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(created.status()).isEqualTo("DRAFT");
  }

  /** Rule 3 boundary — another statement's line dated exactly on the period end refuses. */
  @Test
  void createReconciliation_refusedWhenAnotherStatementHasALineOnThePeriodEnd() throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    var earlyId =
        importStatementForAccount(
            accountId,
            "rule3-early.csv",
            """
            FNB Trust Account Statement - Account 62100000000
            Date,Description,Amount,Balance,Reference
            05/03/2026,Deposit received,1000.00,1000.00,RULE3-001
            """);
    importStatementForAccount(
        accountId,
        "rule3-on-period-end.csv",
        """
        FNB Trust Account Statement - Account 62100000000
        Date,Description,Amount,Balance,Reference
        31/03/2026,Deposit received,500.00,1500.00,RULE3-002
        """);

    assertCreateRefused(
        accountId,
        LocalDate.of(2026, 3, 31),
        earlyId,
        "Statement does not fit the period end",
        "Statement rule3-on-period-end.csv has movements between this statement's last line and 31"
            + " March 2026. Reconcile against the statement that covers the period end.");
  }

  /**
   * Rule 3 boundary — another statement's line dated on this statement's last line date is not in
   * the gap (the gap starts after the last line), so it does not refuse.
   */
  @Test
  void createReconciliation_acceptsAnotherStatementLineOnThisStatementsLastLineDate()
      throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    var earlyId =
        importStatementForAccount(
            accountId,
            "rule3-early-same-day.csv",
            """
            FNB Trust Account Statement - Account 62100000000
            Date,Description,Amount,Balance,Reference
            05/03/2026,Deposit received,1000.00,1000.00,RULE3-003
            """);
    importStatementForAccount(
        accountId,
        "rule3-same-day.csv",
        """
        FNB Trust Account Statement - Account 62100000000
        Date,Description,Amount,Balance,Reference
        05/03/2026,Deposit received,500.00,1500.00,RULE3-004
        """);

    var created = create(accountId, LocalDate.of(2026, 3, 31), earlyId);

    assertThat(created.bankStatementId()).isEqualTo(UUID.fromString(earlyId));
    assertThat(created.status()).isEqualTo("DRAFT");
  }

  /** Rule 4 boundary — a period end of today (SA business date, the injected Clock) is accepted. */
  @Test
  void createReconciliation_acceptsAPeriodEndOfToday() throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    var statementId =
        importStatementForAccount(
            accountId,
            """
            FNB Trust Account Statement - Account 62100000000
            Date,Description,Amount,Balance,Reference
            05/03/2026,Deposit received,1000.00,1000.00,RULE4-001
            """);
    var today = LocalDate.now(clock);

    var created = create(accountId, today, statementId);

    assertThat(created.periodEnd()).isEqualTo(today);
    assertThat(created.status()).isEqualTo("DRAFT");
  }

  /**
   * R5 — a statement whose last line is before the month end reconciles to the month end, and the
   * figures are as at that date: a deposit dated in April is neither in March's cashbook nor a
   * March outstanding item.
   */
  @Test
  void calculateReconciliation_statementEndingBeforeTheMonthEnd_reconcilesAsAtThePeriodEnd()
      throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    createDepositForAccount(accountId, "DEP-R5-001", "25000.00", "2026-03-05");
    var statementId =
        importStatementForAccount(
            accountId,
            """
            FNB Trust Account Statement - Account 62100000000
            Date,Description,Amount,Balance,Reference
            05/03/2026,Deposit received,25000.00,25000.00,DEP-R5-001
            """);
    autoMatch(statementId);
    createDepositForAccount(accountId, "DEP-R5-002", "4000.00", "2026-04-05");

    var reconResponse = create(accountId, LocalDate.of(2026, 3, 31), statementId);
    var calculated = calculate(reconResponse.id());

    assertThat(calculated.bankBalance()).as("bank").isEqualByComparingTo("25000.00");
    assertThat(calculated.outstandingDeposits()).as("outstanding").isEqualByComparingTo("0.00");
    assertThat(calculated.adjustedBankBalance()).as("adjusted").isEqualByComparingTo("25000.00");
    assertThat(calculated.cashbookBalance())
        .as("cashbook 31 March")
        .isEqualByComparingTo("25000.00");
    assertThat(calculated.clientLedgerTotal())
        .as("clients 31 March")
        .isEqualByComparingTo("25000.00");
    assertThat(calculated.isBalanced()).isTrue();
  }

  // --- Task 444.10: Completion Guard Tests ---

  @Test
  void completeReconciliation_succeedsWhenBalanced() throws Exception {
    // Use an isolated trust account so the test is deterministic (no shared state from other tests)
    UUID isolatedAccountId = createIsolatedTrustAccount();

    // Create a single deposit — the only transaction on this account
    createDepositForAccount(isolatedAccountId, "DEP-COMP-001", "30000.00", "2026-03-20");

    // Import a statement whose closing balance matches the single deposit (30000)
    var statementId =
        importStatementForAccount(
            isolatedAccountId,
            """
            FNB Trust Account Statement - Account 62100000002
            Date,Description,Amount,Balance,Reference
            20/03/2026,Deposit received,30000.00,30000.00,DEP-COMP-001
            """);

    autoMatch(statementId);

    var reconResponse = create(isolatedAccountId, LocalDate.of(2026, 3, 31), statementId);
    var calculated = calculate(reconResponse.id());

    assertThat(calculated.isBalanced())
        .as("Reconciliation must be balanced before completing — check test data setup")
        .isTrue();

    var completed =
        ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
            .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
            .call(() -> reconciliationService.completeReconciliation(reconResponse.id()));

    assertThat(completed.status()).isEqualTo("COMPLETED");
    assertThat(completed.completedBy()).isNotNull();
    assertThat(completed.completedAt()).isNotNull();
  }

  @Test
  void completeReconciliation_failsWhenNotBalanced() throws Exception {
    UUID accountId = createIsolatedTrustAccount();
    var statementId =
        importStatementForAccount(
            accountId,
            """
            FNB Trust Account Statement - Account 62100000000
            Date,Description,Amount,Balance,Reference
            05/03/2026,Deposit received,1000.00,1000.00,NB-001
            """);
    // A DRAFT reconciliation never calculated: is_balanced = false (default)
    var reconResponse = create(accountId, LocalDate.of(2026, 3, 31), statementId);

    // Attempting to complete should fail
    assertThatThrownBy(
            () ->
                ScopedValue.where(RequestScopes.TENANT_ID, tenantSchema)
                    .where(RequestScopes.MEMBER_ID, UUID.randomUUID())
                    .run(() -> reconciliationService.completeReconciliation(reconResponse.id())))
        .isInstanceOf(InvalidStateException.class)
        .hasMessageContaining("not balanced");
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
                customerRepository,
                UUID.fromString(id),
                "Trust matter " + id));
  }
}
