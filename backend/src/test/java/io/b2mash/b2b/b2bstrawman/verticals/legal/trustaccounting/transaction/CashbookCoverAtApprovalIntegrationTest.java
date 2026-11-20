package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityFlag;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService.TrustIntegrityReport;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.AllocateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.PayToFundRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.UnidentifiedDepositResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentService.PlaceInvestmentRequest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import javax.sql.DataSource;
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
 * FU-9 (Phase 92 F-6 / F-6b; founder-signed legal ruling {@code
 * qa_cycle/evidence/phase92/legal/F-6-opinion.md}): a firm-initiated outflow from a trust banking
 * account — a payment, refund, fee transfer or section 86(4) / 86(3) placement — is refused at its
 * final approval unless the account's derived cashbook covers it, under the account's lock; the
 * refusal tells the firm to withdraw from the section 86(3) investment first. A bank-confirmed
 * entry (here a dishonoured deposit's reversal) is never refused: it still posts, and the integrity
 * check raises {@code CASHBOOK_IN_DEBIT} (a warning) when it takes a trust banking account's
 * cashbook below zero.
 *
 * <p>Each case drains the cashbook with an approved section 86(3) placement, which lowers the
 * cashbook and leaves every client ledger as it was, so the client still covers the outflow and
 * only the cashbook check can refuse it. Every refusal moves nothing ({@code assertMoves}); the
 * class ends {@code assertFirmBalanced}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CashbookCoverAtApprovalIntegrationTest {

  private static final String ORG_ID = "org_trust_cashbook_cover_test";
  private static final String OWNER = "user_cashbook_cover_owner";
  private static final String APPROVER = "user_cashbook_cover_approver";
  private static final String TITLE = "Insufficient trust balance";
  private static final String WITHDRAW_FIRST =
      " If trust money has been placed in a section 86(3) investment, withdraw it from the"
          + " investment first.";
  private static final Locale ZAR = VariableFormatter.resolveLocale("ZAR");
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustIntegrityService integrityService;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private UnidentifiedDepositService unidentifiedDepositService;
  @Autowired private TrustInvestmentService investmentService;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DataSource dataSource;

  private TrustBooksFixture.Tenant books;
  private UUID ownerId;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService.provisionTenant(ORG_ID, "Trust Cashbook Cover Org", null).schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "cashbook_owner@test.com", "Cashbook Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                APPROVER,
                "cashbook_approver@test.com",
                "Cashbook Approver",
                "admin"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, ownerId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  /**
   * (FU-9 ruling 1) A refund the client's ledger covers is refused at approval once a section 86(3)
   * placement has left the cashbook short — it stays AWAITING_APPROVAL and nothing moves. After the
   * section 86(3) investment is withdrawn (the refusal's remedy) the same approval posts.
   */
  @Test
  void refund_isRefusedAtApproval_whenTheCashbookCannotCover_andPostsAfterTheWithdrawal() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("FU-9 refund");
    books.deposit(funding, client, "5000.00");
    var firm = drain(funding, firmAccount, "4500.00");
    assertThat(books.clientLedger(funding, client)).isEqualByComparingTo("5000.00");

    var refund = books.requestRefund(funding, client, "2000.00");
    books.assertApprovalRefused(funding, refund, approverId, TITLE, refused("refund", "2000.00"));
    assertThat(books.statusOf(refund)).isEqualTo("AWAITING_APPROVAL");

    books.withdraw(
        funding,
        Expected.cashbookAndInterAccount("4500.00"),
        firmAccount,
        Expected.cashbookAndInterAccount("-4500.00"),
        firm,
        books.today());
    books.approve(funding, refund, approverId, Expected.cashbookAndClient("-2000.00"));
    assertThat(books.snapshotAsAt(funding, books.today()).cashbook())
        .isEqualByComparingTo("3000.00");
    books.assertBalancedAsAt(funding, books.today());
  }

  /** (FU-9 ruling 1) A fee transfer the client covers is refused when the cashbook cannot. */
  @Test
  void feeTransfer_isRefusedAtApproval_whenTheCashbookCannotCover() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("FU-9 fee transfer");
    books.deposit(funding, client, "5000.00");
    drain(funding, firmAccount, "4500.00");

    var feeTransfer = books.requestFeeTransfer(funding, client);
    books.assertApprovalRefused(
        funding,
        feeTransfer.transactionId(),
        approverId,
        TITLE,
        refused("fee transfer", "1150.00"));
    assertThat(books.statusOf(feeTransfer.transactionId())).isEqualTo("AWAITING_APPROVAL");
  }

  /**
   * (FU-9 ruling 1) A section 86(4) placement the client's matter covers is refused at approval
   * when the cashbook cannot — nothing moves on either account.
   */
  @Test
  void section86_4Placement_isRefusedAtApproval_whenTheCashbookCannotCover() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var clientAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("FU-9 s86(4)");
    books.deposit(funding, client, "5000.00");
    drain(funding, firmAccount, "4500.00");

    var placement = books.requestPlacement(funding, clientAccount, client, "1000.00");
    books.assertApprovalRefused(
        funding,
        placement.depositTransactionId(),
        approverId,
        TITLE,
        refused("section 86(4) placement", "1000.00"));
    assertThat(books.statusOf(placement.depositTransactionId())).isEqualTo("AWAITING_APPROVAL");
    assertThat(books.clientInvestmentLedger(clientAccount, client)).isEqualByComparingTo("0");
  }

  /**
   * FU-9 R-3: an unidentified deposit's payment to the Fund is a firm-initiated outflow too. With
   * the cashbook emptied by a section 86(3) placement, its approval is refused (the item's own
   * ledger still holds the R500) and nothing moves.
   */
  @Test
  void paymentToTheFund_isRefusedAtApproval_whenTheCashbookCannotCover() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("FU-9 Fund payment");
    books.deposit(funding, client, "1000.00");
    var itemId = unidentifiedItem(funding, "500.00");
    drain(funding, firmAccount, "1500.00");

    var requested = new UnidentifiedDepositResponse[1];
    books.as(
        ownerId,
        () ->
            requested[0] =
                unidentifiedDepositService.payToFund(
                    itemId, new PayToFundRequest("FU9-FUND-1", books.today(), null)));
    var payment = requested[0].resolutionTransactionId();
    books.assertApprovalRefused(
        funding,
        payment,
        approverId,
        TITLE,
        "The trust account's cashbook does not cover this payment to the Fund. Available: "
            + rand("0.00")
            + ", Requested: "
            + rand("500.00")
            + "."
            + WITHDRAW_FIRST);
    assertThat(books.statusOf(payment)).isEqualTo("AWAITING_APPROVAL");
  }

  /**
   * FU-9 R-2: the cover is the lowest the cashbook stands from the outflow's date on, not its
   * figure on that date alone. Two clients' R100 each, R100 of it placed in a section 86(3)
   * investment: the cashbook is R100. A R100 payment dated a week ahead approves (the cashbook
   * stands at R100 then). A R100 payment dated today for the other client must now be refused — the
   * cashbook is R100 today but R0 from next week, so posting it would leave the account R100 in
   * debit from then on.
   */
  @Test
  void paymentDatedToday_isRefused_whenAnAlreadyApprovedLaterPaymentLeavesTheCashbookShort() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var later = books.newClient("FU-9 later");
    var today = books.newClient("FU-9 today");
    books.deposit(funding, later, "100.00");
    books.deposit(funding, today, "100.00");
    drain(funding, firmAccount, "100.00");

    var nextWeek = books.today().plusDays(7);
    var first = books.requestPayment(funding, later, "100.00", nextWeek);
    books.as(approverId, () -> transactionService.approveTransaction(first, approverId));
    assertThat(books.statusOf(first)).isEqualTo("APPROVED");
    assertThat(books.snapshotAsAt(funding, nextWeek).cashbook()).isEqualByComparingTo("0");

    var second = books.requestPayment(funding, today, "100.00", books.today());
    books.assertApprovalRefused(
        funding,
        second,
        approverId,
        TITLE,
        "The trust account's cashbook does not cover this payment. Available: "
            + rand("0.00")
            + ", Requested: "
            + rand("100.00")
            + "."
            + WITHDRAW_FIRST);
    assertThat(books.statusOf(second)).isEqualTo("AWAITING_APPROVAL");
    assertThat(books.snapshotAsAt(funding, nextWeek).cashbook()).isEqualByComparingTo("0");

    // The same rule for a section 86(3) placement (its request and approval share one check)
    books.as(
        ownerId,
        () ->
            assertThatThrownBy(
                    () ->
                        investmentService.placeInvestment(
                            funding,
                            new PlaceInvestmentRequest(
                                firmAccount,
                                null,
                                null,
                                "Trust Books Bank",
                                "FU9-S863-LATER",
                                new BigDecimal("50.00"),
                                new BigDecimal("0.0500"),
                                books.today(),
                                null,
                                null,
                                InvestmentBasis.FIRM_DISCRETION)))
                .isInstanceOfSatisfying(
                    InvalidStateException.class,
                    e ->
                        assertThat(e.getBody().getDetail())
                            .isEqualTo(
                                "The trust account's cashbook does not cover this section 86(3)"
                                    + " placement. Available: "
                                    + rand("0.00")
                                    + ", Requested: "
                                    + rand("50.00"))));
  }

  /** FU-9 R-2, the reverse order: today's payment first, then the later one is refused. */
  @Test
  void paymentDatedLater_isRefused_whenAnAlreadyApprovedPaymentTodayLeavesTheCashbookShort() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var later = books.newClient("FU-9 later rev");
    var today = books.newClient("FU-9 today rev");
    books.deposit(funding, later, "100.00");
    books.deposit(funding, today, "100.00");
    drain(funding, firmAccount, "100.00");

    var first = books.requestPayment(funding, today, "100.00", books.today());
    books.approve(funding, first, approverId, Expected.cashbookAndClient("-100.00"));

    var nextWeek = books.today().plusDays(7);
    var second = books.requestPayment(funding, later, "100.00", nextWeek);
    books.assertApprovalRefused(
        funding,
        second,
        approverId,
        TITLE,
        "The trust account's cashbook does not cover this payment. Available: "
            + rand("0.00")
            + ", Requested: "
            + rand("100.00")
            + "."
            + WITHDRAW_FIRST);
    assertThat(books.statusOf(second)).isEqualTo("AWAITING_APPROVAL");
  }

  /**
   * FU-9 R-1 (lock order): a payment's approval for a client and the approval of an unidentified
   * deposit's allocation to the same client on the same account. The allocation's approval must
   * take the item and the client's anchor before any account lock (global order, 650A F-11): the
   * gate holds the item, so the allocation blocks there holding nothing else; the payment takes the
   * anchor and the account and posts. Released, the allocation completes. Had the allocation taken
   * the account's posting lock before the item and the anchor, it would hold the account while
   * waiting for the anchor the payment holds, and the payment would wait for the account — Postgres
   * aborts one with 40P01.
   */
  @Test
  void lockOrder_paymentApprovalAndAnAllocationApprovalForTheSameClient_bothComplete_noDeadlock()
      throws Exception {
    var account = books.freshAccount(false);
    var client = books.newClient("FU-9 lock order");
    books.deposit(account, client, "1000.00");
    var itemId = unidentifiedItem(account, "300.00");
    var allocation = new UnidentifiedDepositResponse[1];
    books.as(
        ownerId,
        () ->
            allocation[0] =
                unidentifiedDepositService.allocate(
                    itemId, new AllocateRequest(client.id(), client.matter(), null, null)));
    var allocationId = allocation[0].resolutionTransactionId();
    var payment = books.requestPayment(account, client, "200.00");

    var outcome =
        gatedOnRow(
            "unidentified_deposits",
            itemId,
            () -> transactionService.approveTransaction(allocationId, approverId),
            () -> transactionService.approveTransaction(payment, approverId));

    assertThat(outcome.first()).as("the allocation approval").isNull();
    assertThat(outcome.second()).as("the payment approval").isNull();
    assertThat(books.statusOf(allocationId)).isEqualTo("APPROVED");
    assertThat(books.statusOf(payment)).isEqualTo("APPROVED");
    assertThat(books.clientLedger(account, client)).isEqualByComparingTo("1100.00");
    books.assertServicesAgree(account);
  }

  /**
   * (FU-9 ruling 1, the boundary) A payment the cashbook covers exactly still approves, and the
   * cashbook ends at zero — not in debit, so no {@code CASHBOOK_IN_DEBIT}.
   */
  @Test
  void paymentTheCashbookCoversExactly_stillApproves_andRaisesNoCashbookFlag() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("FU-9 covered");
    books.deposit(funding, client, "5000.00");
    drain(funding, firmAccount, "3000.00");

    var payment = books.requestPayment(funding, client, "2000.00");
    books.approve(funding, payment, approverId, Expected.cashbookAndClient("-2000.00"));

    assertThat(books.statusOf(payment)).isEqualTo("APPROVED");
    assertThat(books.snapshotAsAt(funding, books.today()).cashbook()).isEqualByComparingTo("0");
    assertThat(codes(check(funding))).doesNotContain(TrustIntegrityService.CASHBOOK_IN_DEBIT);
    books.assertServicesAgree(funding);
  }

  /**
   * (FU-9 rulings 2 and 3) A bank-confirmed entry is never refused: a deposit dishonoured after a
   * section 86(3) placement emptied the cashbook still reverses (the contra posts at approval, the
   * client still covers it), taking the cashbook R5 000 into debit — and the integrity check now
   * raises {@code CASHBOOK_IN_DEBIT}, a warning naming the account, prompting the Rule 54.14.8
   * check and the Rule 54.14.10 report. The books still balance (EQ-2 holds through the
   * inter-account term), so the report stays {@code balanced}.
   */
  @Test
  void dishonouredDeposit_afterASection86_3Placement_isStillRecorded_andRaisesCashbookInDebit() {
    var funding = books.freshAccount(false);
    var firmAccount = books.freshInvestmentAccount(InvestmentBasis.FIRM_DISCRETION);
    var client = books.newClient("FU-9 dishonoured");
    var deposit = books.deposit(funding, client, "5000.00");
    drain(funding, firmAccount, "5000.00");
    assertThat(codes(check(funding))).doesNotContain(TrustIntegrityService.CASHBOOK_IN_DEBIT);

    var contra = books.reverse(funding, deposit, Expected.none());
    books.approve(funding, contra.id(), approverId, Expected.cashbookAndClient("-5000.00"));

    assertThat(books.statusOf(contra.id())).isEqualTo("APPROVED");
    assertThat(books.statusOf(deposit)).isEqualTo("REVERSED");
    var today = books.today();
    assertThat(books.snapshotAsAt(funding, today).cashbook()).isEqualByComparingTo("-5000.00");
    var report = check(funding);
    assertThat(report.balanced()).as("a warning never unbalances the books").isTrue();
    var flag = only(report, TrustIntegrityService.CASHBOOK_IN_DEBIT);
    assertThat(flag.severity()).isEqualTo(TrustIntegrityService.WARNING);
    assertThat(flag.amount()).isEqualByComparingTo("5000.00");
    assertThat(flag.message())
        .isEqualTo(
            "The trust banking account's cashbook is "
                + rand("5000.00")
                + " in debit as at "
                + today.format(DATE)
                + ". Check that the trust money in the banking account, the trust investment"
                + " accounts and trust cash still covers the balances of all trust creditors (Rule"
                + " 54.14.8); if it does not, report to the Legal Practice Council immediately"
                + " (Rule 54.14.10).");
    assertThat(flag.subjects())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.kind()).isEqualTo(TrustIntegrityService.SUBJECT_TRUST_ACCOUNT);
              assertThat(s.id()).isEqualTo(funding);
              assertThat(s.name()).startsWith("Trust Books ");
              assertThat(s.amount()).isEqualByComparingTo("5000.00");
            });
    // As at the day before the reversal posted the cashbook was R0 — not in debit
    assertThat(codes(check(funding, today.minusDays(1))))
        .doesNotContain(TrustIntegrityService.CASHBOOK_IN_DEBIT);
    // The investment account's books are not a trust banking account's cashbook
    assertThat(codes(check(firmAccount))).doesNotContain(TrustIntegrityService.CASHBOOK_IN_DEBIT);
    books.assertServicesAgree(funding);
    books.assertBalancedAsAt(firmAccount, today);
  }

  /**
   * FU-9 R-1, the FU-11 (650A F-12) variant: an allocation's approval and a section 86(4) placement
   * request for the same client (anchor, then the funding account {@code FOR UPDATE}). Same gate on
   * the item; both complete.
   */
  @Test
  void
      lockOrder_allocationApprovalAndASection86_4PlacementForTheSameClient_bothComplete_noDeadlock()
          throws Exception {
    var account = books.freshAccount(false);
    var clientAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("FU-11 lock order");
    books.deposit(account, client, "1000.00");
    var itemId = unidentifiedItem(account, "300.00");
    var allocation = new UnidentifiedDepositResponse[1];
    books.as(
        ownerId,
        () ->
            allocation[0] =
                unidentifiedDepositService.allocate(
                    itemId, new AllocateRequest(client.id(), client.matter(), null, null)));
    var allocationId = allocation[0].resolutionTransactionId();

    var outcome =
        gatedOnRow(
            "unidentified_deposits",
            itemId,
            () -> transactionService.approveTransaction(allocationId, approverId),
            () ->
                investmentService.placeInvestment(
                    account,
                    new PlaceInvestmentRequest(
                        clientAccount,
                        client.id(),
                        client.matter(),
                        "Trust Books Bank",
                        "FU11-LO-1",
                        new BigDecimal("400.00"),
                        new BigDecimal("0.0500"),
                        books.today(),
                        null,
                        null,
                        InvestmentBasis.CLIENT_INSTRUCTION)));

    assertThat(outcome.first()).as("the allocation approval").isNull();
    assertThat(outcome.second()).as("the placement request").isNull();
    assertThat(books.statusOf(allocationId)).isEqualTo("APPROVED");
    assertThat(books.clientLedger(account, client))
        .as("the allocation posted; the placement awaits approval")
        .isEqualByComparingTo("1300.00");
    books.assertServicesAgree(account);
  }

  // --- helpers -----------------------------------------------------------------------------------

  /** A HELD unidentified deposit of {@code amount} on {@code account}, received yesterday. */
  private UUID unidentifiedItem(UUID account, String amount) {
    var rowId = books.unidentifiedDepositDirect(account, amount, books.today().minusDays(1));
    return jdbc.queryForObject(
        "SELECT id FROM \"%s\".unidentified_deposits WHERE deposit_transaction_id = ?"
            .formatted(books.schema()),
        UUID.class,
        rowId);
  }

  /** Each side's failure (null = succeeded). */
  private record Gated(Throwable first, Throwable second) {}

  /**
   * Holds {@code table}'s row {@code id} {@code FOR NO KEY UPDATE} on a gate connection outside the
   * pool, starts {@code first} (as the approver) and waits until it is blocked by the gate, starts
   * {@code second} and waits (bounded) until it is blocked or has finished — observed in {@code
   * pg_stat_activity}, not a sleep — then releases the gate and joins both. A lock order that let
   * {@code first} hold a lock {@code second} needs while waiting for one {@code second} holds
   * deadlocks here (Postgres aborts one with 40P01).
   */
  private Gated gatedOnRow(String table, UUID id, Runnable first, Runnable second)
      throws Exception {
    var firstError = new AtomicReference<Throwable>();
    var secondError = new AtomicReference<Throwable>();
    var secondFinished = new AtomicBoolean(false);
    Thread firstThread = null;
    Thread secondThread = null;
    var pool = dataSource.unwrap(HikariDataSource.class);
    try (Connection gate =
        DriverManager.getConnection(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword())) {
      gate.setAutoCommit(false);
      int gatePid;
      try (var st = gate.createStatement();
          var rs = st.executeQuery("SELECT pg_backend_pid()")) {
        rs.next();
        gatePid = rs.getInt(1);
      }
      try (var ps =
          gate.prepareStatement(
              "SELECT id FROM \"%s\".%s WHERE id = ? FOR NO KEY UPDATE"
                  .formatted(books.schema(), table))) {
        ps.setObject(1, id);
        ps.executeQuery().close();
      }
      try {
        firstThread = Thread.ofPlatform().start(() -> capture(first, firstError, null));
        assertThat(awaitBlocked(gatePid, null)).as("the first blocked on the gate").isTrue();
        secondThread =
            Thread.ofPlatform().start(() -> capture(second, secondError, secondFinished));
        awaitBlocked(null, secondFinished);
      } finally {
        gate.rollback();
      }
    } finally {
      if (firstThread != null) {
        firstThread.join(TimeUnit.SECONDS.toMillis(60));
      }
      if (secondThread != null) {
        secondThread.join(TimeUnit.SECONDS.toMillis(60));
      }
    }
    return new Gated(firstError.get(), secondError.get());
  }

  private void capture(Runnable action, AtomicReference<Throwable> error, AtomicBoolean done) {
    try {
      books.as(approverId, action);
    } catch (Throwable t) {
      error.set(t);
    } finally {
      if (done != null) {
        done.set(true);
      }
    }
  }

  /**
   * Waits (bounded, 30 s) until a backend is blocked — by {@code holderPid} when given, else a
   * second one in all — (true), or {@code finished} is set (false).
   */
  private boolean awaitBlocked(Integer holderPid, AtomicBoolean finished) {
    long expected = holderPid != null ? 1 : 2;
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE cardinality(pg_blocking_pids(pid)) > 0"
                  + " AND (CAST(? AS int) IS NULL OR CAST(? AS int) = ANY(pg_blocking_pids(pid)))",
              Long.class,
              holderPid,
              holderPid);
      if (waiting != null && waiting >= expected) {
        return true;
      }
      if (finished != null && finished.get()) {
        return false;
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10)); // poll interval
    }
    throw new AssertionError("operation not blocked on a lock within 30 s");
  }

  /** An approved section 86(3) placement of {@code amount} from {@code funding}; its id. */
  private UUID drain(UUID funding, UUID firmAccount, String amount) {
    var placed = books.requestPlacement(funding, firmAccount, null, amount);
    books.approvePlacement(
        funding,
        Expected.cashbookAndInterAccount("-" + amount),
        firmAccount,
        Expected.cashbookAndInterAccount(amount),
        placed.depositTransactionId(),
        approverId);
    return placed.id();
  }

  /** The refusal for a {@code noun} of {@code requested} against R500 left in the cashbook. */
  private static String refused(String noun, String requested) {
    return "The trust account's cashbook does not cover this "
        + noun
        + ". Available: "
        + rand("500.00")
        + ", Requested: "
        + rand(requested)
        + "."
        + WITHDRAW_FIRST;
  }

  private TrustIntegrityReport check(UUID account) {
    return check(account, null);
  }

  private TrustIntegrityReport check(UUID account, LocalDate asAt) {
    var report = new TrustIntegrityReport[1];
    books.as(approverId, () -> report[0] = integrityService.check(account, asAt, null));
    return report[0];
  }

  private static java.util.List<String> codes(TrustIntegrityReport report) {
    return report.flags().stream().map(IntegrityFlag::code).toList();
  }

  private static IntegrityFlag only(TrustIntegrityReport report, String code) {
    var matching = report.flags().stream().filter(f -> f.code().equals(code)).toList();
    assertThat(matching).as("flag %s in %s", code, codes(report)).hasSize(1);
    return matching.getFirst();
  }

  private static String rand(String amount) {
    return VariableFormatter.formatCurrencyText(new BigDecimal(amount), ZAR);
  }
}
