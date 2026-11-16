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
 * Phase 92 slice 649A (task 649.3, architecture §16.3.11, R8): the four reversal hypotheses
 * H-a..H-d, each reproduced red-first on {@code main} against the {@link TrustBooksHarness} oracle.
 * All four reproduced; each is committed as a pinned defect (the assertion states today's wrong
 * outcome, the method name ends {@code _pinsKnownDefect}, the Javadoc states the intended behaviour
 * and the fixing slice). The intended-behaviour versions and their red runs are in {@code
 * qa_cycle/evidence/phase92/649A/RED-RUNS.md}. 649B flips H-a, H-c and H-d; H-b closed by 646A
 * (structural — closed by derived balances; its pin is now a green regression, see the method).
 *
 * <p>One tenant: it holds H-a, H-b, H-c and H-d and ends with {@code assertFirmBalanced(today)}.
 * Setup and posting steps come from the shared {@link TrustBooksFixture}.
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
   * H-a — pinned defect, fixed in <b>649B</b> (contra reversal model). Reproduced red on {@code
   * main} {@code aecf93279} (see {@code qa_cycle/evidence/phase92/649A/RED-RUNS.md}).
   *
   * <p>Today: approving a credit reversal moves neither the cashbook nor the client's ledger, and
   * the original stays {@code APPROVED} with no {@code reversedById} — {@code
   * performApprovalCompletion} skips its balance block for {@code REVERSAL} (not in {@code
   * DEBIT_TYPES}) and nothing marks the original; the cashbook sign table counts {@code REVERSAL}
   * as 0.
   *
   * <p>Intended (649B flips this pin): the approval step moves {@code
   * Expected.cashbookAndClient("-5000.00")} and the original is {@code REVERSED}; refused at
   * approval if the matter cell no longer covers the amount.
   */
  @Test
  void approvedCreditReversal_movesNeitherCashbookNorClient_pinsKnownDefect() {
    var account = books.freshAccount(false);
    var client = books.newClient("H-a");
    var depositId = books.deposit(account, client, "5000.00");
    // H-d evidence (ruling #1): a deposit is never APPROVED through the product, so the only way
    // to reach a reversible credit on HEAD is to hand-set the status, as
    // TrustTransactionServiceTest.reverseCreditTransaction_requiresApproval does.
    books.handSetApproved(depositId);

    var reversal = books.reverse(account, depositId, Expected.none());

    // PIN — intended: Expected.cashbookAndClient("-5000.00")
    books.approve(account, reversal.id(), approverId, Expected.none());

    assertThat(books.statusOf(reversal.id())).as("the reversal").isEqualTo("APPROVED");
    // PIN — intended: the original is REVERSED and points at its reversal.
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
   * H-c — pinned defect, fixed in <b>649B</b>. Reproduced red on {@code main} {@code aecf93279}
   * (see {@code qa_cycle/evidence/phase92/649A/RED-RUNS.md}).
   *
   * <p>Today: once a credit reversal is rejected the original can never be reversed again — {@code
   * existsByReversalOf} counts the {@code REJECTED} contra row, so the second attempt is refused
   * "Transaction has already been reversed".
   *
   * <p>Intended (649B flips this pin): a rejected reversal leaves the original reversible — the
   * already-reversed check ignores {@code REJECTED} contra rows; the second attempt returns a
   * {@code REVERSAL} awaiting approval for the same original, and moves nothing.
   */
  @Test
  void rejectedCreditReversal_blocksLaterReversal_pinsKnownDefect() {
    var account = books.freshAccount(false);
    var client = books.newClient("H-c");
    var depositId = books.deposit(account, client, "5000.00");
    books.handSetApproved(depositId); // H-d evidence (ruling #1)

    var firstReversal = books.reverse(account, depositId, Expected.none());
    books.reject(account, firstReversal.id(), approverId);
    // Guards the pin: with the first reversal still pending, the refusal below would hold for a
    // different reason and no longer show H-c.
    assertThat(books.statusOf(firstReversal.id())).as("first reversal").isEqualTo("REJECTED");

    // PIN — intended: accepted, a REVERSAL AWAITING_APPROVAL for depositId.
    books.assertReversalRefused(account, depositId, "Transaction has already been reversed");
  }

  // --- H-d ------------------------------------------------------------------------------------

  /**
   * H-d — pinned defect by founder ruling #1 (§16.12.2), fixed in <b>649B</b>. Reproduced red on
   * {@code main} {@code aecf93279} (see {@code qa_cycle/evidence/phase92/649A/RED-RUNS.md}).
   *
   * <p>Today: a deposit recorded through the service stays {@code RECORDED} and is refused by the
   * reversal guard "Transaction must be in APPROVED status to be reversed" — so the only credit a
   * firm can record can never be reversed through the product.
   *
   * <p>Intended (649B flips this pin): any posted row ({@code RECORDED} or {@code APPROVED}) is
   * reversible under its registry rule; reversing the deposit returns a {@code REVERSAL} {@code
   * AWAITING_APPROVAL} (a credit — maker-checker unchanged) for the deposit, and moves nothing yet.
   */
  @Test
  void recordedDeposit_cannotBeReversed_pinsKnownDefect() {
    var account = books.freshAccount(false);
    var client = books.newClient("H-d");
    var depositId = books.deposit(account, client, "5000.00");
    assertThat(books.statusOf(depositId)).isEqualTo("RECORDED");

    // PIN — intended: accepted, a REVERSAL AWAITING_APPROVAL for depositId.
    books.assertReversalRefused(
        account, depositId, "Transaction must be in APPROVED status to be reversed");
  }
}
