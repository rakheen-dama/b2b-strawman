package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 92 slices 649A / 649B (tasks 649.3, 649.7; architecture §16.3.11, §16.9.2 paths 6–10; R8):
 * the four reversal hypotheses H-a..H-d against the {@link TrustBooksHarness} oracle, and the
 * contra reversal model. 649A reproduced all four red on {@code main} and pinned them ({@code
 * qa_cycle/evidence/phase92/649A/RED-RUNS.md}); H-b was closed by 646A (structural — derived
 * balances; its pin is now a green regression); 649B flipped H-a, H-c and H-d to the intended
 * behaviour after running them red on {@code main} ({@code
 * qa_cycle/evidence/phase92/649B/red-run.txt}).
 *
 * <p>Since 649B a reversal is a contra entry (ADR-378): the original stays posted with its effects
 * and is marked {@code REVERSED}; the {@code REVERSAL} row carries the negated effects on its own
 * date ({@code reversalDate}, default today), so every earlier as-at balance is unchanged.
 *
 * <p>One tenant; every test uses a fresh account; the class ends with {@code
 * assertFirmBalanced(today)}. Setup and posting steps come from the shared {@link
 * TrustBooksFixture}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustReversalEquationIntegrationTest {

  private static final String ORG_ID = "org_trust_reversal_equation";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustInvestmentRepository investmentRepository;
  @Autowired private TrustTransactionRepository transactionRepository;

  /** The tenant: H-a, H-b, H-c, H-d; ends firm-balanced. */
  private TrustBooksFixture.Tenant books;

  private UUID memberId;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Reversal Equation Org", null)
            .schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_rev_eq_owner", "rev_eq@test.com", "Rev Eq Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_rev_eq_approver",
                "rev_eq_approver@test.com",
                "Rev Eq Approver",
                "owner"));
    fixture.enableTrustModule(tenantSchema);
    books = fixture.tenant(tenantSchema, ORG_ID, memberId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  // --- H-a ------------------------------------------------------------------------------------

  /**
   * H-a — reproduced red on {@code main} {@code aecf93279} (649A) and pinned there; fixed in
   * <b>649B</b> (contra reversal model). Approving a credit reversal posts the contra entry: the
   * cashbook and the client's ledger fall by the amount, the reversal is {@code APPROVED} and the
   * original is {@code REVERSED} and points at it.
   */
  @Test
  void approvedCreditReversal_movesCashbookAndClient() {
    var account = books.freshAccount(false);
    var client = books.newClient("H-a");
    var depositId = books.deposit(account, client, "5000.00");
    // "An APPROVED credit": the hand-set stays so H-a is isolated from H-d (ruling #1).
    books.handSetApproved(depositId);

    var reversal = books.reverse(account, depositId, Expected.none());

    books.approve(account, reversal.id(), approverId, Expected.cashbookAndClient("-5000.00"));

    assertThat(books.statusOf(reversal.id())).as("the reversal").isEqualTo("APPROVED");
    assertThat(books.statusOf(depositId)).as("original deposit").isEqualTo("REVERSED");
    assertThat(books.reversedByOf(depositId))
        .as("original deposit reversedById")
        .isEqualTo(reversal.id());
  }

  /**
   * H-a, refusal half (§16.3.11): the contra takes money off the client, so at approval the client
   * — and the matter cell — must still cover it. Here the client spent R4 000 of the R5 000 after
   * the reversal was requested; the approval is refused and nothing moves.
   */
  @Test
  void approvedCreditReversal_refusedWhenTheClientNoLongerCoversIt() {
    var account = books.freshAccount(false);
    var client = books.newClient("H-a refused");
    var depositId = books.deposit(account, client, "5000.00");
    books.handSetApproved(depositId);
    var reversal = books.reverse(account, depositId, Expected.none());
    var paymentId = books.requestPayment(account, client, "4000.00");
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-4000.00"));

    books.assertApprovalRefused(
        account,
        reversal.id(),
        approverId,
        "Insufficient trust balance",
        "Insufficient trust balance for "
            + client.name()
            + ". Available: R1\u00a0000,00, Requested: R5\u00a0000,00");

    assertThat(books.statusOf(reversal.id())).as("the reversal").isEqualTo("AWAITING_APPROVAL");
    assertThat(books.statusOf(depositId)).as("original deposit").isEqualTo("APPROVED");
    assertThat(books.reversedByOf(depositId)).as("original deposit reversedById").isNull();
  }

  // --- H-b ------------------------------------------------------------------------------------

  /**
   * H-b — <b>structural — closed by derived balances (646A)</b>. Pinned by 649A as {@code
   * debitReversalWithoutCard_movesCashbookOnly_pinsKnownDefect} (reproduced red on {@code main}
   * {@code aecf93279}, see {@code qa_cycle/evidence/phase92/649A/RED-RUNS.md}): a posted debit for
   * a client with no ledger card, written straight to the repository as {@code
   * LegalDemoDataSeeder.saveTrustTxn} did, reversed into {@code cashbook − client ledgers} growing
   * by the amount — the reversal credited a card only if one existed.
   *
   * <p>Since 646A the client ledger is derived from the posted rows (ADR-378) and the card is only
   * the per-client lock anchor, so whether an anchor exists no longer decides what a reversal
   * moves; and the original setup is impossible ({@code chk_trust_txn_posting} refuses a posted row
   * written without {@code TrustPostingService}). Green regression: the rows are posted straight
   * through {@code TrustPostingService} — which creates no anchor — then the payment is reversed
   * through the product, and both sides move by the amount.
   */
  @Test
  void debitReversalForAClientWithoutAnAnchor_movesBothSides() {
    var account = books.freshAccount(false);
    var client = books.newClient("H-b");
    books.postDirect(account, "DEPOSIT", "10000.00", client);
    var paymentId = books.postDirect(account, "PAYMENT", "4000.00", client);

    books.reverse(account, paymentId, Expected.cashbookAndClient("4000.00"));

    assertThat(books.statusOf(paymentId)).as("the payment").isEqualTo("REVERSED");
  }

  // --- H-c ------------------------------------------------------------------------------------

  /**
   * H-c — reproduced red on {@code main} {@code aecf93279} (649A) and pinned there; fixed in
   * <b>649B</b>. A rejected reversal leaves the original reversible: the already-reversed check
   * ignores {@code REJECTED} contra rows, so the second attempt returns a {@code REVERSAL} awaiting
   * approval for the same original, and moves nothing.
   */
  @Test
  void rejectedCreditReversal_leavesOriginalReversible() {
    var account = books.freshAccount(false);
    var client = books.newClient("H-c");
    var depositId = books.deposit(account, client, "5000.00");
    // Kept so H-c is isolated from H-d (on main the RECORDED deposit is refused for another
    // reason).
    books.handSetApproved(depositId);

    var firstReversal = books.reverse(account, depositId, Expected.none());
    books.reject(account, firstReversal.id(), approverId);
    assertThat(books.statusOf(firstReversal.id())).as("first reversal").isEqualTo("REJECTED");

    var second = books.reverse(account, depositId, Expected.none());

    assertThat(second.id()).isNotEqualTo(firstReversal.id());
    assertThat(second.status()).isEqualTo("AWAITING_APPROVAL");
    assertThat(second.reversalOf()).isEqualTo(depositId);
  }

  // --- H-d ------------------------------------------------------------------------------------

  /**
   * H-d — founder ruling #1 (§16.12.2): reproduced red on {@code main} {@code aecf93279} (649A) and
   * pinned there; fixed in <b>649B</b>. Any posted row ({@code RECORDED} or {@code APPROVED}) is
   * reversible under its registry rule; reversing a deposit recorded through the service returns a
   * {@code REVERSAL} {@code AWAITING_APPROVAL} (a credit — maker-checker unchanged) and moves
   * nothing yet.
   */
  @Test
  void recordedDeposit_isReversible() {
    var account = books.freshAccount(false);
    var client = books.newClient("H-d");
    var depositId = books.deposit(account, client, "5000.00");
    assertThat(books.statusOf(depositId)).isEqualTo("RECORDED");

    var reversal = books.reverse(account, depositId, Expected.none());

    assertThat(reversal.status()).isEqualTo("AWAITING_APPROVAL");
    assertThat(books.statusOf(depositId)).as("original deposit").isEqualTo("RECORDED");
  }

  // --- Path 9: reversal of a RECORDED deposit (H-d, ruling #1) ---------------------------------

  /**
   * Path 9 (§16.9.2): a deposit recorded through the service — no hand-set — is reversed; the
   * contra awaits approval and moves nothing; a second member approves it and the cashbook and the
   * client fall by the amount; the deposit ends {@code REVERSED}, the contra {@code APPROVED}.
   */
  @Test
  void recordedDepositReversal_approved_movesCashbookAndClient() {
    var account = books.freshAccount(false);
    var client = books.newClient("P9");
    var depositId = books.deposit(account, client, "5000.00");

    var reversal = books.reverse(account, depositId, Expected.none());
    books.approve(account, reversal.id(), approverId, Expected.cashbookAndClient("-5000.00"));

    assertThat(books.statusOf(reversal.id())).as("the reversal").isEqualTo("APPROVED");
    assertThat(books.statusOf(depositId)).as("original deposit").isEqualTo("REVERSED");
    assertThat(books.reversedByOf(depositId)).isEqualTo(reversal.id());
    books.assertServicesAgree(account);
  }

  // --- Paths 6 and 10: a debit reversed with an earlier reversal date ---------------------------

  /**
   * Paths 6 (contra shape) and 10 (§16.9.2): a R4 000 payment dated 1 March is reversed with a
   * reversal date of 15 April. Today the pair nets to zero; as at any date before the payment
   * neither counts, between the two dates the payment counts, from the reversal date both count —
   * the original keeps its effects (EQ-6 v2 checks the shape of both rows on every date).
   */
  @Test
  void debitReversedOnAnEarlierDate_asAtBalancesBeforeBetweenAndAfter() {
    var account = books.freshAccount(false);
    var client = books.newClient("P10");
    books.deposit(account, client, "10000.00");
    var paymentId = books.requestPayment(account, client, "4000.00");
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-4000.00"));
    var reversalDate = LocalDate.of(2026, 4, 15);

    var reversal =
        books.reverse(account, paymentId, Expected.cashbookAndClient("4000.00"), reversalDate);

    assertThat(reversal.status()).isEqualTo("RECORDED");
    assertThat(reversal.transactionDate()).isEqualTo(reversalDate);
    assertThat(books.statusOf(paymentId)).isEqualTo("REVERSED");
    assertThat(books.reversedByOf(paymentId)).isEqualTo(reversal.id());
    assertThat(books.transactionDateOf(paymentId))
        .as("the original keeps its own date")
        .isEqualTo(TrustBooksFixture.POSTING_DATE);

    assertAsAt(account, client, LocalDate.of(2026, 2, 28), "0.00");
    assertAsAt(account, client, LocalDate.of(2026, 3, 31), "6000.00");
    assertAsAt(account, client, reversalDate.minusDays(1), "6000.00");
    assertAsAt(account, client, reversalDate, "10000.00");
    books.assertServicesAgree(account);
  }

  // --- Path 8 with a reversal date: both legs ---------------------------------------------------

  /**
   * Path 8 with {@code reversalDate} (§16.9.2): a {@code RECORDED} transfer (no hand-set — ruling
   * #1) is reversed with an earlier date; both contra legs carry the date and the source and target
   * ledgers are restored as at that date, not before it.
   */
  @Test
  void transferReversedOnAnEarlierDate_bothLegsCarryTheDate() {
    var account = books.freshAccount(false);
    var source = books.newClient("P8d source");
    var target = books.newClient("P8d target");
    books.deposit(account, source, "10000.00");
    var legs = books.transfer(account, source, target, "4000.00");
    var transferOutId = legs.get(0).id();
    var transferInId = legs.get(1).id();
    var reversalDate = LocalDate.of(2026, 5, 10);

    var outContra = books.reverse(account, transferOutId, Expected.none(), reversalDate);

    var inContraId = books.reversedByOf(transferInId);
    assertThat(inContraId).as("the TRANSFER_IN's contra").isNotNull();
    assertThat(outContra.transactionDate()).isEqualTo(reversalDate);
    assertThat(books.transactionDateOf(inContraId)).isEqualTo(reversalDate);
    assertThat(books.statusOf(transferOutId)).isEqualTo("REVERSED");
    assertThat(books.statusOf(transferInId)).isEqualTo("REVERSED");

    var before = reversalDate.minusDays(1);
    assertThat(books.clientLedgerAsAt(account, source, before)).isEqualByComparingTo("6000.00");
    assertThat(books.clientLedgerAsAt(account, target, before)).isEqualByComparingTo("4000.00");
    assertThat(books.clientLedgerAsAt(account, source, reversalDate))
        .isEqualByComparingTo("10000.00");
    assertThat(books.clientLedgerAsAt(account, target, reversalDate)).isEqualByComparingTo("0.00");
    books.assertBalancedAsAt(account, before);
    books.assertBalancedAsAt(account, reversalDate);
    books.assertServicesAgree(account);
  }

  // --- Refusals ---------------------------------------------------------------------------------

  @Test
  void reversalDate_inTheFutureOrBeforeTheOriginal_isRefused() {
    var account = books.freshAccount(false);
    var client = books.newClient("Refused dates");
    books.deposit(account, client, "5000.00");
    var paymentId = books.requestPayment(account, client, "1000.00");
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-1000.00"));

    books.assertReversalRefused(
        account, paymentId, "The reversal date cannot be in the future", books.today().plusDays(1));
    books.assertReversalRefused(
        account,
        paymentId,
        "The reversal date cannot be before the transaction date (2026-03-01)",
        TrustBooksFixture.POSTING_DATE.minusDays(1));

    // Neither refusal wrote anything: the payment is still reversible.
    assertThat(books.statusOf(paymentId)).isEqualTo("APPROVED");
    books.reverse(account, paymentId, Expected.cashbookAndClient("1000.00"));
  }

  @Test
  void aReversalOrAnAlreadyReversedRow_isRefused() {
    var account = books.freshAccount(false);
    var client = books.newClient("Refused rows");
    books.deposit(account, client, "5000.00");
    var paymentId = books.requestPayment(account, client, "1000.00");
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-1000.00"));
    var contra = books.reverse(account, paymentId, Expected.cashbookAndClient("1000.00"));

    books.assertReversalRefused(account, paymentId, "Transaction has already been reversed");
    books.assertReversalRefused(
        account, contra.id(), "Transaction type REVERSAL cannot be reversed");
  }

  @Test
  void aSecondReversalWhileTheFirstAwaitsApproval_isRefused() {
    var account = books.freshAccount(false);
    var client = books.newClient("Refused pending");
    var depositId = books.deposit(account, client, "5000.00");
    var pending = books.reverse(account, depositId, Expected.none());
    assertThat(pending.status()).isEqualTo("AWAITING_APPROVAL");

    books.assertReversalRefused(account, depositId, "Transaction has already been reversed");
  }

  // --- reverseForUnmatch (649B for 647A, §16.3.5) ----------------------------------------------

  /**
   * Unmatching a recorded statement line reverses each posted original linked to it through the
   * normal reversal path, the contra dated the <b>line's</b> date — so as at that date the pair
   * nets to zero and no earlier as-at balance changes.
   */
  @Test
  void reverseForUnmatch_reversesTheLinkedRow_onTheLinesDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("Unmatch");
    books.deposit(account, client, "8000.00");
    var paymentId = books.requestPayment(account, client, "3000.00");
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-3000.00"));
    var lineDate = TrustBooksFixture.POSTING_DATE.plusDays(2);
    var lineId = books.bankLineFor(account, paymentId, lineDate);

    var contras = books.reverseForUnmatch(account, lineId, Expected.cashbookAndClient("3000.00"));

    assertThat(contras).hasSize(1);
    var contra = contras.getFirst();
    assertThat(contra.getTransactionType()).isEqualTo("REVERSAL");
    assertThat(contra.getReversalOf()).isEqualTo(paymentId);
    assertThat(contra.getStatus()).isEqualTo("RECORDED");
    assertThat(contra.getTransactionDate()).isEqualTo(lineDate);
    assertThat(books.statusOf(paymentId)).isEqualTo("REVERSED");
    assertAsAt(account, client, lineDate.minusDays(1), "5000.00");
    assertAsAt(account, client, lineDate, "8000.00");
  }

  /**
   * CodeRabbit on PR #1789, 648A: the unmatch path refuses an investment's placement exactly as
   * {@code reverseTransaction} does — on the type ({@code INV_PLACE} has reversal {@code NONE}),
   * with the same guidance — otherwise the placement would be credited back while its investment
   * stays ACTIVE with the money on the investment account. Nothing moves on either account; the
   * placement stays APPROVED and the investment ACTIVE.
   */
  @Test
  void reverseForUnmatch_refusesAnInvestmentsPlacement() {
    var account = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("Unmatch placement");
    books.deposit(account, client, "5000.00");
    var placed = books.requestPlacement(account, investmentAccount, client, "1000.00");
    var placementId = placed.depositTransactionId();
    books.approvePlacement(
        account,
        Expected.cashbookAndClient("-1000.00"),
        investmentAccount,
        Expected.cashbookAndInvestment("1000.00"),
        placementId,
        approverId);
    var lineId =
        books.bankLineFor(account, placementId, TrustBooksFixture.POSTING_DATE.plusDays(2));

    books.assertUnmatchRefused(
        account,
        lineId,
        "Cannot unmatch statement line",
        "This entry placed a trust investment. Withdraw the investment instead of reversing its"
            + " placement.");

    assertThat(books.statusOf(placementId)).as("placement").isEqualTo("APPROVED");
    assertThat(books.reversedByOf(placementId)).as("placement reversedById").isNull();
    assertThat(
            books.read(() -> investmentRepository.findById(placed.id()).orElseThrow().getStatus()))
        .as("investment")
        .isEqualTo("ACTIVE");
  }

  /**
   * 649B found.md R3 (647A, HF-2): {@code reverseForUnmatch} reverses only what a line recorded
   * from the statement created. An ordinary match links a row that existed before the match;
   * unmatching it must never book a contra. Refused; nothing moves; the payment stays APPROVED.
   */
  @Test
  void reverseForUnmatch_refusesALineThatIsNotRecorded() {
    var account = books.freshAccount(false);
    var client = books.newClient("Unmatch matched");
    books.deposit(account, client, "4000.00");
    var paymentId = books.requestPayment(account, client, "1500.00");
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-1500.00"));
    var lineId =
        books.matchedLineFor(account, paymentId, TrustBooksFixture.POSTING_DATE.plusDays(2));

    books.assertUnmatchRefused(
        account,
        lineId,
        "Cannot unmatch statement line",
        "Only a line recorded from the statement has entries to reverse");

    assertThat(books.statusOf(paymentId)).as("matched payment").isEqualTo("APPROVED");
    assertThat(books.reversedByOf(paymentId)).as("matched payment reversedById").isNull();
  }

  /**
   * 646B found.md (647A, HF-1): a contra carries its original's ledger target (§16.3.11). Reversing
   * a posted {@code NON_CLIENT} row through the generic endpoint ({@code FUND_SWEEP} reverses
   * {@code IMMEDIATE}) must produce a contra on the original's creditor account — before 647A the
   * contra had none and {@code chk_trust_txn_ledger_target} refused it.
   */
  @Test
  void genericReversalOfANonClientRow_carriesItsCreditorAccount() {
    var account = books.freshAccount(false);
    var fund = books.creditorAccount(account, "FUND_INTEREST_AND_CHARGES");
    var sweepId =
        books.postNonClientDirect(account, "FUND_SWEEP", "40.00", TrustBooksFixture.POSTING_DATE);

    var contra = books.reverse(account, sweepId, Expected.cashbookAndFund("40.00"));

    assertThat(contra.ledger()).isEqualTo(TrustLedger.NON_CLIENT);
    assertThat(contra.creditorAccountId()).isEqualTo(fund);
    assertThat(books.read(() -> transactionRepository.findById(contra.id()).orElseThrow()))
        .satisfies(
            row -> {
              assertThat(row.getCreditorAccountId()).as("contra creditor account").isEqualTo(fund);
              assertThat(row.getInvestmentId()).isNull();
              assertThat(row.getChargeRecoverable()).isNull();
              assertThat(row.getChargeVatAmount()).isNull();
            });
    assertThat(books.statusOf(sweepId)).isEqualTo("REVERSED");
  }

  /** Cashbook and the client's ledger as at {@code asAt}, both {@code amount}; books balanced. */
  private void assertAsAt(
      UUID account, TrustBooksFixture.Client client, LocalDate asAt, String amount) {
    var snapshot = books.snapshotAsAt(account, asAt);
    assertThat(snapshot.cashbook()).as("cashbook as at %s", asAt).isEqualByComparingTo(amount);
    assertThat(books.clientLedgerAsAt(account, client, asAt))
        .as("client ledger as at %s", asAt)
        .isEqualByComparingTo(amount);
    books.assertBalancedAsAt(account, asAt);
  }
}
