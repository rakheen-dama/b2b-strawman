package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustMonthlyListService.MonthlyListResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.BankStatementRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLineClassification;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingService.RecordFromLineRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.TrustReconciliationResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Phase 92 slice 650A (task 650.4, architecture §16.3.10, §16.9.2 paths 23 and 24 lock part,
 * ADR-382): the Rule 54.15 monthly list lock. Completing a reconciliation to a month end freezes
 * the account's list in the same transaction; a mid-month reconciliation locks nothing; a second
 * reconciliation to a locked month end is refused and rolled back; a row back-dated into a locked
 * month leaves the list unchanged, moves the live as-at position and raises {@code
 * BACKDATED_AFTER_LOCK}; the two read endpoints.
 *
 * <p>One harness tenant, a fresh account per test; the class ends with {@code assertFirmBalanced}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustMonthlyListIntegrationTest {

  private static final String ORG_ID = "org_trust_monthly_list";
  private static final String OWNER = "user_tml_owner";
  private static final String NO_TRUST = "user_tml_no_trust";

  private static final LocalDate MARCH_END = LocalDate.of(2026, 3, 31);
  private static final LocalDate FEBRUARY_END = LocalDate.of(2026, 2, 28);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private StatementLinePostingService statementLinePostingService;
  @Autowired private TrustMonthlyListService monthlyListService;
  @Autowired private TrustMonthlyListRepository monthlyListRepository;
  @Autowired private TrustIntegrityService integrityService;
  @Autowired private BankStatementRepository bankStatementRepository;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private EntityManager entityManager;

  private TrustBooksFixture.Tenant books;
  private UUID memberId;
  private int statementCounter = 0;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService.provisionTenant(ORG_ID, "Trust Monthly List Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "tml@test.com", "TML Owner", "owner"));
    var noTrustId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, NO_TRUST, "tml_no_trust@test.com", "TML No Trust", "member");
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    assignRole(owner, noTrustId, createCustomRole(owner, "TML No Trust Role", "VIEW_LEGAL"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, memberId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  // --- lock content ----------------------------------------------------------------------------

  /**
   * Two clients (one with a matter and an unmattered row), an unknown credit recorded as an
   * unidentified deposit and a non-recoverable bank charge (the Fund ledger in debit, a charge to
   * refund): the 31 March list freezes the reconciled money side, every creditor by name with the
   * per-matter breakdown, the Fund line, the item with its received date and description, the
   * stored flags, who locked it and when — and the jsonb round-trips.
   */
  @Test
  void lockContent_freezesTheMoneySide_everyCreditorByName_andTheFlags() {
    var account = books.freshAccount(false);
    var alpha = books.newClient("alpha");
    var beta = books.newClient("beta");
    var alphaDeposit = books.deposit(account, alpha, "1000.00");
    var betaDeposit = books.deposit(account, beta, "600.00");
    // An unmattered row for alpha, not on the statement (an outstanding deposit)
    books.postLedgerRowDirect(
        account, "DEPOSIT", "300.00", LocalDate.of(2026, 3, 10), alpha.id(), null, null, null);
    var march =
        importStatement(
            account,
            "tml-content-march.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,TML-A",
            "01/03/2026,Deposit received,600.00,1600.00,TML-B",
            "02/03/2026,ACB CREDIT UNKNOWN,1250.00,2850.00,TML-UNK",
            "20/03/2026,Service fee,-57.50,2792.50,TML-FEE");
    match(march, 0, alphaDeposit);
    match(march, 1, betaDeposit);
    recordLine(march.lines().get(2).id(), unidentified());
    recordLine(
        march.lines().get(3).id(),
        new RecordFromLineRequest(
            StatementLineClassification.BANK_CHARGE,
            false,
            BigDecimal.ZERO,
            null,
            null,
            null,
            null,
            null));

    var recon = reconcile(account, MARCH_END, march.id());
    assertThat(recon.isBalanced()).isTrue();
    var completed = complete(recon);
    assertThat(completed.monthlyListId()).isNotNull();

    var list = getList(completed.monthlyListId());
    assertThat(list.trustAccountId()).isEqualTo(account);
    assertThat(list.periodEnd()).isEqualTo(MARCH_END);
    assertThat(list.reconciliationId()).isEqualTo(recon.id());
    assertThat(list.bankBalance()).isEqualByComparingTo("2792.50");
    assertThat(list.outstandingDeposits()).isEqualByComparingTo("300.00");
    assertThat(list.outstandingPayments()).isEqualByComparingTo("0.00");
    assertThat(list.adjustedBankBalance()).isEqualByComparingTo("3092.50");
    assertThat(list.cashbookBalance()).isEqualByComparingTo("3092.50");
    assertThat(list.clientLedgerTotal()).isEqualByComparingTo("1900.00");
    assertThat(list.investmentCreditorTotal()).isEqualByComparingTo("0.00");
    assertThat(list.fundLedgerBalance()).isEqualByComparingTo("-57.50");
    assertThat(list.unidentifiedDepositTotal()).isEqualByComparingTo("1250.00");
    assertThat(list.interAccountBalance()).isEqualByComparingTo("0.00");
    assertThat(list.creditorTotal()).isEqualByComparingTo("3092.50");
    assertThat(list.difference()).isEqualByComparingTo("0.00");
    assertThat(list.lockedBy()).isEqualTo(memberId);
    assertThat(list.lockedByName()).isEqualTo("TML Owner");
    assertThat(list.lockedAt()).isNotNull();
    assertThat(list.trustAccountName()).isNotBlank();

    // Every creditor by name: CLIENT by name, then FUND, then UNIDENTIFIED; no inter-account line
    assertThat(list.lines())
        .extracting(CreditorLine::kind)
        .containsExactly(
            CreditorLine.Kind.CLIENT,
            CreditorLine.Kind.CLIENT,
            CreditorLine.Kind.FUND,
            CreditorLine.Kind.UNIDENTIFIED);
    var alphaLine = list.lines().get(0);
    assertThat(alphaLine.clientId()).isEqualTo(alpha.id());
    assertThat(alphaLine.name()).isEqualTo(alpha.name());
    assertThat(alphaLine.balance()).isEqualByComparingTo("1300.00");
    assertThat(alphaLine.matters()).hasSize(2);
    assertThat(
            alphaLine.matters().stream()
                .map(CreditorLine.MatterBalance::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .as("the per-matter breakdown sums to the client's balance")
        .isEqualByComparingTo("1300.00");
    assertThat(alphaLine.matters())
        .anySatisfy(
            m -> {
              assertThat(m.matterId()).isEqualTo(alpha.matter());
              assertThat(m.name()).isNotBlank();
              assertThat(m.balance()).isEqualByComparingTo("1000.00");
            })
        .anySatisfy(
            m -> {
              assertThat(m.matterId()).isNull();
              assertThat(m.balance()).isEqualByComparingTo("300.00");
            });
    // The client's posted totals and last posted date, frozen on its line (650A review C4)
    assertThat(alphaLine.totalDeposits()).isEqualByComparingTo("1300.00");
    assertThat(alphaLine.totalPayments()).isEqualByComparingTo("0.00");
    assertThat(alphaLine.totalFeeTransfers()).isEqualByComparingTo("0.00");
    assertThat(alphaLine.lastTransactionDate()).isEqualTo("2026-03-10");
    var betaLine = list.lines().get(1);
    assertThat(betaLine.clientId()).isEqualTo(beta.id());
    assertThat(betaLine.balance()).isEqualByComparingTo("600.00");
    assertThat(betaLine.totalDeposits()).isEqualByComparingTo("600.00");
    assertThat(betaLine.lastTransactionDate()).isEqualTo("2026-03-01");
    var fundLine = list.lines().get(2);
    assertThat(fundLine.name())
        .isEqualTo("Legal Practitioners' Fidelity Fund — interest and charges");
    assertThat(fundLine.balance()).isEqualByComparingTo("-57.50");
    assertThat(fundLine.totalDeposits()).as("totals ride on client lines only").isNull();
    assertThat(fundLine.lastTransactionDate()).isNull();
    var itemLine = list.lines().get(3);
    assertThat(itemLine.unidentifiedDepositId()).isNotNull();
    assertThat(itemLine.receivedDate()).isEqualTo("2026-03-02");
    assertThat(itemLine.bankDescription()).isEqualTo("ACB CREDIT UNKNOWN");
    assertThat(itemLine.balance()).isEqualByComparingTo("1250.00");
    assertThat(
            list.lines().stream()
                .map(CreditorLine::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .as("the lines sum to the creditor total")
        .isEqualByComparingTo(list.creditorTotal());

    // The flags stored at completion, frozen
    assertThat(list.integrityFlags()).isEqualTo(completed.integrityFlags());
    assertThat(list.integrityFlags())
        .extracting(IntegrityFlag::code)
        .containsExactly(
            TrustIntegrityService.FUND_LEDGER_IN_DEBIT, TrustIntegrityService.CHARGES_TO_REFUND);
    assertThat(list.adjustmentsSinceLock().entries()).isEmpty();
    assertThat(list.adjustmentsSinceLock().changeInCreditorTotal()).isEqualByComparingTo("0.00");

    // jsonb round trip in a fresh transaction (the received date included)
    var reread =
        books.read(() -> monthlyListRepository.findById(list.id()).orElseThrow().getLines());
    assertThat(reread).isEqualTo(list.lines());
    assertThat(reread.get(3).receivedDate()).isEqualTo("2026-03-02");

    // Audit
    var details =
        jdbc.queryForObject(
            """
            SELECT details::text FROM "%s".audit_events
            WHERE entity_id = ? AND event_type = 'trust_monthly_list.locked'
              AND entity_type = 'trust_monthly_list'
            """
                .formatted(books.schema()),
            String.class,
            list.id());
    assertThat(details)
        .contains("\"trust_account_id\": \"" + account + "\"")
        .contains("\"period_end\": \"2026-03-31\"")
        .contains("\"creditor_total\": \"3092.50\"")
        .contains("\"reconciliation_id\": \"" + recon.id() + "\"");
    books.assertServicesAgree(account, MARCH_END);
  }

  /** The Fund line is frozen even at zero, and the month is the period end the firm chose. */
  @Test
  void aStatementWhoseLastLineIsThe30th_stillLocksTheThirtyFirst_withTheFundLineAtZero() {
    var account = books.freshAccount(false);
    var client = books.newClient("thirtieth");
    var first = books.deposit(account, client, "400.00");
    var second =
        books.postLedgerRowDirect(
            account,
            "DEPOSIT",
            "100.00",
            LocalDate.of(2026, 3, 30),
            client.id(),
            client.matter(),
            null,
            null);
    var march =
        importStatement(
            account,
            "tml-thirtieth-march.csv",
            "01/03/2026,Deposit received,400.00,400.00,TML-30A",
            "30/03/2026,Deposit received,100.00,500.00,TML-30B");
    match(march, 0, first);
    match(march, 1, second);
    var completed = complete(reconcile(account, MARCH_END, march.id()));

    var row = books.read(() -> monthlyListRepository.findById(completed.monthlyListId()));
    assertThat(row).isPresent();
    assertThat(row.get().getPeriodEnd()).isEqualTo(MARCH_END);
    assertThat(row.get().getLines())
        .filteredOn(l -> l.kind() == CreditorLine.Kind.FUND)
        .singleElement()
        .satisfies(l -> assertThat(l.balance()).isEqualByComparingTo("0.00"));
  }

  // --- uniqueness -----------------------------------------------------------------------------

  /**
   * D-4, since 652C (§16.3.12): one reconciliation per account and period end — a second one to a
   * locked month end is refused when it is created (409), so it can never reach completion; the
   * lock's own guard ("Month already locked") stays as defence in depth. Still one reconciliation,
   * COMPLETED, and one list.
   */
  @Test
  void uniqueness_aSecondReconciliationToALockedMonthEnd_isRefusedAtCreate() {
    var account = books.freshAccount(false);
    var march = matchedMarchStatement(account, "tml-unique-march.csv");
    var first = complete(reconcile(account, MARCH_END, march.id()));
    assertThat(first.monthlyListId()).isNotNull();

    books.as(
        memberId,
        () ->
            assertThatThrownBy(
                    () ->
                        reconciliationService.createReconciliation(account, MARCH_END, march.id()))
                .isInstanceOfSatisfying(
                    ResourceConflictException.class,
                    e -> {
                      assertThat(e.getBody().getStatus()).isEqualTo(409);
                      assertThat(e.getBody().getTitle())
                          .isEqualTo("This period is already reconciled");
                      assertThat(e.getBody().getDetail())
                          .isEqualTo(
                              "This trust account was reconciled to 31 March 2026. A completed"
                                  + " reconciliation cannot be changed; choose a later period"
                                  + " end.");
                    }));
    var after = getReconciliation(first.id());
    assertThat(after.status()).as("the first is still COMPLETED").isEqualTo("COMPLETED");
    assertThat(after.monthlyListId()).isEqualTo(first.monthlyListId());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".trust_reconciliations WHERE trust_account_id = ?"
                    .formatted(books.schema()),
                Long.class,
                account))
        .as("one reconciliation for the account")
        .isEqualTo(1L);
    assertThat(
            books.read(
                () -> monthlyListRepository.findByTrustAccountIdOrderByPeriodEndDesc(account)))
        .extracting(TrustMonthlyList::getId)
        .containsExactly(first.monthlyListId());
  }

  // --- path 23 ----------------------------------------------------------------------------------

  /**
   * HARNESS path 23 (§16.9.2): a deposit dated 1 March, entered after March was locked, is recorded
   * normally (through {@code assertMoves}); the locked list is unchanged; the live as-at position
   * for 31 March moves by the deposit; {@code get} shows it as an adjustment since the lock; the
   * integrity check raises {@code BACKDATED_AFTER_LOCK} (a warning, one subject per list) — and not
   * for a date before the list's period end.
   */
  @Test
  void
      path23_aRowBackDatedIntoALockedMonth_leavesTheListUnchanged_movesTheLiveAsAtPosition_andRaisesTheFlag() {
    var account = books.freshAccount(false);
    var client = books.newClient("backdated");
    var march = matchedMarchStatement(account, "tml-p23-march.csv", client);
    var completed = complete(reconcile(account, MARCH_END, march.id()));
    var listId = completed.monthlyListId();
    var before = getList(listId);
    var lockedCashbook = before.cashbookBalance();

    // Path 23: a back-dated deposit (POSTING_DATE = 1 March) is recorded normally
    var backdated = books.deposit(account, client, "250.00");

    var after = getList(listId);
    assertThat(after.lines()).as("the locked lines never change").isEqualTo(before.lines());
    assertThat(after.cashbookBalance()).isEqualByComparingTo(lockedCashbook);
    assertThat(after.creditorTotal()).isEqualByComparingTo(before.creditorTotal());
    assertThat(after.integrityFlags()).isEqualTo(before.integrityFlags());
    assertThat(after.lockedAt()).isEqualTo(before.lockedAt());
    assertThat(books.read(() -> monthlyListRepository.findById(listId).orElseThrow().getLines()))
        .isEqualTo(before.lines());

    // The live as-at position moves
    var live = books.snapshotAsAt(account, MARCH_END);
    assertThat(live.cashbook()).isEqualByComparingTo(lockedCashbook.add(new BigDecimal("250.00")));

    // Shown against the list
    var adjustments = after.adjustmentsSinceLock();
    assertThat(adjustments.entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.transactionId()).isEqualTo(backdated);
              assertThat(e.transactionDate()).isEqualTo(LocalDate.of(2026, 3, 1));
              assertThat(e.typeLabel()).isEqualTo("Deposit");
              assertThat(e.clientId()).isEqualTo(client.id());
              assertThat(e.clientName()).isEqualTo(client.name());
              assertThat(e.cashbookEffect()).isEqualByComparingTo("250.00");
              assertThat(e.postedAt()).isAfter(before.lockedAt());
            });
    assertThat(adjustments.cashbook()).isEqualByComparingTo("250.00");
    assertThat(adjustments.clientLedgers()).isEqualByComparingTo("250.00");
    assertThat(adjustments.changeInCreditorTotal()).isEqualByComparingTo("250.00");
    assertThat(adjustments.liveCreditorTotal())
        .isEqualByComparingTo(before.creditorTotal().add(new BigDecimal("250.00")));
    assertThat(
            adjustments.entries().stream()
                .map(TrustMonthlyListService.AdjustmentEntry::cashbookEffect)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .as("Σ entries = live cashbook − locked cashbook")
        .isEqualByComparingTo(live.cashbook().subtract(lockedCashbook));

    // The flag: raised as at today and as at the period end, not before it
    var report = check(account, null);
    assertThat(report.balanced()).as("a warning never unbalances the books").isTrue();
    assertThat(report.flags())
        .filteredOn(f -> TrustIntegrityService.BACKDATED_AFTER_LOCK.equals(f.code()))
        .singleElement()
        .satisfies(
            f -> {
              assertThat(f.severity()).isEqualTo(TrustIntegrityService.WARNING);
              assertThat(f.message())
                  .isEqualTo(
                      "1 entry dated on or before 31 March 2026 was entered after the March 2026"
                          + " list was locked.");
              assertThat(f.amount()).isNull();
              assertThat(f.subjects())
                  .singleElement()
                  .satisfies(
                      s -> {
                        assertThat(s.kind()).isEqualTo(TrustIntegrityService.SUBJECT_MONTHLY_LIST);
                        assertThat(s.id()).isEqualTo(listId);
                        assertThat(s.name()).isEqualTo("March 2026 list");
                        assertThat(s.amount()).isEqualByComparingTo("250.00");
                      });
            });
    assertThat(codes(check(account, MARCH_END)))
        .contains(TrustIntegrityService.BACKDATED_AFTER_LOCK);
    assertThat(codes(check(account, MARCH_END.minusDays(1))))
        .doesNotContain(TrustIntegrityService.BACKDATED_AFTER_LOCK);
    var summaries = new ArrayList<TrustMonthlyListService.MonthlyListSummary>();
    books.as(memberId, () -> summaries.addAll(monthlyListService.list(account)));
    assertThat(summaries)
        .singleElement()
        .satisfies(
            summary -> {
              assertThat(summary.id()).isEqualTo(listId);
              assertThat(summary.adjustmentCount()).as("the back-dated deposit").isEqualTo(1);
            });
    books.assertServicesAgree(account);
    books.assertServicesAgree(account, MARCH_END);
  }

  // --- path 24, lock part -----------------------------------------------------------------------

  /**
   * HARNESS path 24, lock part (§16.9.2): completing a reconciliation moves nothing (asserted
   * through {@code assertMoves}); to 15 March it locks no list, to 31 March it does.
   */
  @Test
  void path24_lock_aMonthEndLocks_aMidMonthDoesNot() {
    var midMonthAccount = books.freshAccount(false);
    var client = books.newClient("mid-month");
    var deposit = books.deposit(midMonthAccount, client, "800.00");
    var midMarch =
        importStatement(
            midMonthAccount,
            "tml-p24-mid.csv",
            "01/03/2026,Deposit received,800.00,800.00,TML-P24M");
    match(midMarch, 0, deposit);
    var midRecon = reconcile(midMonthAccount, LocalDate.of(2026, 3, 15), midMarch.id());
    var midCompleted = new TrustReconciliationResponse[1];
    books.as(
        memberId,
        () ->
            harness.assertMoves(
                midMonthAccount,
                Expected.none(),
                () ->
                    midCompleted[0] =
                        reconciliationService.completeReconciliation(
                            midRecon.id(), midRecon.bankStatementId())));
    assertThat(midCompleted[0].status()).isEqualTo("COMPLETED");
    assertThat(midCompleted[0].monthlyListId()).as("a mid-month end locks nothing").isNull();
    assertThat(
            books.read(
                () ->
                    monthlyListRepository.findByTrustAccountIdOrderByPeriodEndDesc(
                        midMonthAccount)))
        .isEmpty();

    var monthEndAccount = books.freshAccount(false);
    var march = matchedMarchStatement(monthEndAccount, "tml-p24-end.csv");
    var endRecon = reconcile(monthEndAccount, MARCH_END, march.id());
    var endCompleted = new TrustReconciliationResponse[1];
    books.as(
        memberId,
        () ->
            harness.assertMoves(
                monthEndAccount,
                Expected.none(),
                () ->
                    endCompleted[0] =
                        reconciliationService.completeReconciliation(
                            endRecon.id(), endRecon.bankStatementId())));
    var lists =
        books.read(
            () -> monthlyListRepository.findByTrustAccountIdOrderByPeriodEndDesc(monthEndAccount));
    assertThat(lists)
        .singleElement()
        .satisfies(
            l -> {
              assertThat(l.getId()).isEqualTo(endCompleted[0].monthlyListId());
              assertThat(l.getReconciliationId()).isEqualTo(endRecon.id());
            });
    assertThat(getReconciliation(endRecon.id()).monthlyListId())
        .isEqualTo(endCompleted[0].monthlyListId());
  }

  // --- endpoints --------------------------------------------------------------------------------

  /**
   * {@code GET /api/trust-accounts/{id}/monthly-lists} is a bare array, newest first; {@code GET
   * /api/trust-monthly-lists/{id}} returns the lines and the adjustments; an unknown list is a 404
   * ProblemDetail; a member without {@code VIEW_TRUST} is refused.
   */
  @Test
  void endpoints_requireViewTrust_listIsABareArrayNewestFirst_getReturnsAdjustments_unknownIs404()
      throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("endpoints");
    var february =
        books.postLedgerRowDirect(
            account,
            "DEPOSIT",
            "100.00",
            LocalDate.of(2026, 2, 10),
            client.id(),
            client.matter(),
            null,
            null);
    var febStatement =
        importStatement(
            account, "tml-ep-feb.csv", "10/02/2026,Deposit received,100.00,100.00,TML-EPF");
    match(febStatement, 0, february);
    var febList = complete(reconcile(account, FEBRUARY_END, febStatement.id())).monthlyListId();
    var march = books.deposit(account, client, "900.00");
    var marchStatement =
        importStatement(
            account, "tml-ep-march.csv", "01/03/2026,Deposit received,900.00,1000.00,TML-EPM");
    match(marchStatement, 0, march);
    var marchList = complete(reconcile(account, MARCH_END, marchStatement.id())).monthlyListId();

    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    mockMvc
        .perform(get("/api/trust-accounts/{id}/monthly-lists", account).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(marchList.toString()))
        .andExpect(jsonPath("$[0].periodEnd").value("2026-03-31"))
        .andExpect(jsonPath("$[0].creditorTotal").value(1000.0))
        .andExpect(jsonPath("$[0].lockedByName").value("TML Owner"))
        .andExpect(jsonPath("$[0].adjustmentCount").value(0))
        .andExpect(jsonPath("$[1].id").value(febList.toString()))
        .andExpect(jsonPath("$[1].periodEnd").value("2026-02-28"));
    mockMvc
        .perform(get("/api/trust-monthly-lists/{id}", marchList).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(marchList.toString()))
        .andExpect(jsonPath("$.trustAccountId").value(account.toString()))
        .andExpect(jsonPath("$.periodEnd").value("2026-03-31"))
        .andExpect(jsonPath("$.lines[0].kind").value("CLIENT"))
        .andExpect(jsonPath("$.lines[0].clientId").value(client.id().toString()))
        .andExpect(jsonPath("$.lines[0].balance").value(1000.0))
        .andExpect(jsonPath("$.lines[1].kind").value("FUND"))
        .andExpect(jsonPath("$.adjustmentsSinceLock.entries").isArray())
        .andExpect(jsonPath("$.adjustmentsSinceLock.entries.length()").value(0));

    var unknown = UUID.randomUUID();
    mockMvc
        .perform(get("/api/trust-monthly-lists/{id}", unknown).with(owner))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("TrustMonthlyList not found"))
        .andExpect(jsonPath("$.detail").value("No trustmonthlylist found with id " + unknown));
    var unknownAccount = UUID.randomUUID();
    mockMvc
        .perform(get("/api/trust-accounts/{id}/monthly-lists", unknownAccount).with(owner))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.title").value("TrustAccount not found"))
        .andExpect(jsonPath("$.detail").value("No trustaccount found with id " + unknownAccount));

    var noTrust = TestJwtFactory.memberJwt(ORG_ID, NO_TRUST);
    mockMvc
        .perform(get("/api/trust-accounts/{id}/monthly-lists", account).with(noTrust))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
    mockMvc
        .perform(get("/api/trust-monthly-lists/{id}", marchList).with(noTrust))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Access denied"))
        .andExpect(jsonPath("$.detail").value("Insufficient permissions for this operation"));
  }

  // --- lockedThrough on the account (650D) -----------------------------------------------------

  /**
   * 650D (§16.3.10, ADR-382): the trust account carries {@code lockedThrough}, the latest period
   * end of its locked lists, null until one is locked; a mid-month completion locks nothing; a
   * posting dated on or before it is still recorded (never a refusal) and never moves it.
   */
  @Test
  void lockedThrough_onTheAccount_isTheLatestLockedMonthEnd_andAPostingDatedBeforeItStillRecords()
      throws Exception {
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    var midMonth = books.freshAccount(false);
    var account = books.freshAccount(false);
    mockMvc
        .perform(get("/api/trust-accounts/{id}", account).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasKey("lockedThrough")))
        .andExpect(jsonPath("$.lockedThrough").value(nullValue()));

    var midStatement = matchedMarchStatement(midMonth, "tml-lt-mid.csv");
    complete(reconcile(midMonth, LocalDate.of(2026, 3, 15), midStatement.id()));
    mockMvc
        .perform(get("/api/trust-accounts/{id}", midMonth).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasKey("lockedThrough")))
        .andExpect(jsonPath("$.lockedThrough").value(nullValue()));

    var client = books.newClient("locked through");
    var february =
        books.postLedgerRowDirect(
            account,
            "DEPOSIT",
            "100.00",
            LocalDate.of(2026, 2, 10),
            client.id(),
            client.matter(),
            null,
            null);
    var febStatement =
        importStatement(
            account, "tml-lt-feb.csv", "10/02/2026,Deposit received,100.00,100.00,TML-LTF");
    match(febStatement, 0, february);
    complete(reconcile(account, FEBRUARY_END, febStatement.id()));
    var march = books.deposit(account, client, "900.00");
    var marchStatement =
        importStatement(
            account, "tml-lt-march.csv", "01/03/2026,Deposit received,900.00,1000.00,TML-LTM");
    match(marchStatement, 0, march);
    complete(reconcile(account, MARCH_END, marchStatement.id()));

    mockMvc
        .perform(get("/api/trust-accounts/{id}", account).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lockedThrough").value("2026-03-31"));
    mockMvc
        .perform(get("/api/trust-accounts").with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + account + "')].lockedThrough").value("2026-03-31"));

    // Never a refusal: a deposit dated 1 March (on or before the lock) is recorded
    var backdated = new UUID[1];
    books.as(memberId, () -> backdated[0] = marchDeposit(account, client, "TML-LT-BACK"));
    assertThat(books.statusOf(backdated[0])).isEqualTo("RECORDED");
    mockMvc
        .perform(get("/api/trust-accounts/{id}", account).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lockedThrough").value("2026-03-31"));
  }

  // --- the lock and a concurrent posting (650A review C1 / found.md F-1) ------------------------

  /**
   * A deposit dated into March is posting (its transaction open) when March's reconciliation
   * completes: the list's account lock waits for the deposit to commit — observed in {@code
   * pg_stat_activity}, not a sleep — and then reads books the reconciliation did not see, so the
   * completion is refused and rolled back. Without the lock the list would freeze without the
   * deposit and its {@code posted_at} (before {@code locked_at}) would keep it off the after-lock
   * schedule too.
   */
  @Test
  void lockRace_aPostingInFlight_theLockWaitsForIt_andRefusesBooksTheReconciliationDidNotSee()
      throws Exception {
    var account = books.freshAccount(false);
    var march = matchedMarchStatement(account, "tml-race-in-flight.csv");
    var recon = reconcile(account, MARCH_END, march.id());
    assertThat(recon.isBalanced()).isTrue();
    var client = books.newClient("race in flight");

    var outcome =
        race(
            () -> marchDeposit(account, client, "TML-RACE-1"),
            () ->
                reconciliationService.completeReconciliation(recon.id(), recon.bankStatementId()));

    assertThat(outcome.secondBlockedOnFirst())
        .as("the completion waited for the posting's account lock")
        .isTrue();
    assertThat(outcome.second())
        .isInstanceOfSatisfying(
            InvalidStateException.class,
            e -> {
              assertThat(e.getBody().getStatus()).isEqualTo(400);
              assertThat(e.getBody().getTitle()).isEqualTo("Trust books changed during completion");
              assertThat(e.getBody().getDetail())
                  .isEqualTo(
                      "An entry was posted while the reconciliation was being completed."
                          + " Recalculate the reconciliation and complete it again.");
            });
    assertThat(getReconciliation(recon.id()).status()).as("rolled back").isEqualTo("DRAFT");
    assertThat(
            books.read(
                () -> monthlyListRepository.findByTrustAccountIdOrderByPeriodEndDesc(account)))
        .isEmpty();
  }

  /**
   * The other order: the completion has locked March (its transaction open) when a deposit dated
   * into March posts. The deposit waits for the completion to commit, so its {@code posted_at} is
   * after {@code locked_at} and it is shown against the list.
   */
  @Test
  void lockRace_aPostingWhileTheListLocks_waits_andIsShownAgainstTheList() throws Exception {
    var account = books.freshAccount(false);
    var march = matchedMarchStatement(account, "tml-race-after.csv");
    var recon = reconcile(account, MARCH_END, march.id());
    var client = books.newClient("race after lock");
    var completed = new AtomicReference<TrustReconciliationResponse>();
    var deposit = new AtomicReference<UUID>();

    var outcome =
        race(
            () ->
                completed.set(
                    reconciliationService.completeReconciliation(
                        recon.id(), recon.bankStatementId())),
            () -> deposit.set(marchDeposit(account, client, "TML-RACE-2")));

    assertThat(outcome.second()).as("the deposit").isNull();
    assertThat(outcome.secondBlockedOnFirst())
        .as("the deposit waited for the list's account lock")
        .isTrue();
    var list = getList(completed.get().monthlyListId());
    assertThat(list.creditorTotal())
        .as("frozen without the deposit")
        .isEqualByComparingTo("1000.00");
    assertThat(list.adjustmentsSinceLock().entries())
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.transactionId()).isEqualTo(deposit.get());
              assertThat(e.postedAt()).isAfter(list.lockedAt());
            });
  }

  private UUID marchDeposit(UUID account, TrustBooksFixture.Client client, String reference) {
    return transactionService
        .recordDeposit(
            account,
            new RecordDepositRequest(
                client.id(),
                client.matter(),
                new BigDecimal("250.00"),
                reference,
                "Deposit",
                LocalDate.of(2026, 3, 1)))
        .id();
  }

  /** Each side's failure (null = succeeded) and whether {@code second} was seen blocked. */
  private record RaceOutcome(Throwable second, boolean secondBlockedOnFirst) {}

  /**
   * Runs {@code first} in a transaction held open after it returns, starts {@code second} on
   * another thread, waits (bounded) until {@code second} is blocked by {@code first}'s backend or
   * has finished, then commits {@code first}. {@code first} must succeed.
   */
  private RaceOutcome race(Runnable first, Runnable second) throws Exception {
    var firstDone = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);
    var firstError = new AtomicReference<Throwable>();
    var secondError = new AtomicReference<Throwable>();
    var secondFinished = new AtomicBoolean(false);
    var firstPid = new AtomicInteger();
    Thread firstThread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    books.as(
                        memberId,
                        () ->
                            transactionTemplate.executeWithoutResult(
                                tx -> {
                                  first.run();
                                  firstPid.set(
                                      ((Number)
                                              entityManager
                                                  .createNativeQuery("SELECT pg_backend_pid()")
                                                  .getSingleResult())
                                          .intValue());
                                  firstDone.countDown();
                                  await(releaseFirst);
                                }));
                  } catch (Throwable t) {
                    firstError.set(t);
                    firstDone.countDown();
                  }
                });
    assertThat(firstDone.await(30, TimeUnit.SECONDS)).isTrue();
    assertThat(firstError.get()).as("the first operation").isNull();

    Thread secondThread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    books.as(memberId, second);
                  } catch (Throwable t) {
                    secondError.set(t);
                  } finally {
                    secondFinished.set(true);
                  }
                });
    boolean blocked;
    try {
      blocked = awaitBlockedByOrFinished(firstPid.get(), secondFinished);
    } finally {
      releaseFirst.countDown();
      join(firstThread);
      join(secondThread);
    }
    assertThat(firstError.get()).as("the first operation's commit").isNull();
    return new RaceOutcome(secondError.get(), blocked);
  }

  /** True once a backend is blocked by {@code holderPid}; false if the waiter finished first. */
  private boolean awaitBlockedByOrFinished(int holderPid, AtomicBoolean waiterFinished) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long blocked =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))",
              Long.class,
              holderPid);
      if (blocked != null && blocked > 0) {
        return true;
      }
      if (waiterFinished.get()) {
        return false;
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
    }
    throw new AssertionError("the second operation neither blocked nor finished within 30 s");
  }

  private static void join(Thread thread) throws InterruptedException {
    thread.join(30_000);
    assertThat(thread.isAlive()).as("thread finished within 30 s").isFalse();
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // --- helpers ----------------------------------------------------------------------------------

  /** A March statement with one R1 000 deposit (1 March), matched. */
  private BankStatementResponse matchedMarchStatement(UUID account, String fileName) {
    return matchedMarchStatement(account, fileName, books.newClient("march"));
  }

  private BankStatementResponse matchedMarchStatement(
      UUID account, String fileName, TrustBooksFixture.Client client) {
    var deposit = books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            account,
            fileName,
            "01/03/2026,Deposit received,1000.00,1000.00,TML-M" + (++statementCounter));
    match(march, 0, deposit);
    return march;
  }

  private static RecordFromLineRequest unidentified() {
    return new RecordFromLineRequest(
        StatementLineClassification.UNIDENTIFIED_DEPOSIT, null, null, null, null, null, null, null);
  }

  private void recordLine(UUID lineId, RecordFromLineRequest request) {
    books.as(memberId, () -> statementLinePostingService.recordFromLine(lineId, request));
  }

  private MonthlyListResponse getList(UUID listId) {
    var list = new MonthlyListResponse[1];
    books.as(memberId, () -> list[0] = monthlyListService.get(listId));
    return list[0];
  }

  private TrustIntegrityService.TrustIntegrityReport check(UUID account, LocalDate asAt) {
    var report = new TrustIntegrityService.TrustIntegrityReport[1];
    books.as(memberId, () -> report[0] = integrityService.check(account, asAt, null));
    return report[0];
  }

  private static List<String> codes(TrustIntegrityService.TrustIntegrityReport report) {
    return report.flags().stream().map(IntegrityFlag::code).toList();
  }

  private BankStatementResponse importStatement(UUID account, String fileName, String... lines) {
    var csv =
        """
        FNB Trust Account Statement - Account 62400000000
        Date,Description,Amount,Balance,Reference
        %s
        """
            .formatted(String.join("\n", lines));
    var file =
        new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    var statement = new BankStatementResponse[1];
    books.as(
        memberId, () -> statement[0] = reconciliationService.importBankStatement(account, file));
    return statement[0];
  }

  private void match(BankStatementResponse statement, int index, UUID transactionId) {
    var lineId = statement.lines().get(index).id();
    books.as(memberId, () -> reconciliationService.manualMatch(lineId, transactionId));
  }

  private TrustReconciliationResponse reconcile(
      UUID account, LocalDate periodEnd, UUID statementId) {
    var recon = new TrustReconciliationResponse[1];
    books.as(
        memberId,
        () -> {
          var created = reconciliationService.createReconciliation(account, periodEnd, statementId);
          recon[0] =
              reconciliationService.calculateReconciliation(
                  created.id(), created.bankStatementId());
        });
    return recon[0];
  }

  private TrustReconciliationResponse complete(TrustReconciliationResponse draft) {
    var completed = new TrustReconciliationResponse[1];
    books.as(
        memberId,
        () ->
            completed[0] =
                reconciliationService.completeReconciliation(draft.id(), draft.bankStatementId()));
    return completed[0];
  }

  private TrustReconciliationResponse getReconciliation(UUID reconciliationId) {
    var recon = new TrustReconciliationResponse[1];
    books.as(memberId, () -> recon[0] = reconciliationService.getReconciliation(reconciliationId));
    return recon[0];
  }

  private String createCustomRole(JwtRequestPostProcessor owner, String name, String capability)
      throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/org-roles")
                    .with(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"%s","description":"Test role","capabilities":["%s"]}"""
                            .formatted(name, capability)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
  }

  private void assignRole(JwtRequestPostProcessor owner, String member, String roleId)
      throws Exception {
    mockMvc
        .perform(
            put("/api/members/" + member + "/role")
                .with(owner)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orgRoleId\":\"%s\",\"capabilityOverrides\":[]}".formatted(roleId)))
        .andExpect(status().isOk());
  }
}
