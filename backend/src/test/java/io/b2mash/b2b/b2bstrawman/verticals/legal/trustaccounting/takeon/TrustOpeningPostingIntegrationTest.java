package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.AllocateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedger;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustPostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordPaymentRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.ReverseRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 93 / 657A (tasks 657.4, 657.5, 657.9; ADR-386, architecture §17.3.9–§17.3.13): opening
 * entries of a trust take-on, posted through {@code TrustPostingService.recordOpening} by the
 * harness's take-on path on the §17.3.13 figures (C = the end of the month four months back, so the
 * dates float with today — D-63). Every group of the take-on balances at C (EQ-1..EQ-6); only the
 * two items outstanding at cutover are outstanding; a cleared row never meets a bank line; every
 * other posting path refuses an entry dated on or before C ({@code DATED_BEFORE_TAKE_ON}); the
 * unidentified-deposit resolution window starts the day after C; and an item outstanding at cutover
 * still auto-matches months later. The {@code recordOpening} guards themselves are unit tested in
 * {@code TrustPostingServiceRecordOpeningTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustOpeningPostingIntegrationTest {

  private static final String ORG_ID = "org_trust_opening_posting";
  private static final DateTimeFormatter X3_DATE =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustBooksService trustBooksService;
  @Autowired private TrustPostingService postingService;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private UnidentifiedDepositService unidentifiedDepositService;
  @Autowired private JdbcTemplate jdbc;

  private TrustBooksFixture.Tenant books;
  private UUID ownerId;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var tenantSchema =
        provisioningService.provisionTenant(ORG_ID, "Trust Opening Posting Org", null).schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_trust_opening_owner",
                "trust_opening@test.com",
                "Opening Owner",
                "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_trust_opening_approver",
                "trust_opening_approver@test.com",
                "Opening Approver",
                "owner"));
    fixture.enableTrustModule(tenantSchema);
    books = fixture.tenant(tenantSchema, ORG_ID, ownerId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  private LocalDate cutover() {
    return YearMonth.from(books.today()).minusMonths(4).atEndOfMonth();
  }

  // --- 1: record refuses every take-on type ----------------------------------------------------

  @Test
  void record_refusesEveryTakeOnType_andSavesNothing() {
    var account = books.freshAccount(false);
    var client = books.newClient("R1");
    var takeOnTypes =
        Arrays.stream(TrustTransactionType.values())
            .filter(TrustTransactionType::recordedOnlyAtTakeOn)
            .toList();
    assertThat(takeOnTypes).hasSize(11);
    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () -> {
                  for (var type : takeOnTypes) {
                    assertThatThrownBy(
                            () ->
                                books.read(
                                    () ->
                                        postingService.record(
                                            new TrustTransaction(
                                                account,
                                                type.name(),
                                                new BigDecimal("10.00"),
                                                client.id(),
                                                client.matter(),
                                                null,
                                                "TO-R1",
                                                "crafted",
                                                books.today(),
                                                null,
                                                ownerId))))
                        .as(type.name())
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage(
                            "record is not the posting path for "
                                + type
                                + " (a take-on type: recordOpening only)");
                  }
                }));
    assertThat(
            jdbc.queryForObject(
                ("SELECT count(*) FROM \"%s\".trust_transactions WHERE trust_account_id = ?")
                    .formatted(books.schema()),
                Integer.class,
                account))
        .isZero();
  }

  // --- 2 and 4: the take-on path ----------------------------------------------------------------

  @Test
  void recordOpening_postsApprovedWithTheSigner_clearedFromTheRegistry() {
    var takeOn = books.takeOnSection17313(cutover());

    var row = books.read(() -> transactionRepository.findById(takeOn.clientRowA()).orElseThrow());
    assertThat(row.getStatus()).isEqualTo("APPROVED");
    assertThat(row.getApprovedBy()).isEqualTo(ownerId);
    assertThat(row.getApprovedAt()).isEqualTo(takeOn.authority().signedAt());
    assertThat(row.getPostedAt()).isNotNull();
    assertThat(row.getTransactionDate()).isEqualTo(takeOn.cutover());
    assertThat(row.getTakeOnBatchId()).isEqualTo(takeOn.batch());
    assertThat(row.isClearedAtCutover()).isTrue();
    assertThat(row.getLedger()).isEqualTo(TrustLedger.CLIENT);
    assertThat(row.getCashbookEffect()).isEqualByComparingTo("300000.00");
    assertThat(row.getLedgerEffect()).isEqualByComparingTo("300000.00");

    var item = books.read(() -> transactionRepository.findById(takeOn.payUnpres()).orElseThrow());
    assertThat(item.isClearedAtCutover()).isFalse();
    assertThat(item.getStatus()).isEqualTo("APPROVED");
    assertThat(item.getReference()).isEqualTo("PAY-778");

    assertThat(books.clientLedger(takeOn.g(), takeOn.a())).isEqualByComparingTo("300000.00");
    assertThat(books.clientLedger(takeOn.g(), takeOn.b())).isEqualByComparingTo("150000.00");
    assertThat(books.clientLedger(takeOn.g(), takeOn.c())).isEqualByComparingTo("49500.00");
  }

  @Test
  void takeOnPath_balancesAtCutover_andOnlyTheTwoItemsAreOutstanding() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();

    var g = books.snapshotAsAt(takeOn.g(), c);
    assertThat(g.cashbook()).isEqualByComparingTo("403500.00");
    assertThat(g.clientLedgers()).isEqualByComparingTo("499500.00");
    assertThat(g.fundLedger()).isEqualByComparingTo("1250.00");
    assertThat(g.unidentified()).isEqualByComparingTo("2750.00");
    assertThat(g.interAccount()).isEqualByComparingTo("-100000.00");
    var i3 = books.snapshotAsAt(takeOn.i3(), c);
    assertThat(i3.cashbook()).isEqualByComparingTo("100000.00");
    assertThat(i3.interAccount()).isEqualByComparingTo("100000.00");
    var i4 = books.snapshotAsAt(takeOn.i4(), c);
    assertThat(i4.cashbook()).isEqualByComparingTo("200000.00");
    assertThat(i4.investmentCreditors()).isEqualByComparingTo("200000.00");

    // EQ-1: the bank at C holds every cleared opening entry; only the two items are outstanding
    var statement = books.statementDirect(takeOn.g(), c.withDayOfMonth(1), c, "0.00", "418500.00");
    var bank = new TrustBooksService.BankPosition[1];
    books.as(ownerId, () -> bank[0] = trustBooksService.bankPosition(takeOn.g(), statement, c));
    assertThat(bank[0].outstandingDeposits()).isEqualByComparingTo("5000.00");
    assertThat(bank[0].outstandingPayments()).isEqualByComparingTo("20000.00");
    assertThat(bank[0].adjustedBankBalance()).isEqualByComparingTo("403500.00");
    books.as(ownerId, () -> harness.assertBankAgrees(takeOn.g(), statement, c));

    // EQ-2..EQ-6 at C and today, and every service agrees with the oracle
    for (var account : new UUID[] {takeOn.g(), takeOn.i3(), takeOn.i4()}) {
      books.assertBalancedAsAt(account, c);
      books.assertServicesAgree(account, c);
      books.assertServicesAgree(account);
    }
  }

  // --- 5, 6, 7: a cleared row never meets a bank line; an item does ---------------------------

  @Test
  void linkBankLine_refusesAClearedRow_butUnlinkingIsAllowed() {
    var takeOn = books.takeOnSection17313(cutover());
    var line =
        books.unmatchedLine(takeOn.g(), takeOn.cutover().plusDays(1), "300000.00", null).lineId();

    books.as(
        ownerId,
        () ->
            assertThatThrownBy(
                    () ->
                        books.read(
                            () ->
                                postingService.linkBankLine(
                                    transactionRepository
                                        .findById(takeOn.clientRowA())
                                        .orElseThrow(),
                                    line)))
                .isInstanceOfSatisfying(
                    ResourceConflictException.class, e -> assertClearedAtCutover(e)));
    var unlinked =
        books.read(
            () ->
                postingService.linkBankLine(
                    transactionRepository.findById(takeOn.clientRowA()).orElseThrow(), null));
    assertThat(unlinked.getBankStatementLineId()).isNull();
  }

  @Test
  void manualMatch_refusesAClearedRow_andMatchesAnItemOutstandingAtCutover() {
    var takeOn = books.takeOnSection17313(cutover());
    var credit =
        books.unmatchedLine(takeOn.g(), takeOn.cutover().plusDays(2), "300000.00", null).lineId();

    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                takeOn.g(),
                Expected.none(),
                () ->
                    assertThatThrownBy(
                            () -> reconciliationService.manualMatch(credit, takeOn.clientRowA()))
                        .isInstanceOfSatisfying(
                            ResourceConflictException.class, e -> assertClearedAtCutover(e))));
    assertThat(lineStatus(credit)).isEqualTo("UNMATCHED");

    // the cheque unpresented at cutover is an ordinary row: it matches its debit line
    var debit =
        books
            .unmatchedLine(takeOn.g(), takeOn.cutover().plusDays(3), "-20000.00", "PAY-778")
            .lineId();
    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                takeOn.g(),
                Expected.none(),
                () -> reconciliationService.manualMatch(debit, takeOn.payUnpres())));
    assertThat(lineStatus(debit)).isEqualTo("MANUALLY_MATCHED");
    assertThat(lineOf(takeOn.payUnpres())).isEqualTo(debit);
  }

  // --- 8: DATED_BEFORE_TAKE_ON on the posting paths ---------------------------------------------

  @Test
  void aTakenOnAccount_refusesEntriesDatedOnOrBeforeTheCutover() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();
    var a = takeOn.a();

    // record (a deposit)
    assertDatedBeforeTakeOn(
        takeOn.g(),
        c,
        () ->
            transactionService.recordDeposit(
                takeOn.g(),
                new RecordDepositRequest(
                    a.id(), a.matter(), new BigDecimal("100.00"), "DEP-C", "Deposit", c)));
    books.deposit(takeOn.g(), a, "100.00", c.plusDays(1));

    // recordAwaitingApproval (a payment request)
    assertDatedBeforeTakeOn(
        takeOn.g(),
        c,
        () ->
            transactionService.recordPayment(
                takeOn.g(),
                new RecordPaymentRequest(
                    a.id(), a.matter(), new BigDecimal("100.00"), "PAY-C", "Payment", c)));
  }

  @Test
  void approvingAPaymentRequestedBeforeTheTakeOn_isRefusedUnderThePostingLock() {
    var c = cutover();
    var account = books.freshAccount(false);
    var client = books.newClient("T8");
    books.deposit(account, client, "1000.00", c.minusDays(5));
    var payment = books.requestPayment(account, client, "100.00", c);
    books.markTakenOn(account, c, books.takeOnBatch(account, c));

    assertDatedBeforeTakeOn(
        account, c, () -> transactionService.approveTransaction(payment, approverId), approverId);
    assertThat(books.statusOf(payment)).isEqualTo("AWAITING_APPROVAL");
  }

  // --- 9, 10: reversals
  // ---------------------------------------------------------------------------

  @Test
  void aReversalDatedOnOrBeforeTheCutover_isRefused_afterwardsTheItemReversesLikeAPayment() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();

    assertDatedBeforeTakeOn(
        takeOn.g(),
        c,
        () ->
            transactionService.reverseTransaction(
                takeOn.payUnpres(), new ReverseRequest("cheque cancelled", c)));
    assertThat(books.statusOf(takeOn.payUnpres())).isEqualTo("APPROVED");

    // the cancelled cheque comes back to the client the day after the cutover
    books.reverse(
        takeOn.g(), takeOn.payUnpres(), Expected.cashbookAndClient("+20000.00"), c.plusDays(1));
    assertThat(books.clientLedger(takeOn.g(), takeOn.a())).isEqualByComparingTo("320000.00");
    var bank = new TrustBooksService.BankPosition[1];
    var statement =
        books.statementDirect(takeOn.g(), c.plusDays(1), c.plusDays(1), "418500.00", "418500.00");
    books.as(
        ownerId,
        () -> bank[0] = trustBooksService.bankPosition(takeOn.g(), statement, c.plusDays(1)));
    assertThat(bank[0].outstandingPayments()).isEqualByComparingTo("0.00");
    assertThat(bank[0].outstandingDeposits()).isEqualByComparingTo("5000.00");
    books.as(ownerId, () -> harness.assertBankAgrees(takeOn.g(), statement, c.plusDays(1)));

    // the deposit not yet cleared reverses like a DEPOSIT (needs approval) — never dated <= C
    assertDatedBeforeTakeOn(
        takeOn.g(),
        c,
        () ->
            transactionService.reverseTransaction(
                takeOn.depTransit(), new ReverseRequest("deposit bounced", c)));
    var pending = books.reverse(takeOn.g(), takeOn.depTransit(), Expected.none(), c.plusDays(1));
    assertThat(pending.status()).isEqualTo("AWAITING_APPROVAL");
  }

  @Test
  void aRowClearedAtTheCutover_isNeverReversed() {
    var takeOn = books.takeOnSection17313(cutover());
    books.assertReversalRefused(
        takeOn.g(), takeOn.clientRowA(), "Transaction type TAKEON_CLIENT cannot be reversed");
    books.assertReversalRefused(
        takeOn.g(), takeOn.payOffset(), "Transaction type TAKEON_PAY_OFFSET cannot be reversed");
  }

  // --- 11: the resolution window of an unidentified deposit starts after the cutover -----------

  @Test
  void allocatingATakenOnItem_isDatedAfterTheCutover() {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();
    var item = takeOn.unidentifiedRecent().itemId();
    var b = takeOn.b();

    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                takeOn.g(),
                Expected.none(),
                () ->
                    assertThatThrownBy(
                            () ->
                                unidentifiedDepositService.allocate(
                                    item, new AllocateRequest(b.id(), b.matter(), c, null)))
                        .isInstanceOfSatisfying(
                            InvalidStateException.class,
                            e -> {
                              assertThat(e.getBody().getStatus()).isEqualTo(400);
                              assertThat(e.getBody().getTitle())
                                  .isEqualTo("Cannot allocate deposit");
                              assertThat(e.getBody().getDetail())
                                  .isEqualTo(
                                      "The allocation date must be after the take-on date ("
                                          + X3_DATE.format(c)
                                          + ") and no later than today");
                            })));

    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                takeOn.g(),
                Expected.none(),
                () ->
                    unidentifiedDepositService.allocate(
                        item, new AllocateRequest(b.id(), b.matter(), c.plusDays(1), null))));
  }

  // --- 12: an item outstanding at cutover joins the auto-match pool whatever its date ---------

  @Test
  void anItemOutstandingAtCutover_autoMatchesMonthsLater_aClearedRowNever() {
    var takeOn = books.takeOnSection17313(cutover());
    var later = takeOn.cutover().plusMonths(3);
    // C is far outside this statement's ±7-day window
    var statement = books.unmatchedLine(takeOn.g(), later, "-20000.00", "PAY-778");
    // a credit of exactly a cleared row's amount and sign (TAKEON_CLIENT, R300 000): never picked
    var credit = books.addLine(statement.statementId(), 2, later, "300000.00", null);

    var result = new TrustReconciliationService.MatchResult[1];
    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                takeOn.g(),
                Expected.none(),
                () ->
                    result[0] = reconciliationService.autoMatchStatement(statement.statementId())));

    assertThat(result[0].autoMatched()).isEqualTo(1);
    assertThat(lineOf(takeOn.payUnpres())).isEqualTo(statement.lineId());
    assertThat(lineStatus(statement.lineId())).isEqualTo("AUTO_MATCHED");
    assertThat(lineStatus(credit)).isEqualTo("UNMATCHED");
    assertThat(lineOf(takeOn.clientRowA())).isNull();
  }

  // --- helpers
  // ------------------------------------------------------------------------------------

  private void assertDatedBeforeTakeOn(UUID account, LocalDate cutover, ThrowingCallable call) {
    assertDatedBeforeTakeOn(account, cutover, call, ownerId);
  }

  /** The call is refused 409 DATED_BEFORE_TAKE_ON with the X3 text, and nothing moves. */
  private void assertDatedBeforeTakeOn(
      UUID account, LocalDate cutover, ThrowingCallable call, UUID actor) {
    books.as(
        actor,
        () ->
            harness.assertMoves(
                account,
                Expected.none(),
                () ->
                    assertThatThrownBy(call)
                        .isInstanceOfSatisfying(
                            ResourceConflictException.class,
                            e -> {
                              assertThat(e.getBody().getStatus()).isEqualTo(409);
                              assertThat(e.getBody().getTitle())
                                  .isEqualTo("Entry dated before the take-on");
                              assertThat(e.getBody().getDetail())
                                  .isEqualTo(
                                      "This account was taken on at "
                                          + X3_DATE.format(cutover)
                                          + ". Entries on or before that date belong in the"
                                          + " previous system's books.");
                              assertThat(e.getBody().getProperties())
                                  .containsEntry("code", TrustPostingService.DATED_BEFORE_TAKE_ON);
                            })));
  }

  private static void assertClearedAtCutover(ResourceConflictException e) {
    assertThat(e.getBody().getStatus()).isEqualTo(409);
    assertThat(e.getBody().getTitle()).isEqualTo("Cleared at the cutover date");
    assertThat(e.getBody().getDetail())
        .isEqualTo(
            "This entry is part of the take-on position, which was cleared at the cutover date."
                + " It cannot be matched to a bank statement line.");
    assertThat(e.getBody().getProperties())
        .containsEntry("code", TrustPostingService.CLEARED_AT_CUTOVER);
  }

  private String lineStatus(UUID lineId) {
    return jdbc.queryForObject(
        "SELECT match_status FROM \"%s\".bank_statement_lines WHERE id = ?"
            .formatted(books.schema()),
        String.class,
        lineId);
  }

  private UUID lineOf(UUID transactionId) {
    return books.read(
        () -> transactionRepository.findById(transactionId).orElseThrow().getBankStatementLineId());
  }
}
