package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.BooksSnapshot;
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
 * qa_cycle/evidence/phase92/649A/RED-RUNS.md}. 649B flips H-a, H-c and H-d; 646A retires H-b (its
 * setup becomes impossible — see the method).
 *
 * <p>Two tenants: the main tenant holds H-a, H-c and H-d and ends with {@code
 * assertFirmBalanced(today)}; H-b deliberately leaves an account out of balance (cashbook ≠ client
 * ledgers), so it runs in a second, separate tenant that the firm-wide assertion does not cover.
 * Setup and posting steps come from the shared {@link TrustBooksFixture}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustReversalEquationIntegrationTest {

  private static final String ORG_ID = "org_trust_reversal_equation";
  private static final String CARDLESS_ORG_ID = "org_trust_reversal_cardless";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustBooksFixture fixture;

  /** Main tenant: H-a, H-c, H-d; ends firm-balanced. */
  private TrustBooksFixture.Tenant books;

  private UUID approverId;

  /** Separate tenant for H-b, which deliberately leaves an account out of balance. */
  private TrustBooksFixture.Tenant cardless;

  private UUID cardlessMemberId;

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

    var cardlessSchema =
        provisioningService
            .provisionTenant(CARDLESS_ORG_ID, "Trust Reversal Cardless Org", null)
            .schemaName();
    cardlessMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                CARDLESS_ORG_ID,
                "user_rev_cardless_owner",
                "rev_cardless@test.com",
                "Rev Cardless Owner",
                "owner"));
    fixture.enableTrustModule(cardlessSchema);
    cardless = fixture.tenant(cardlessSchema, CARDLESS_ORG_ID, cardlessMemberId);
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
   * H-b — pinned defect, <b>structural — closed by derived balances</b> in <b>646A</b>. Reproduced
   * red on {@code main} {@code aecf93279} (see {@code qa_cycle/evidence/phase92/649A/RED-RUNS.md}).
   *
   * <p>A posted debit for a client with <b>no ledger card</b> is written straight to the
   * repository, with the constructor {@code LegalDemoDataSeeder.saveTrustTxn} uses — but {@code
   * APPROVED}, because the seeder's {@code RECORDED} is refused by the reversal guard. Today the
   * reversal sets the original {@code REVERSED} (it drops out of the cashbook, which rises by the
   * amount) but credits a card only if one exists, so {@code cashbook − client ledgers} grows by
   * the amount.
   *
   * <p><b>Reproduces only through direct repository writes.</b> Two service-only routes were probed
   * on {@code main} and both are refused (probes recorded in RED-RUNS.md, not committed): (1) the
   * seeder's own {@code RECORDED} payment → {@code reverseTransaction} refused "Transaction must be
   * in APPROVED status to be reversed"; (2) {@code recordPayment} for a client with no card then
   * {@code approveTransaction} → refused "Insufficient trust balance … Available: R0,00". Cards are
   * never deleted, so no service path produces a posted debit without a card.
   *
   * <p>Intended: a reversal moves both sides or neither, {@code difference()} unchanged. This pin
   * is <b>closed (retired) by 646A</b> — the setup becomes impossible once card-less rows cannot
   * exist ({@code chk_trust_txn_posting}; client ledgers derived from the rows), so 646A deletes or
   * rewrites this test rather than flipping one assertion. The direct write is confined to {@link
   * TrustBooksFixture.Tenant#saveDirect}. Runs in a separate tenant because it leaves the account
   * out of balance.
   */
  @Test
  void debitReversalWithoutCard_movesCashbookOnly_pinsKnownDefect() {
    var account = cardless.freshAccount(false);
    var client = cardless.newClient("H-b");
    cardless.saveDirect(account, "DEPOSIT", "10000.00", client, "RECORDED");
    var paymentId = cardless.saveDirect(account, "PAYMENT", "4000.00", client, "APPROVED");

    var before = new BooksSnapshot[1];
    var after = new BooksSnapshot[1];
    cardless.as(
        cardlessMemberId,
        () -> {
          before[0] = harness.snapshot(account);
          transactionService.reverseTransaction(paymentId, "H-b: paid to the wrong party");
          after[0] = harness.snapshot(account);
        });

    var moved = after[0].deltaSince(before[0]);
    // PIN — intended: the difference does not move (both sides move, or neither).
    assertThat(moved.cashbook()).as("cashbook moved").isEqualByComparingTo("4000.00");
    assertThat(moved.clientLedgers()).as("client ledgers moved").isEqualByComparingTo("0.00");
    assertThat(after[0].difference().subtract(before[0].difference()))
        .as("cashbook − client ledgers moved")
        .isEqualByComparingTo("4000.00");
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
