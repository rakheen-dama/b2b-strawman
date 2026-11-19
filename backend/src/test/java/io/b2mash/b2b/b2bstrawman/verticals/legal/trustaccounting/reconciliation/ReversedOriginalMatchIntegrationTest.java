package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingService.RecordFromLineRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.MatchResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.TrustReconciliationResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 92 slice 652H (tasks 652.36–652.38; HF-8 / 649B F5): a REVERSED original is still posted —
 * it keeps its cashbook effect and only its contra cancels it (649B, ADR-378) — so it matches the
 * bank line that shows it, by auto-match and by hand, like any posted row. When the bank shows both
 * legs (the movement and its return) and both are matched, nothing is outstanding and the month
 * balances (§16.9.6 postings #7–#10). Matching never changes the row's status; unmatching only
 * clears the link.
 *
 * <p>Except (652H D-1, R-1): a REVERSED row of a type only ever recorded from a statement line
 * ({@code TrustTransactionType.recordedOnlyFromStatementLine()}) was born linked to its line and
 * has since been unrecorded from it — it is never matched again, by auto-match or by hand.
 *
 * <p>And (652H D-4, widened by D-5, narrowed to D-6): auto-match links a REVERSED original only on
 * its own reference (tier 1, never by amount/date) and only when the run's initial pool held no
 * live row of its amount and sign — so a reverse-then-re-enter never matches the bank line to the
 * reversed row, whatever reference the line carries, and an unrelated line of its amount is never
 * absorbed by it. Anything else is left for a human (manual match).
 *
 * <p>One harness tenant, a fresh account per test; the class ends with {@code assertFirmBalanced}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReversedOriginalMatchIntegrationTest {

  private static final String ORG_ID = "org_trust_reversed_match";

  private static final LocalDate MARCH_END = LocalDate.of(2026, 3, 31);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private StatementLinePostingService statementLinePostingService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private JdbcTemplate jdbc;

  private TrustBooksFixture.Tenant books;
  private UUID memberId;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService.provisionTenant(ORG_ID, "Trust Reversed Match Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_trm_owner", "trm@test.com", "TRM Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_trm_approver",
                "trm_approver@test.com",
                "TRM Approver",
                "owner"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, memberId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  /**
   * Reversal of a credit (§16.9.6 #7/#8): a deposit of R1 000 on 1 March, reversed on 3 March by a
   * contra a second member approves (DEPOSIT: NEEDS_APPROVAL) — the deposit is now REVERSED. The
   * bank shows the deposit and its return. Auto-match links each line to its own row; nothing is
   * outstanding; adjusted bank R0 = cashbook R0; the reconciliation completes. RED on main: the
   * REVERSED deposit is not a candidate, its line stays unmatched and is an outstanding deposit.
   */
  @Test
  void autoMatch_matchesAReversedDepositToItsOwnBankLine_andTheMonthBalances() {
    var account = books.freshAccount(false);
    var client = books.newClient("reversed deposit");
    var depositId = books.deposit(account, client, "1000.00");
    var contra = books.reverse(account, depositId, Expected.none(), LocalDate.of(2026, 3, 3));
    books.approve(account, contra.id(), approverId, Expected.cashbookAndClient("-1000.00"));
    assertThat(books.statusOf(depositId)).as("reversed before matching").isEqualTo("REVERSED");

    var march =
        importStatement(
            account,
            "h-deposit-march.csv",
            "01/03/2026,Deposit received,1000.00,1000.00," + referenceOf(depositId),
            "03/03/2026,Unpaid deposit returned,-1000.00,0.00," + referenceOf(contra.id()));
    var result = autoMatch(march);

    assertLineMatchedTo(march, 0, depositId, "AUTO_MATCHED");
    assertLineMatchedTo(march, 1, contra.id(), "AUTO_MATCHED");
    assertThat(result.autoMatched()).as("both legs auto-matched").isEqualTo(2);
    assertThat(result.unmatched()).as("no line left").isZero();
    assertStillReversedBy(depositId, contra.id());

    var recon = reconcile(account, MARCH_END, march.id());
    assertBalancedWithNothingOutstanding(recon, "0");
    books.as(memberId, () -> harness.assertBankAgrees(account, march.id(), MARCH_END));
    assertThat(complete(recon).status()).as("G1–G4 pass").isEqualTo("COMPLETED");
    books.assertServicesAgree(account, MARCH_END);
  }

  /**
   * Reversal of a debit (§16.9.6 #9/#10): a payment of R400 (approved by a second member) reversed
   * on 5 March (PAYMENT: IMMEDIATE — the contra posts RECORDED at once, the payment is REVERSED).
   * The bank shows the deposit, the payment and the payment returned. All three lines match their
   * own rows; adjusted bank R1 000 = cashbook R1 000. RED on main: the REVERSED payment is not a
   * candidate and stays an outstanding payment of R400.
   */
  @Test
  void autoMatch_matchesAReversedPaymentToItsOwnBankLine_andTheMonthBalances() {
    var account = books.freshAccount(false);
    var client = books.newClient("reversed payment");
    var depositId = books.deposit(account, client, "1000.00");
    var paymentId = books.requestPayment(account, client, "400.00");
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-400.00"));
    var contra =
        books.reverse(
            account, paymentId, Expected.cashbookAndClient("400.00"), LocalDate.of(2026, 3, 5));
    assertThat(books.statusOf(paymentId)).as("reversed before matching").isEqualTo("REVERSED");
    assertThat(books.statusOf(contra.id()))
        .as("a debit's contra posts at once")
        .isEqualTo("RECORDED");

    var march =
        importStatement(
            account,
            "h-payment-march.csv",
            "01/03/2026,Deposit received,1000.00,1000.00," + referenceOf(depositId),
            "01/03/2026,EFT payment,-400.00,600.00," + referenceOf(paymentId),
            "05/03/2026,EFT returned,400.00,1000.00," + referenceOf(contra.id()));
    var result = autoMatch(march);

    assertLineMatchedTo(march, 0, depositId, "AUTO_MATCHED");
    assertLineMatchedTo(march, 1, paymentId, "AUTO_MATCHED");
    assertLineMatchedTo(march, 2, contra.id(), "AUTO_MATCHED");
    assertThat(result.autoMatched()).isEqualTo(3);
    assertThat(result.unmatched()).isZero();
    assertStillReversedBy(paymentId, contra.id());

    var recon = reconcile(account, MARCH_END, march.id());
    assertBalancedWithNothingOutstanding(recon, "1000.00");
    books.as(memberId, () -> harness.assertBankAgrees(account, march.id(), MARCH_END));
    assertThat(complete(recon).status()).as("G1–G4 pass").isEqualTo("COMPLETED");
    books.assertServicesAgree(account, MARCH_END);
  }

  /**
   * Manual match accepts a REVERSED original (HF-8): references that auto-match would not use, so
   * the firm matches by hand — the deposit to its line, the contra to the return. The deposit stays
   * REVERSED; the month balances. RED on main: "Transaction must be APPROVED or RECORDED to match".
   */
  @Test
  void manualMatch_acceptsAReversedOriginal_andKeepsItReversed() {
    var account = books.freshAccount(false);
    var client = books.newClient("manual reversed");
    var depositId = books.deposit(account, client, "700.00");
    var contra = books.reverse(account, depositId, Expected.none(), LocalDate.of(2026, 3, 4));
    books.approve(account, contra.id(), approverId, Expected.cashbookAndClient("-700.00"));

    var march =
        importStatement(
            account,
            "h-manual-march.csv",
            "01/03/2026,Cash deposit branch,700.00,700.00,H-MAN-IN",
            "04/03/2026,Deposit returned,-700.00,0.00,H-MAN-OUT");
    match(march, 0, depositId);
    match(march, 1, contra.id());

    assertLineMatchedTo(march, 0, depositId, "MANUALLY_MATCHED");
    assertLineMatchedTo(march, 1, contra.id(), "MANUALLY_MATCHED");
    assertThat(lineState(march.lines().get(1).id()).get("matched_count")).isEqualTo(2);
    assertStillReversedBy(depositId, contra.id());

    var recon = reconcile(account, MARCH_END, march.id());
    assertBalancedWithNothingOutstanding(recon, "0");
    books.assertServicesAgree(account, MARCH_END);
  }

  /**
   * Unmatching a line matched to a REVERSED original only clears the link (the matched-line path,
   * never reverseForUnmatch): no contra is booked, the row stays REVERSED, and it is a candidate
   * again — auto-match links it back. RED on main at set-up (the manual match is refused).
   */
  @Test
  void unmatch_ofALineMatchedToAReversedOriginal_onlyClearsTheLink() {
    var account = books.freshAccount(false);
    var client = books.newClient("unmatch reversed");
    var depositId = books.deposit(account, client, "650.00");
    var contra = books.reverse(account, depositId, Expected.none(), LocalDate.of(2026, 3, 6));
    books.approve(account, contra.id(), approverId, Expected.cashbookAndClient("-650.00"));
    var march =
        importStatement(
            account,
            "h-unmatch-march.csv",
            "01/03/2026,Deposit received,650.00,650.00," + referenceOf(depositId));
    var lineId = march.lines().getFirst().id();
    match(march, 0, depositId);
    long rowsBefore = transactionCount(account);

    books.as(memberId, () -> reconciliationService.unmatch(lineId));

    assertThat(lineState(lineId).get("match_status")).isEqualTo("UNMATCHED");
    assertThat(lineState(lineId).get("trust_transaction_id")).isNull();
    assertThat(bankLineOf(depositId)).as("the row's link is cleared").isNull();
    assertStillReversedBy(depositId, contra.id());
    assertThat(transactionCount(account))
        .as("no contra booked by the unmatch")
        .isEqualTo(rowsBefore);

    var result = autoMatch(march);
    assertThat(result.autoMatched()).as("a candidate again").isEqualTo(1);
    assertLineMatchedTo(march, 0, depositId, "AUTO_MATCHED");
    assertStillReversedBy(depositId, contra.id());
  }

  /**
   * Guard (green on main): a credit contra awaiting approval leaves its original RECORDED (only a
   * posted contra marks it REVERSED) — the original matches its line as before; the pending contra
   * is unposted and is never a candidate, so the return line stays unmatched.
   */
  @Test
  void pendingCreditContra_leavesTheOriginalRecorded_andIsNeverACandidate() {
    var account = books.freshAccount(false);
    var client = books.newClient("pending contra");
    var depositId = books.deposit(account, client, "550.00");
    var contra = books.reverse(account, depositId, Expected.none(), LocalDate.of(2026, 3, 7));
    assertThat(books.statusOf(contra.id())).isEqualTo("AWAITING_APPROVAL");
    assertThat(books.statusOf(depositId)).as("untouched until approval").isEqualTo("RECORDED");

    var march =
        importStatement(
            account,
            "h-pending-march.csv",
            "01/03/2026,Deposit received,550.00,550.00," + referenceOf(depositId),
            "07/03/2026,Deposit returned,-550.00,0.00," + referenceOf(contra.id()));
    var result = autoMatch(march);

    assertLineMatchedTo(march, 0, depositId, "AUTO_MATCHED");
    assertThat(lineState(march.lines().get(1).id()).get("match_status")).isEqualTo("UNMATCHED");
    assertThat(result.autoMatched()).isEqualTo(1);
  }

  /**
   * Guard: the widened rule is "posted", not "any status" — a payment awaiting approval is still
   * refused, and nothing is linked. (Red on main only by the reworded detail.)
   */
  @Test
  void manualMatch_stillRefusesARowAwaitingApproval() {
    var account = books.freshAccount(false);
    var client = books.newClient("unposted");
    var depositId = books.deposit(account, client, "900.00");
    var paymentId = books.requestPayment(account, client, "300.00");
    var march =
        importStatement(
            account,
            "h-unposted-march.csv",
            "01/03/2026,Deposit received,900.00,900.00," + referenceOf(depositId),
            "02/03/2026,EFT payment,-300.00,600.00,H-UNPOSTED");
    var lineId = march.lines().get(1).id();

    books.as(
        memberId,
        () ->
            assertThatThrownBy(() -> reconciliationService.manualMatch(lineId, paymentId))
                .isInstanceOfSatisfying(
                    InvalidStateException.class,
                    e -> {
                      assertThat(e.getBody().getStatus()).isEqualTo(400);
                      assertThat(e.getBody().getTitle()).isEqualTo("Invalid match state");
                      assertThat(e.getBody().getDetail())
                          .isEqualTo(
                              "Transaction must be posted (recorded, approved or reversed) to"
                                  + " match");
                    }));
    assertThat(lineState(lineId).get("match_status")).isEqualTo("UNMATCHED");
    assertThat(bankLineOf(paymentId)).isNull();
  }

  /**
   * R-1 guard (652H D-1; green on main — it pins that the fix did not open R-1): a bank charge
   * recorded from its line, then unrecorded (unmatching the RECORDED line reverses the row and
   * unlinks it), is REVERSED and unlinked — but it was born of that line, so auto-match never links
   * it back, not even on the line's own reference; the line stays unmatched for the firm to record
   * again.
   */
  @Test
  void autoMatch_doesNotOfferAnUnrecordedStatementRowToItsFormerLine() {
    var account = books.freshAccount(false);
    var client = books.newClient("unrecorded charge");
    var depositId = books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            account,
            "h-unrecorded-march.csv",
            "01/03/2026,Deposit received,1000.00,1000.00," + referenceOf(depositId),
            "20/03/2026,Service fee,-57.50,942.50,H-R1-FEE");
    var feeLineId = march.lines().get(1).id();
    var chargeId = recordCharge(feeLineId);
    books.as(memberId, () -> reconciliationService.unmatch(feeLineId));
    assertThat(books.statusOf(chargeId)).as("unrecorded: reversed").isEqualTo("REVERSED");
    assertThat(bankLineOf(chargeId)).as("unrecorded: unlinked").isNull();

    var result = autoMatch(march);

    assertLineMatchedTo(march, 0, depositId, "AUTO_MATCHED");
    assertThat(lineState(feeLineId).get("match_status")).isEqualTo("UNMATCHED");
    assertThat(lineState(feeLineId).get("trust_transaction_id")).isNull();
    assertThat(bankLineOf(chargeId)).as("never re-linked to its former line").isNull();
    assertThat(result.autoMatched()).as("the deposit only").isEqualTo(1);
    assertThat(books.statusOf(chargeId)).isEqualTo("REVERSED");
  }

  /**
   * R-1 guard (652H D-1): manual match refuses the same unrecorded statement row, with words that
   * say why; nothing is linked. (Red on main only by the detail — main refuses every REVERSED row.)
   */
  @Test
  void manualMatch_refusesAnUnrecordedStatementRow() {
    var account = books.freshAccount(false);
    var march =
        importStatement(
            account, "h-unrecorded-manual.csv", "20/03/2026,Service fee,-57.50,-57.50,H-R1-MAN");
    var feeLineId = march.lines().getFirst().id();
    var chargeId = recordCharge(feeLineId);
    books.as(memberId, () -> reconciliationService.unmatch(feeLineId));
    assertThat(books.statusOf(chargeId)).isEqualTo("REVERSED");

    books.as(
        memberId,
        () ->
            assertThatThrownBy(() -> reconciliationService.manualMatch(feeLineId, chargeId))
                .isInstanceOfSatisfying(
                    InvalidStateException.class,
                    e -> {
                      assertThat(e.getBody().getStatus()).isEqualTo(400);
                      assertThat(e.getBody().getTitle()).isEqualTo("Invalid match state");
                      assertThat(e.getBody().getDetail())
                          .isEqualTo(
                              "This entry was recorded from a statement line and has since been"
                                  + " reversed, so it cannot be matched to a bank line. Record the"
                                  + " line again instead.");
                    }));
    assertThat(lineState(feeLineId).get("match_status")).isEqualTo("UNMATCHED");
    assertThat(bankLineOf(chargeId)).isNull();
  }

  // --- 652H D-4/D-5/D-6: a REVERSED original yields to live rows (reverse-then-re-enter) --------

  /**
   * Reverse-then-re-enter, same reference, different dates (652H D-4): a deposit R of R1 000 on 1
   * March posted to the wrong client is reversed on 2 March (contra C) and re-entered as G on 4
   * March to the right client with the same reference. The bank shows one +1 000 line on 1 March.
   * Tier 1 finds R and G — R yields to the live G, so the line matches G; R and C stay unlinked and
   * cancel: nothing outstanding, adjusted bank = cashbook = R1 000. RED before D-4: tier 1 is
   * ambiguous and tier 2 (exact date) finds only R, so the line matches the reversed row.
   */
  @Test
  void autoMatch_reEnteredDepositWithTheSameReference_matchesTheLiveRow_notTheReversedOne() {
    var account = books.freshAccount(false);
    var reEntered =
        reverseAndReEnter(account, "H-RE-77", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 4));
    var march =
        importStatement(
            account, "h-reenter-ref.csv", "01/03/2026,Deposit received,1000.00,1000.00,H-RE-77");

    var result = autoMatch(march);

    assertMatchedToTheLiveRow(march, result, reEntered);
    var recon = reconcile(account, MARCH_END, march.id());
    assertBalancedWithNothingOutstanding(recon, "1000.00");
    books.as(memberId, () -> harness.assertBankAgrees(account, march.id(), MARCH_END));
    assertThat(complete(recon).status()).as("G1–G4 pass").isEqualTo("COMPLETED");
    books.assertServicesAgree(account, MARCH_END);
  }

  /**
   * Reverse-then-re-enter, same reference AND same date (652H D-4): R and G are identical but for
   * status, so every tier finds both — R yields at each, the line matches G and is not left
   * ambiguous. RED before D-4: every tier is ambiguous and the line stays UNMATCHED.
   */
  @Test
  void autoMatch_reEnteredDepositWithTheSameReferenceAndDate_matchesTheLiveRow_notAmbiguous() {
    var account = books.freshAccount(false);
    var reEntered =
        reverseAndReEnter(account, "H-RE-88", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1));
    var march =
        importStatement(
            account, "h-reenter-same.csv", "01/03/2026,Deposit received,1000.00,1000.00,H-RE-88");

    var result = autoMatch(march);

    assertMatchedToTheLiveRow(march, result, reEntered);
    var recon = reconcile(account, MARCH_END, march.id());
    assertBalancedWithNothingOutstanding(recon, "1000.00");
    books.assertServicesAgree(account, MARCH_END);
  }

  /**
   * Reverse-then-re-enter under a new reference (652H D-4): G gets its own reference, and the bank
   * line carries the bank's own reference (neither row's), so tier 1 finds nothing and tier 2
   * (amount + exact date) finds R and G — R yields, the line matches G. RED before D-4: tier 2 and
   * 3 are ambiguous and the line stays UNMATCHED.
   */
  @Test
  void autoMatch_reEnteredDepositUnderANewReference_matchesTheLiveRow_notTheReversedOne() {
    var account = books.freshAccount(false);
    var reEntered =
        reverseAndReEnter(
            account, "H-RE-99", "H-RE-99-C", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1));
    var march =
        importStatement(
            account, "h-reenter-new.csv", "01/03/2026,Cash deposit,1000.00,1000.00,BANK-CD-0301");

    var result = autoMatch(march);

    assertMatchedToTheLiveRow(march, result, reEntered);
    var recon = reconcile(account, MARCH_END, march.id());
    assertBalancedWithNothingOutstanding(recon, "1000.00");
    books.assertServicesAgree(account, MARCH_END);
  }

  /**
   * Reverse-then-re-enter under a new reference, the bank line carrying the OLD reference (652H
   * D-5, F-652H-3): R ref DEP-77 on 1 March, reversed 2 March by C, G re-entered on 4 March under
   * DEP-77-C; the bank shows one +1 000 line on 1 March, ref DEP-77. A live candidate of R's amount
   * and sign (G) is in the pool, so R is no candidate for the line at any tier. The line then
   * resolves among live rows exactly as on main: tier 1 (DEP-77) finds none, tier 2 (1 March) none,
   * tier 3 (within 3 days) finds G alone — suggested at 0.60, the line stays UNMATCHED. Confirming
   * the suggestion by hand matches G and the month balances. RED before D-5: tier 1 finds R alone
   * and AUTO_MATCHES the reversed row, leaving C and G outstanding.
   */
  @Test
  void
      autoMatch_reEnteredDepositUnderANewReference_lineCarriesTheOldReference_doesNotMatchTheReversedRow() {
    var account = books.freshAccount(false);
    var reEntered =
        reverseAndReEnter(
            account, "DEP-77", "DEP-77-C", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 4));
    var march =
        importStatement(
            account, "h-reenter-old-ref.csv", "01/03/2026,Deposit received,1000.00,1000.00,DEP-77");

    var result = autoMatch(march);

    var state = lineState(march.lines().getFirst().id());
    assertThat(state.get("match_status")).as("line status (tier 3 only)").isEqualTo("UNMATCHED");
    assertThat(state.get("trust_transaction_id")).as("suggested row").isEqualTo(reEntered.live());
    assertThat((BigDecimal) state.get("match_confidence")).isEqualByComparingTo("0.60");
    assertThat(result.autoMatched()).as("nothing auto-matched").isZero();
    assertThat(bankLineOf(reEntered.reversed())).as("reversed R unlinked").isNull();
    assertThat(bankLineOf(reEntered.contra())).as("contra C unlinked").isNull();
    assertThat(bankLineOf(reEntered.live())).as("G not yet linked").isNull();

    match(march, 0, reEntered.live());
    assertLineMatchedTo(march, 0, reEntered.live(), "MANUALLY_MATCHED");
    assertStillReversedBy(reEntered.reversed(), reEntered.contra());
    var recon = reconcile(account, MARCH_END, march.id());
    assertBalancedWithNothingOutstanding(recon, "1000.00");
    books.assertServicesAgree(account, MARCH_END);
  }

  // --- 652H D-6: a REVERSED original auto-matches only on its own reference, unshadowed --------

  /**
   * A deposit R of R1 000 recorded in error on 1 March (never received) is reversed on 2 March by
   * C. The bank shows an unrelated +1 000 receipt on 1 March under its own reference — never
   * booked. A REVERSED original is never a candidate at the amount/date tiers (652H D-6), so the
   * line is not linked to R (not matched, not suggested): it stays unresolved and G4 keeps the
   * month open until the firm books the receipt. RED before D-6: R is the only +1 000 row, so tier
   * 2 (amount + exact date) AUTO_MATCHES the line to R and C sits as an outstanding payment.
   */
  @Test
  void autoMatch_doesNotLetAReversedOriginalAbsorbAnUnrelatedLineByAmountAndDate() {
    var account = books.freshAccount(false);
    var client = books.newClient("deposit in error");
    var originalId = depositOn(account, client, "H-ERR-11", LocalDate.of(2026, 3, 1));
    var contra = books.reverse(account, originalId, Expected.none(), LocalDate.of(2026, 3, 2));
    books.approve(account, contra.id(), approverId, Expected.cashbookAndClient("-1000.00"));
    assertThat(books.statusOf(originalId)).as("reversed before matching").isEqualTo("REVERSED");
    var march =
        importStatement(
            account,
            "h-unrelated-line.csv",
            "01/03/2026,Unrelated receipt,1000.00,1000.00,BANK-UNREL-0301");

    var result = autoMatch(march);

    var state = lineState(march.lines().getFirst().id());
    assertThat(state.get("match_status")).as("line status").isEqualTo("UNMATCHED");
    assertThat(state.get("trust_transaction_id")).as("no row suggested").isNull();
    assertThat(state.get("match_confidence")).as("no confidence").isNull();
    assertThat(result.autoMatched()).as("nothing auto-matched").isZero();
    assertThat(bankLineOf(originalId)).as("reversed R unlinked").isNull();
    assertStillReversedBy(originalId, contra.id());
    var recon = reconcile(account, MARCH_END, march.id());
    assertThat(recon.unresolvedLineCount()).as("the receipt is unresolved (G4)").isEqualTo(1);
  }

  /**
   * Reverse-then-re-enter, same reference and date (DEP-77, 1 March), and the bank shows two +1 000
   * lines with that reference on 1 March. The first line auto-matches the live G; R was shadowed by
   * G in the run's initial pool, so it stays shadowed after G is taken — the second line is not
   * linked to R (652H D-6). RED before D-6: once G left the shrinking pool, R was the only +1 000
   * row and tier 1 AUTO_MATCHED the second line to it.
   */
  @Test
  void autoMatch_reversedOriginalStaysShadowedAfterTheLiveRowIsConsumed() {
    var account = books.freshAccount(false);
    var reEntered =
        reverseAndReEnter(account, "DEP-77", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1));
    var march =
        importStatement(
            account,
            "h-reenter-two-lines.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,DEP-77",
            "01/03/2026,Deposit received,1000.00,2000.00,DEP-77");

    var result = autoMatch(march);

    var lineToG =
        reEntered.live().equals(lineState(march.lines().get(0).id()).get("trust_transaction_id"))
            ? 0
            : 1;
    var otherLine = 1 - lineToG;
    assertLineMatchedTo(march, lineToG, reEntered.live(), "AUTO_MATCHED");
    var other = lineState(march.lines().get(otherLine).id());
    assertThat(other.get("match_status")).as("second line status").isEqualTo("UNMATCHED");
    assertThat(other.get("trust_transaction_id")).as("second line not linked to R").isNull();
    assertThat(result.autoMatched()).as("G only").isEqualTo(1);
    assertThat(bankLineOf(reEntered.reversed())).as("reversed R unlinked").isNull();
    assertThat(bankLineOf(reEntered.contra())).as("contra C unlinked").isNull();
    assertStillReversedBy(reEntered.reversed(), reEntered.contra());
  }

  // --- helpers -----------------------------------------------------------------------------------

  /** The original R, its approved contra C, and the corrected deposit G re-entered after it. */
  private record ReEntered(UUID reversed, UUID contra, UUID live) {}

  private ReEntered reverseAndReEnter(
      UUID account, String reference, LocalDate originalDate, LocalDate reEnteredDate) {
    return reverseAndReEnter(account, reference, reference, originalDate, reEnteredDate);
  }

  /**
   * A R1 000 deposit to the wrong client on {@code originalDate}, reversed on 2 March (approved by
   * the second member — DEPOSIT: NEEDS_APPROVAL), then re-entered to the right client on {@code
   * reEnteredDate} under {@code reEnteredReference}.
   */
  private ReEntered reverseAndReEnter(
      UUID account,
      String originalReference,
      String reEnteredReference,
      LocalDate originalDate,
      LocalDate reEnteredDate) {
    var wrongClient = books.newClient("re-enter wrong client");
    var rightClient = books.newClient("re-enter right client");
    var originalId = depositOn(account, wrongClient, originalReference, originalDate);
    var contra = books.reverse(account, originalId, Expected.none(), LocalDate.of(2026, 3, 2));
    books.approve(account, contra.id(), approverId, Expected.cashbookAndClient("-1000.00"));
    assertThat(books.statusOf(originalId)).as("reversed before matching").isEqualTo("REVERSED");
    var liveId = depositOn(account, rightClient, reEnteredReference, reEnteredDate);
    return new ReEntered(originalId, contra.id(), liveId);
  }

  /** A R1 000 deposit (posts RECORDED) with its own reference and date. */
  private UUID depositOn(
      UUID account, TrustBooksFixture.Client client, String reference, LocalDate date) {
    var id = new UUID[1];
    books.as(
        memberId,
        () ->
            id[0] =
                transactionService
                    .recordDeposit(
                        account,
                        new RecordDepositRequest(
                            client.id(),
                            client.matter(),
                            new BigDecimal("1000.00"),
                            reference,
                            "Deposit",
                            date))
                    .id());
    assertThat(books.statusOf(id[0])).isEqualTo("RECORDED");
    return id[0];
  }

  /** The single line matched the live G; the reversed R and its contra C stay unlinked. */
  private void assertMatchedToTheLiveRow(
      BankStatementResponse statement, MatchResult result, ReEntered reEntered) {
    assertLineMatchedTo(statement, 0, reEntered.live(), "AUTO_MATCHED");
    assertThat(result.autoMatched()).as("the line auto-matched").isEqualTo(1);
    assertThat(result.unmatched()).as("no line left").isZero();
    assertThat(bankLineOf(reEntered.reversed())).as("reversed R unlinked").isNull();
    assertThat(bankLineOf(reEntered.contra())).as("contra C unlinked").isNull();
    assertStillReversedBy(reEntered.reversed(), reEntered.contra());
  }

  /** Records the debit line as a (non-recoverable) bank charge; returns the row it created. */
  private UUID recordCharge(UUID lineId) {
    var rowId = new UUID[1];
    books.as(
        memberId,
        () ->
            rowId[0] =
                statementLinePostingService
                    .recordFromLine(
                        lineId,
                        new RecordFromLineRequest(
                            StatementLineClassification.BANK_CHARGE,
                            false,
                            BigDecimal.ZERO,
                            null,
                            null,
                            null,
                            null,
                            null))
                    .transactions()
                    .getFirst()
                    .id());
    return rowId[0];
  }

  /**
   * Adjusted bank = cashbook = {@code amount}; nothing outstanding; no flag; no line unresolved.
   */
  private static void assertBalancedWithNothingOutstanding(
      TrustReconciliationResponse recon, String amount) {
    assertThat(recon.outstandingDeposits()).as("o/s deposits").isEqualByComparingTo("0");
    assertThat(recon.outstandingPayments()).as("o/s payments").isEqualByComparingTo("0");
    assertThat(recon.adjustedBankBalance()).as("adjusted bank").isEqualByComparingTo(amount);
    assertThat(recon.cashbookBalance()).as("cashbook").isEqualByComparingTo(amount);
    assertThat(recon.unresolvedLineCount()).as("unresolved lines").isZero();
    assertThat(recon.integrityFlags()).as("flags").isEmpty();
    assertThat(recon.isBalanced()).as("balanced").isTrue();
  }

  /** Matching or unmatching never changes the row's status: still REVERSED by its contra. */
  private void assertStillReversedBy(UUID originalId, UUID contraId) {
    assertThat(books.statusOf(originalId)).as("status of %s", originalId).isEqualTo("REVERSED");
    assertThat(books.reversedByOf(originalId)).isEqualTo(contraId);
  }

  /** The statement's line {@code index} is {@code status} and both sides point at each other. */
  private void assertLineMatchedTo(
      BankStatementResponse statement, int index, UUID transactionId, String status) {
    var lineId = statement.lines().get(index).id();
    var state = lineState(lineId);
    assertThat(state.get("match_status")).as("line %d status", index).isEqualTo(status);
    assertThat(state.get("trust_transaction_id")).as("line %d row", index).isEqualTo(transactionId);
    assertThat(bankLineOf(transactionId)).as("row of line %d links back", index).isEqualTo(lineId);
  }

  private String referenceOf(UUID transactionId) {
    return books.read(
        () -> transactionRepository.findById(transactionId).orElseThrow().getReference());
  }

  private UUID bankLineOf(UUID transactionId) {
    return books.read(
        () -> transactionRepository.findById(transactionId).orElseThrow().getBankStatementLineId());
  }

  /** A statement line's status, row, confidence and the statement count. */
  private Map<String, Object> lineState(UUID lineId) {
    return jdbc.queryForMap(
        """
        SELECT l.match_status, l.trust_transaction_id, l.match_confidence, s.matched_count
        FROM "%1$s".bank_statement_lines l
        JOIN "%1$s".bank_statements s ON s.id = l.bank_statement_id
        WHERE l.id = ?
        """
            .formatted(books.schema()),
        lineId);
  }

  private long transactionCount(UUID account) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".trust_transactions WHERE trust_account_id = ?"
            .formatted(books.schema()),
        Long.class,
        account);
  }

  /** Imports an FNB statement through the production import (as the maker). */
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

  private MatchResult autoMatch(BankStatementResponse statement) {
    var result = new MatchResult[1];
    books.as(memberId, () -> result[0] = reconciliationService.autoMatchStatement(statement.id()));
    return result[0];
  }

  /** Matches the statement's line {@code index} to {@code transactionId} (production match). */
  private void match(BankStatementResponse statement, int index, UUID transactionId) {
    var lineId = statement.lines().get(index).id();
    books.as(memberId, () -> reconciliationService.manualMatch(lineId, transactionId));
  }

  /** Creates and calculates a reconciliation of {@code account} as at {@code periodEnd}. */
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
}
