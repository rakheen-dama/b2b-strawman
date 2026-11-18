package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService.IntegrityFlag;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingService.RecordFromLineRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.TrustReconciliationResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
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
 * Phase 92 slice 649C (task 649.11, architecture §16.3.4, §16.9.2 path 24 gate part, ADR-377): the
 * reconciliation completion gate G1–G4. {@code completeReconciliation} recalculates first (never
 * trusts stored figures — 646C hand-forward H-1a), evaluates all four conditions and refuses once:
 * the title of the first failing condition (G1 → G4), one sentence per failing condition. Warnings
 * never block. On success the statement is {@code RECONCILED} and the flags are stored.
 *
 * <p>One harness tenant (a fresh account per test; the class ends with {@code assertFirmBalanced});
 * a second tenant holds the books that deliberately break EQ-2 / EQ-4 and never runs it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustReconciliationCompletionGateIntegrationTest {

  private static final String ORG_ID = "org_trust_recon_gate";

  /** Books that deliberately break an equation (G2, G3) — never {@code assertFirmBalanced}. */
  private static final String BROKEN_ORG_ID = "org_trust_recon_gate_broken";

  private static final Locale ZAR = VariableFormatter.resolveLocale("ZAR");

  private static final LocalDate MARCH_END = LocalDate.of(2026, 3, 31);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private StatementLinePostingService statementLinePostingService;
  @Autowired private BankStatementRepository bankStatementRepository;
  @Autowired private JdbcTemplate jdbc;

  private TrustBooksFixture.Tenant books;
  private UUID memberId;
  private UUID approverId;

  private TrustBooksFixture.Tenant broken;
  private UUID brokenMemberId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService.provisionTenant(ORG_ID, "Trust Recon Gate Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, "user_trg_owner", "trg@test.com", "TRG Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_trg_approver",
                "trg_approver@test.com",
                "TRG Approver",
                "owner"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, memberId);

    var brokenSchema =
        provisioningService
            .provisionTenant(BROKEN_ORG_ID, "Trust Recon Gate Broken Org", null)
            .schemaName();
    brokenMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                BROKEN_ORG_ID,
                "user_trg_broken_owner",
                "trg_broken@test.com",
                "TRG Broken Owner",
                "owner"));
    fixture.enableTrustModule(brokenSchema);
    broken = fixture.tenant(brokenSchema, BROKEN_ORG_ID, brokenMemberId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  // --- G1, G2, G3 -----------------------------------------------------------------------------

  /**
   * G1: the statement's line (R900, matched) carries a running balance of R1 000 — the bank shows
   * R100 the books do not. Nothing else fails.
   */
  @Test
  void refusedOnG1_whenTheAdjustedBankDiffersFromTheCashbook() {
    var account = books.freshAccount(false);
    var client = books.newClient("G1");
    var depositId = books.deposit(account, client, "900.00");
    var march =
        importStatement(
            account, "g1-march.csv", "01/03/2026,Deposit received,900.00,1000.00,G1-DEP");
    match(march, 0, depositId);
    var recon = reconcile(account, MARCH_END, march.id());
    assertThat(recon.isBalanced()).as("not balanced at calculate").isFalse();
    assertThat(codes(recon.integrityFlags()))
        .as("flags stored at calculate")
        .containsExactly(TrustIntegrityService.BANK_DIFFERENCE);

    assertCompleteRefused(
        recon.id(),
        "Reconciliation does not balance",
        "Adjusted bank balance "
            + rand("1000.00")
            + " differs from the cashbook "
            + rand("900.00")
            + " by "
            + rand("100.00")
            + " as at 31 March 2026.");
  }

  /**
   * G2 (its own tenant): an unpaired cashbook-neutral leg (a TRANSFER_IN of R50 with no OUT) puts
   * the client ledgers R50 above the cashbook; the bank agrees with the cashbook, so only G2 fails.
   */
  @Test
  void refusedOnG2_whenTheCashbookDiffersFromTheTrustCreditors() {
    var account = broken.freshAccount(false);
    var client = broken.newClient("G2");
    var depositId = broken.deposit(account, client, "1000.00");
    var march =
        importStatement(
            broken,
            brokenMemberId,
            account,
            "g2-march.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,G2-DEP");
    match(broken, brokenMemberId, march, 0, depositId);
    broken.postLedgerRowDirect(
        account,
        "TRANSFER_IN",
        "50.00",
        LocalDate.of(2026, 3, 5),
        client.id(),
        client.matter(),
        null,
        null);
    var recon = reconcile(broken, brokenMemberId, account, MARCH_END, march.id());

    assertCompleteRefused(
        broken,
        brokenMemberId,
        recon.id(),
        "Trust books do not balance",
        "As at 31 March 2026 the cashbook is "
            + rand("1000.00")
            + " and trust creditors total "
            + rand("1050.00")
            + " — a difference of "
            + rand("50.00")
            + ". Client ledgers "
            + rand("1050.00")
            + "; investment creditors "
            + rand("0")
            + "; Fund interest and charges "
            + rand("0")
            + "; unidentified deposits "
            + rand("0")
            + ".");
  }

  /**
   * G3 (its own tenant): an unidentified-deposits ledger row naming no item (EQ-4 (a)) — written
   * with its cashbook move, so EQ-1 and EQ-2 still hold; the refusal names the ledger.
   */
  @Test
  void refusedOnG3_whenAnUnidentifiedLedgerRowNamesNoItem() {
    var account = broken.freshAccount(false);
    var client = broken.newClient("G3");
    var depositId = broken.deposit(account, client, "1000.00");
    var march =
        importStatement(
            broken,
            brokenMemberId,
            account,
            "g3-march.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,G3-DEP");
    match(broken, brokenMemberId, march, 0, depositId);
    // A receipt on the unidentified-deposits ledger naming no item; on no line, so it is an
    // outstanding deposit and EQ-1 holds
    broken.postLedgerRowDirect(
        account,
        "FUND_INTEREST",
        "40.00",
        LocalDate.of(2026, 3, 6),
        null,
        null,
        broken.creditorAccount(account, "UNIDENTIFIED_DEPOSITS"),
        null);
    var recon = reconcile(broken, brokenMemberId, account, MARCH_END, march.id());
    assertThat(recon.isBalanced()).as("EQ-1 and EQ-2 hold").isTrue();

    assertCompleteRefused(
        broken,
        brokenMemberId,
        recon.id(),
        "Trust ledgers do not agree with their records",
        "1 unidentified-deposit ledger row names no deposit.");
  }

  // --- G4 -----------------------------------------------------------------------------------

  /**
   * G4: balanced books (bank R1 000 = cashbook R1 000) with two statement lines that net to zero
   * and are neither matched, recorded nor excluded — completion is refused.
   */
  @Test
  void refusedOnG4_whenAStatementLineIsUnmatched() {
    var account = books.freshAccount(false);
    var client = books.newClient("G4");
    var depositId = books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            account,
            "g4-march.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,G4-DEP",
            "05/03/2026,Fee charged in error,50.00,1050.00,G4-ERR",
            "06/03/2026,Fee error corrected,-50.00,1000.00,G4-COR");
    match(march, 0, depositId);
    var recon = reconcile(account, MARCH_END, march.id());
    assertThat(recon.isBalanced()).as("balanced before completing").isTrue();

    assertCompleteRefused(
        recon.id(),
        "Statement lines are unresolved",
        "2 statement lines are neither matched, recorded nor excluded.");
  }

  /**
   * §16.5.3: an unrecorded bank credit of R1 250 fails G1 and G4 together — one refusal, the G1
   * title, the G1 sentence then the G4 sentence.
   */
  @Test
  void refusedOnG1AndG4_inOneResponse_titleOfG1_twoSentences() {
    var account = books.freshAccount(false);
    var march = unknownCreditStatement(account, "g14-march.csv");
    var recon = reconcile(account, MARCH_END, march.id());
    assertThat(codes(recon.integrityFlags()))
        .containsExactly(
            TrustIntegrityService.BANK_DIFFERENCE,
            TrustIntegrityService.UNRESOLVED_STATEMENT_LINES);

    assertCompleteRefused(
        recon.id(),
        "Reconciliation does not balance",
        "Adjusted bank balance "
            + rand("2250.00")
            + " differs from the cashbook "
            + rand("1000.00")
            + " by "
            + rand("1250.00")
            + " as at 31 March 2026. 1 statement line is neither matched, recorded nor excluded.");
  }

  /**
   * §16.5.3 continued: the unknown credit is recorded as an unidentified deposit; the
   * reconciliation then completes, its statement is {@code RECONCILED}, the (empty) flags are
   * stored and the completed audit carries {@code creditor_total}, {@code difference} and {@code
   * flags}.
   */
  @Test
  void passes_afterTheLineIsRecorded_statementReconciled_flagsStored_audited() {
    var account = books.freshAccount(false);
    var march = unknownCreditStatement(account, "gpass-march.csv");
    var recon = reconcile(account, MARCH_END, march.id());
    recordLine(march.lines().get(1).id(), unidentified());
    var recalculated = calculate(recon.id());
    assertThat(recalculated.isBalanced()).isTrue();
    assertThat(recalculated.unresolvedLineCount()).isZero();

    var completed = complete(recon.id());
    assertThat(completed.status()).isEqualTo("COMPLETED");
    assertThat(completed.completedBy()).isEqualTo(memberId);
    assertThat(completed.cashbookBalance()).isEqualByComparingTo("2250.00");
    assertThat(completed.creditorBreakdown().unidentifiedDeposits())
        .isEqualByComparingTo("1250.00");
    assertThat(completed.integrityFlags()).isEmpty();
    assertThat(statementStatus(march.id())).isEqualTo("RECONCILED");

    var details =
        jdbc.queryForObject(
            """
            SELECT details::text FROM "%s".audit_events
            WHERE entity_id = ? AND event_type = 'trust_reconciliation.completed'
            """
                .formatted(books.schema()),
            String.class,
            recon.id());
    assertThat(details)
        .contains("\"creditor_total\": \"2250.00\"")
        .contains("\"difference\": \"0.00\"")
        .contains("\"flags\": []");
    books.assertServicesAgree(account, MARCH_END);
  }

  /**
   * Warnings never block (decision 9, ruling #5 / OL-7): a non-recoverable bank charge recorded
   * from the statement puts the Fund ledger in debit and leaves a charge to refund, and a client
   * paid more than it holds is in debit — the books still balance, so the reconciliation completes,
   * with the three warnings stored (they round-trip through the jsonb column, D-11).
   */
  @Test
  void warningsNeverBlock_fundInDebitClientInDebitAndChargesToRefundStillComplete() {
    var account = books.freshAccount(false);
    var client = books.newClient("warnings");
    var overdrawn = books.newClient("overdrawn");
    var depositId = books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            account,
            "gwarn-march.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,GW-DEP",
            "20/03/2026,Service fee,-57.50,942.50,GW-FEE");
    match(march, 0, depositId);
    recordLine(
        march.lines().get(1).id(),
        new RecordFromLineRequest(
            StatementLineClassification.BANK_CHARGE,
            false,
            BigDecimal.ZERO,
            null,
            null,
            null,
            null,
            null));
    // A payment the client cannot cover, written past the sufficiency check: still outstanding at
    // the bank, so EQ-1 holds; EQ-2 holds (cashbook and client move together)
    books.postLedgerRowDirect(
        account,
        "PAYMENT",
        "200.00",
        LocalDate.of(2026, 3, 25),
        overdrawn.id(),
        overdrawn.matter(),
        null,
        null);
    var recon = reconcile(account, MARCH_END, march.id());
    assertThat(recon.isBalanced()).isTrue();

    var completed = complete(recon.id());
    assertThat(completed.status()).isEqualTo("COMPLETED");
    assertThat(codes(completed.integrityFlags()))
        .containsExactly(
            TrustIntegrityService.FUND_LEDGER_IN_DEBIT,
            TrustIntegrityService.CLIENT_LEDGER_IN_DEBIT,
            TrustIntegrityService.CHARGES_TO_REFUND);
    assertThat(completed.integrityFlags()).allMatch(f -> "WARNING".equals(f.severity()));

    // Round trip through trust_reconciliations.integrity_flags (jsonb)
    var reread = new TrustReconciliationResponse[1];
    books.as(memberId, () -> reread[0] = reconciliationService.getReconciliation(recon.id()));
    var stored = reread[0].integrityFlags();
    assertThat(stored).isEqualTo(completed.integrityFlags());
    assertThat(stored.getFirst().amount()).isEqualByComparingTo("57.50");
    assertThat(stored.get(1).subjects())
        .singleElement()
        .satisfies(
            subject -> {
              assertThat(subject.kind()).isEqualTo(TrustIntegrityService.SUBJECT_CLIENT);
              assertThat(subject.id()).isEqualTo(overdrawn.id());
              assertThat(subject.name()).isEqualTo(overdrawn.name());
              assertThat(subject.amount()).isEqualByComparingTo("200.00");
            });
    assertThat(stored.get(2).amount()).isEqualByComparingTo("57.50");
    books.assertServicesAgree(account, MARCH_END);
  }

  // --- H-1a: complete recalculates first ---------------------------------------------------

  /**
   * H-1a (1), 646C CodeRabbit Major: a statement imported after the last calculate that covers the
   * period end makes the reconciliation's statement stop fitting it — complete re-runs the period
   * rules and refuses, instead of trusting the stored {@code is_balanced}.
   */
  @Test
  void complete_recalculates_aStatementImportedAfterCalculateIsRefused() {
    var account = books.freshAccount(false);
    var client = books.newClient("late statement");
    var depositId = books.deposit(account, client, "1000.00");
    var paymentId = books.requestPayment(account, client, "300.00");
    books.approve(account, paymentId, approverId, Expected.cashbookAndClient("-300.00"));
    var statementA =
        importStatement(
            account, "gate-late-a.csv", "01/03/2026,Deposit received,1000.00,1000.00,GLATE-DEP");
    match(statementA, 0, depositId);
    var recon = reconcile(account, MARCH_END, statementA.id());
    assertThat(recon.isBalanced()).as("balanced at calculate").isTrue();

    importStatement(account, "gate-late-b.csv", "25/03/2026,Payment made,-300.00,700.00,GLATE-PAY");

    assertCompleteRefused(
        recon.id(),
        "Statement does not fit the period end",
        "Statement gate-late-b.csv has movements between this statement's last line and 31 March"
            + " 2026. Reconcile against the statement that covers the period end.");
  }

  /**
   * H-1a (2), 646C CodeRabbit Major: a reversal dated inside the period, posted after the last
   * calculate, changes the books as at the period end — complete recalculates, so the completed
   * reconciliation carries the figures as now known (cashbook R0, the unmatched contra an
   * outstanding payment of R1 000), never the stale ones (cashbook R1 000).
   */
  @Test
  void complete_recalculates_aReversalDatedInThePeriodPostedAfterCalculate() {
    var account = books.freshAccount(false);
    var client = books.newClient("late reversal");
    var depositId = books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            account, "gate-rev-march.csv", "01/03/2026,Deposit received,1000.00,1000.00,GREV-DEP");
    match(march, 0, depositId);
    var recon = reconcile(account, MARCH_END, march.id());
    assertThat(recon.isBalanced()).as("balanced at calculate").isTrue();
    assertThat(recon.cashbookBalance()).as("cashbook at calculate").isEqualByComparingTo("1000");

    var contra = books.reverse(account, depositId, Expected.none(), LocalDate.of(2026, 3, 10));
    books.approve(account, contra.id(), approverId, Expected.cashbookAndClient("-1000.00"));

    var completed = complete(recon.id());
    assertThat(completed.status()).isEqualTo("COMPLETED");
    assertThat(completed.cashbookBalance())
        .as("cashbook recalculated at complete")
        .isEqualByComparingTo("0");
    assertThat(completed.outstandingPayments())
        .as("the unmatched contra is outstanding")
        .isEqualByComparingTo("1000.00");
    assertThat(completed.adjustedBankBalance()).as("adjusted bank").isEqualByComparingTo("0");
    assertThat(completed.clientLedgerTotal()).as("client ledgers").isEqualByComparingTo("0");
    books.assertServicesAgree(account, MARCH_END);
  }

  // --- helpers -----------------------------------------------------------------------------------

  /**
   * A deposit of R1 000 (matched to line 1) and an unknown credit "ACB CREDIT UNKNOWN" of R1 250
   * (line 2, unmatched): bank R2 250, cashbook R1 000.
   */
  private BankStatementResponse unknownCreditStatement(UUID account, String fileName) {
    var client = books.newClient("unknown credit");
    var depositId = books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            account,
            fileName,
            "01/03/2026,Deposit received,1000.00,1000.00,GU-DEP",
            "02/03/2026,ACB CREDIT UNKNOWN,1250.00,2250.00,GU-UNK");
    match(march, 0, depositId);
    return march;
  }

  private static RecordFromLineRequest unidentified() {
    return new RecordFromLineRequest(
        StatementLineClassification.UNIDENTIFIED_DEPOSIT, null, null, null, null, null, null, null);
  }

  private void recordLine(UUID lineId, RecordFromLineRequest request) {
    books.as(memberId, () -> statementLinePostingService.recordFromLine(lineId, request));
  }

  private TrustReconciliationResponse calculate(UUID reconciliationId) {
    var recon = new TrustReconciliationResponse[1];
    books.as(
        memberId, () -> recon[0] = reconciliationService.calculateReconciliation(reconciliationId));
    return recon[0];
  }

  private String statementStatus(UUID statementId) {
    return books.read(
        () -> bankStatementRepository.findById(statementId).orElseThrow().getStatus());
  }

  private static List<String> codes(List<IntegrityFlag> flags) {
    return flags.stream().map(IntegrityFlag::code).toList();
  }

  private static String rand(String amount) {
    return VariableFormatter.formatCurrencyText(new BigDecimal(amount), ZAR);
  }

  /** Imports an FNB statement through the production import (as the maker). */
  private BankStatementResponse importStatement(UUID account, String fileName, String... lines) {
    return importStatement(books, memberId, account, fileName, lines);
  }

  private BankStatementResponse importStatement(
      TrustBooksFixture.Tenant tenant,
      UUID member,
      UUID account,
      String fileName,
      String... lines) {
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
    tenant.as(
        member, () -> statement[0] = reconciliationService.importBankStatement(account, file));
    return statement[0];
  }

  /** Matches the statement's line {@code index} to {@code transactionId} (production match). */
  private void match(BankStatementResponse statement, int index, UUID transactionId) {
    match(books, memberId, statement, index, transactionId);
  }

  private void match(
      TrustBooksFixture.Tenant tenant,
      UUID member,
      BankStatementResponse statement,
      int index,
      UUID transactionId) {
    var lineId = statement.lines().get(index).id();
    tenant.as(member, () -> reconciliationService.manualMatch(lineId, transactionId));
  }

  /** Creates and calculates a reconciliation of {@code account} as at {@code periodEnd}. */
  private TrustReconciliationResponse reconcile(
      UUID account, LocalDate periodEnd, UUID statementId) {
    return reconcile(books, memberId, account, periodEnd, statementId);
  }

  private TrustReconciliationResponse reconcile(
      TrustBooksFixture.Tenant tenant,
      UUID member,
      UUID account,
      LocalDate periodEnd,
      UUID statementId) {
    var recon = new TrustReconciliationResponse[1];
    tenant.as(
        member,
        () -> {
          var created = reconciliationService.createReconciliation(account, periodEnd, statementId);
          recon[0] = reconciliationService.calculateReconciliation(created.id());
        });
    return recon[0];
  }

  private TrustReconciliationResponse complete(UUID reconciliationId) {
    var completed = new TrustReconciliationResponse[1];
    books.as(
        memberId,
        () -> completed[0] = reconciliationService.completeReconciliation(reconciliationId));
    return completed[0];
  }

  /** {@code completeReconciliation} is refused once: 400, {@code title}, {@code detail}. */
  private void assertCompleteRefused(UUID reconciliationId, String title, String detail) {
    assertCompleteRefused(books, memberId, reconciliationId, title, detail);
  }

  private void assertCompleteRefused(
      TrustBooksFixture.Tenant tenant,
      UUID member,
      UUID reconciliationId,
      String title,
      String detail) {
    tenant.as(
        member,
        () ->
            assertThatThrownBy(() -> reconciliationService.completeReconciliation(reconciliationId))
                .isInstanceOfSatisfying(
                    InvalidStateException.class,
                    e -> {
                      assertThat(e.getBody().getStatus()).isEqualTo(400);
                      assertThat(e.getBody().getTitle()).isEqualTo(title);
                      assertThat(e.getBody().getDetail()).isEqualTo(detail);
                    }));
  }
}
