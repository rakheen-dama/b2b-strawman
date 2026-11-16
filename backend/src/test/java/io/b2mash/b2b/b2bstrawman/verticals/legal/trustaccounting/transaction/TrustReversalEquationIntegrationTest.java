package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
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

  /** The tenant: H-a, H-b, H-c, H-d; ends firm-balanced. */
  private TrustBooksFixture.Tenant books;

  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var tenantSchema =
        provisioningService
            .provisionTenant(ORG_ID, "Trust Reversal Equation Org", null)
            .schemaName();
    var memberId =
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

    books.assertApprovalRefused(account, reversal.id(), approverId, "Insufficient trust balance");

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
}
