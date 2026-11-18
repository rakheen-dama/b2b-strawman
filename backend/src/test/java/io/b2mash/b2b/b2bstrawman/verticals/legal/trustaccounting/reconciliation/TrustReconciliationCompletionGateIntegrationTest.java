package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityFlag;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingService.RecordFromLineRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.TrustReconciliationResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

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
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private EntityManager entityManager;

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

  /**
   * G3 via EQ-5 (its own tenant): a section 86(4) investment whose record says R250 but whose rows
   * total R200 — the placement is outstanding at the bank and in both the cashbook and the
   * investment creditor, so EQ-1 and EQ-2 hold; the refusal names the investment and both figures.
   */
  @Test
  void refusedOnG3_whenAnInvestmentDisagreesWithItsRecord() {
    var account = broken.freshAccount(false);
    var client = broken.newClient("G3 investment");
    var depositId = broken.deposit(account, client, "1000.00");
    var march =
        importStatement(
            broken,
            brokenMemberId,
            account,
            "g3-inv-march.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,G3I-DEP");
    match(broken, brokenMemberId, march, 0, depositId);
    var investment = broken.investmentDirect(account, account, client, "200.00");
    var placement =
        broken.postLedgerRowDirect(
            account,
            "INV_RECEIVE",
            "200.00",
            LocalDate.of(2026, 3, 3),
            client.id(),
            client.matter(),
            null,
            investment);
    broken.linkPlacementDirect(investment, placement);
    broken.setInvestmentPrincipalDirect(investment, "250.00");
    var recon = reconcile(broken, brokenMemberId, account, MARCH_END, march.id());
    assertThat(recon.isBalanced()).as("EQ-1 and EQ-2 hold").isTrue();
    assertThat(codes(recon.integrityFlags()))
        .containsExactly(TrustIntegrityService.INVESTMENT_BALANCE_MISMATCH);
    var name = recon.integrityFlags().getFirst().subjects().getFirst().name();

    assertCompleteRefused(
        broken,
        brokenMemberId,
        recon.id(),
        "Trust ledgers do not agree with their records",
        "Investment "
            + name
            + ": its ledger rows total "
            + rand("200.00")
            + " against "
            + rand("250.00")
            + " placed, credited and withdrawn as at 31 March 2026.");
  }

  /**
   * G2 and G3 together (its own tenant): an unpaired client leg (EQ-2) and an unidentified-ledger
   * row naming no item (EQ-4) — one refusal with G2's title and both sentences, G2's first.
   */
  @Test
  void refusedOnG2AndG3_inOneResponse_titleOfG2_twoSentences() {
    var account = broken.freshAccount(false);
    var client = broken.newClient("G2 and G3");
    var depositId = broken.deposit(account, client, "1000.00");
    var march =
        importStatement(
            broken,
            brokenMemberId,
            account,
            "g23-march.csv",
            "01/03/2026,Deposit received,1000.00,1000.00,G23-DEP");
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
    assertThat(codes(recon.integrityFlags()))
        .containsExactly(
            TrustIntegrityService.BOOKS_OUT_OF_BALANCE,
            TrustIntegrityService.UNIDENTIFIED_ITEMS_MISMATCH);

    assertCompleteRefused(
        broken,
        brokenMemberId,
        recon.id(),
        "Trust books do not balance",
        "As at 31 March 2026 the cashbook is "
            + rand("1040.00")
            + " and trust creditors total "
            + rand("1090.00")
            + " — a difference of "
            + rand("50.00")
            + ". Client ledgers "
            + rand("1050.00")
            + "; investment creditors "
            + rand("0")
            + "; Fund interest and charges "
            + rand("0")
            + "; unidentified deposits "
            + rand("40.00")
            + ". 1 unidentified-deposit ledger row names no deposit.");
  }

  // --- 650A: the monthly list lock ------------------------------------------------------------

  /**
   * Path 24, lock part (650A, ADR-382): completing to 31 March locks the account's Rule 54.15 list
   * in the same transaction — the response carries its id and the completed audit names it;
   * completing to 15 March on another account locks nothing and the audit has no list id.
   */
  @Test
  void completion_atAMonthEnd_locksTheList_andAuditsItsId_midMonthDoesNot() {
    var account = books.freshAccount(false);
    var march = unknownCreditStatement(account, "glock-march.csv");
    var recon = reconcile(account, MARCH_END, march.id());
    recordLine(march.lines().get(1).id(), unidentified());
    var completed = complete(recon.id());
    assertThat(completed.monthlyListId()).isNotNull();
    var row =
        jdbc.queryForMap(
            "SELECT id, reconciliation_id, period_end, creditor_total FROM \"%s\".trust_monthly_lists WHERE trust_account_id = ?"
                .formatted(books.schema()),
            account);
    assertThat(row.get("id")).isEqualTo(completed.monthlyListId());
    assertThat(row.get("reconciliation_id")).isEqualTo(recon.id());
    assertThat(row.get("period_end").toString()).isEqualTo("2026-03-31");
    assertThat((BigDecimal) row.get("creditor_total")).isEqualByComparingTo("2250.00");
    assertThat(completedAuditDetails(recon.id()))
        .contains("\"monthly_list_id\": \"" + completed.monthlyListId() + "\"");

    var midMonthAccount = books.freshAccount(false);
    var client = books.newClient("mid-month lock");
    var depositId = books.deposit(midMonthAccount, client, "500.00");
    var midMarch =
        importStatement(
            midMonthAccount, "glock-mid.csv", "01/03/2026,Deposit received,500.00,500.00,GL-MID");
    match(midMarch, 0, depositId);
    var midRecon = reconcile(midMonthAccount, LocalDate.of(2026, 3, 15), midMarch.id());
    var midCompleted = complete(midRecon.id());
    assertThat(midCompleted.status()).isEqualTo("COMPLETED");
    assertThat(midCompleted.monthlyListId()).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".trust_monthly_lists WHERE trust_account_id = ?"
                    .formatted(books.schema()),
                Long.class,
                midMonthAccount))
        .isZero();
    assertThat(completedAuditDetails(midRecon.id())).doesNotContain("monthly_list_id");
  }

  // --- CR-2 / D-14: complete and unmatch are serialised on the statement row ------------------

  /**
   * Unmatch first, then complete: the statement row lock makes complete read the gate inputs after
   * the unmatch — the now-unresolved line fails G4 (and the deposit, outstanding again, fails G1),
   * so completion is refused.
   */
  @Test
  void unmatchThenComplete_isRefused_theUnmatchedLineFailsG4() {
    var account = books.freshAccount(false);
    var client = books.newClient("unmatch first");
    var depositId = books.deposit(account, client, "1000.00");
    var march =
        importStatement(
            account, "gum-march.csv", "01/03/2026,Deposit received,1000.00,1000.00,GUM-DEP");
    match(march, 0, depositId);
    var recon = reconcile(account, MARCH_END, march.id());
    assertThat(recon.isBalanced()).as("balanced at calculate").isTrue();
    assertThat(recon.integrityFlags()).isEmpty();

    unmatch(march.lines().getFirst().id());

    assertCompleteRefused(
        recon.id(),
        "Reconciliation does not balance",
        "Adjusted bank balance "
            + rand("2000.00")
            + " differs from the cashbook "
            + rand("1000.00")
            + " by "
            + rand("1000.00")
            + " as at 31 March 2026. 1 statement line is neither matched, recorded nor excluded.");
  }

  /**
   * Complete first, then unmatch (D-14): both unmatch paths — a matched line and a line recorded
   * from the statement (an unidentified deposit, whose unmatch reverses its rows) — are refused on
   * the RECONCILED statement; nothing moves: the lines keep their state and links, no contra is
   * posted, the item stays held, the statement stays RECONCILED and the reconciliation COMPLETED.
   */
  @Test
  void completeThenUnmatch_isRefused_onTheReconciledStatement_lineUnchanged() {
    var account = books.freshAccount(false);
    var march = unknownCreditStatement(account, "gcu-march.csv");
    var recon = reconcile(account, MARCH_END, march.id());
    var matchedLine = march.lines().get(0).id();
    var recordedLine = march.lines().get(1).id();
    recordLine(recordedLine, unidentified());
    calculate(recon.id());
    var completed = complete(recon.id());
    assertThat(completed.status()).isEqualTo("COMPLETED");
    assertThat(statementStatus(march.id())).isEqualTo("RECONCILED");

    for (var lineId : List.of(matchedLine, recordedLine)) {
      var lineBefore = lineState(lineId);
      long rowsBefore = transactionCount(account);
      books.as(
          memberId,
          () ->
              assertThatThrownBy(() -> reconciliationService.unmatch(lineId))
                  .isInstanceOfSatisfying(
                      InvalidStateException.class,
                      e -> {
                        assertThat(e.getBody().getStatus()).isEqualTo(400);
                        assertThat(e.getBody().getTitle())
                            .isEqualTo("Statement already reconciled");
                        assertThat(e.getBody().getDetail())
                            .isEqualTo(
                                "This statement's reconciliation is complete, so its lines cannot"
                                    + " be unmatched.");
                      }));
      assertThat(lineState(lineId)).as("line %s unchanged", lineId).isEqualTo(lineBefore);
      assertThat(transactionCount(account)).as("no contra posted").isEqualTo(rowsBefore);
    }
    assertThat(lineState(matchedLine).get("match_status")).isEqualTo("MANUALLY_MATCHED");
    assertThat(lineState(recordedLine).get("match_status")).isEqualTo("RECORDED");
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM \"%s\".unidentified_deposits WHERE bank_statement_line_id = ?"
                    .formatted(books.schema()),
                String.class,
                recordedLine))
        .as("the unidentified deposit is still held")
        .isEqualTo("HELD");
    assertThat(statementStatus(march.id())).isEqualTo("RECONCILED");
    assertThat(getReconciliation(books, memberId, recon.id()).status()).isEqualTo("COMPLETED");
    books.assertServicesAgree(account, MARCH_END);
  }

  // --- concurrent complete (review fix cycle 2) ------------------------------------------------

  /**
   * A double-click: two completes of the same balanced reconciliation. The first (the owner)
   * completes and holds its transaction open — the statement row locked, COMPLETED not yet
   * committed — so the second (the approver) reads DRAFT and then blocks on the statement row lock
   * (observed in {@code pg_stat_activity}, not a sleep). Once the first commits, the second
   * re-reads the status under the lock and is refused exactly as a sequential second complete is:
   * one completion, one {@code trust_reconciliation.completed} audit event, the winner's {@code
   * completedBy} / {@code completedAt}.
   */
  @Test
  void concurrentDoubleComplete_secondWaitsOnTheStatementLock_andIsRefused_completedOnce()
      throws Exception {
    var account = books.freshAccount(false);
    var march = unknownCreditStatement(account, "gdc-march.csv");
    var recon = reconcile(account, MARCH_END, march.id());
    recordLine(march.lines().get(1).id(), unidentified());
    assertThat(calculate(recon.id()).isBalanced()).isTrue();

    var firstDone = new CountDownLatch(1);
    var releaseFirst = new CountDownLatch(1);
    var firstResult = new AtomicReference<TrustReconciliationResponse>();
    var firstError = new AtomicReference<Throwable>();
    var firstBackendPid = new AtomicReference<Integer>();
    var secondResult = new AtomicReference<TrustReconciliationResponse>();
    var secondError = new AtomicReference<Throwable>();
    var secondFinished = new AtomicBoolean(false);

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
                                  firstResult.set(
                                      reconciliationService.completeReconciliation(recon.id()));
                                  firstBackendPid.set(
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
    assertThat(firstError.get()).as("the first complete").isNull();

    Thread secondThread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    books.as(
                        approverId,
                        () ->
                            secondResult.set(
                                reconciliationService.completeReconciliation(recon.id())));
                  } catch (Throwable t) {
                    secondError.set(t);
                  } finally {
                    secondFinished.set(true);
                  }
                });
    boolean secondBlockedOnFirst;
    boolean secondFinishedBeforeCommit;
    try {
      secondBlockedOnFirst =
          awaitBlockedOnStatementLockHeldByOrFinished(firstBackendPid.get(), secondFinished);
      secondFinishedBeforeCommit = secondFinished.get();
    } finally {
      releaseFirst.countDown();
      join(firstThread);
      join(secondThread);
    }

    assertThat(firstError.get()).as("the first complete (committed)").isNull();
    assertThat(secondBlockedOnFirst)
        .as("the second complete blocked on the bank_statements row lock the first held")
        .isTrue();
    assertThat(secondFinishedBeforeCommit)
        .as("the second complete finished while the first still held the statement lock")
        .isFalse();
    assertThat(secondResult.get()).as("the second complete succeeded").isNull();
    assertThat(secondError.get())
        .as("the second complete, refused as a sequential second complete is")
        .isInstanceOfSatisfying(
            InvalidStateException.class,
            e -> {
              assertThat(e.getBody().getStatus()).isEqualTo(400);
              assertThat(e.getBody().getTitle()).isEqualTo("Invalid reconciliation state");
              assertThat(e.getBody().getDetail()).isEqualTo("Reconciliation is already COMPLETED");
            });

    var winner = firstResult.get();
    var stored = getReconciliation(books, memberId, recon.id());
    assertThat(stored.status()).isEqualTo("COMPLETED");
    assertThat(stored.completedBy()).as("the winner's completedBy").isEqualTo(memberId);
    // the column holds microseconds; the second's overwrite would come well after the release
    assertThat(stored.completedAt())
        .as("the winner's completedAt")
        .isCloseTo(winner.completedAt(), within(1, ChronoUnit.MICROS));
    assertThat(statementStatus(march.id())).isEqualTo("RECONCILED");
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM "%s".audit_events
                WHERE entity_id = ? AND event_type = 'trust_reconciliation.completed'
                """
                    .formatted(books.schema()),
                Long.class,
                recon.id()))
        .as("exactly one completed audit event for this reconciliation")
        .isEqualTo(1L);
  }

  /**
   * Waits (bounded, 30 s) until a backend blocked by {@code holderPid} is running a bank_statements
   * query (true — the statement row lock is contested) or the waiter finishes without having been
   * seen blocked (false). Fails if neither happens.
   */
  private boolean awaitBlockedOnStatementLockHeldByOrFinished(
      int holderPid, AtomicBoolean waiterFinished) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long blocked =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))"
                  + " AND query ILIKE '%bank_statements%'",
              Long.class, holderPid);
      if (blocked != null && blocked > 0) {
        return true;
      }
      if (waiterFinished.get()) {
        return false;
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10)); // poll interval
    }
    throw new AssertionError("the second complete neither blocked nor finished within 30 s");
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

  private String completedAuditDetails(UUID reconciliationId) {
    return jdbc.queryForObject(
        """
        SELECT details::text FROM "%s".audit_events
        WHERE entity_id = ? AND event_type = 'trust_reconciliation.completed'
        """
            .formatted(books.schema()),
        String.class,
        reconciliationId);
  }

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
    return statementStatus(books, statementId);
  }

  private String statementStatus(TrustBooksFixture.Tenant tenant, UUID statementId) {
    return tenant.read(
        () -> bankStatementRepository.findById(statementId).orElseThrow().getStatus());
  }

  /** A statement line's {@code match_status} and {@code trust_transaction_id}, read straight. */
  private Map<String, Object> lineState(UUID lineId) {
    return jdbc.queryForMap(
        """
        SELECT l.match_status, l.trust_transaction_id, s.matched_count
        FROM "%1$s".bank_statement_lines l
        JOIN "%1$s".bank_statements s ON s.id = l.bank_statement_id
        WHERE l.id = ?
        """
            .formatted(books.schema()),
        lineId);
  }

  /** The account's trust transactions (a refused unmatch posts no contra). */
  private long transactionCount(UUID account) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".trust_transactions WHERE trust_account_id = ?"
            .formatted(books.schema()),
        Long.class,
        account);
  }

  private void unmatch(UUID lineId) {
    books.as(memberId, () -> reconciliationService.unmatch(lineId));
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

  /**
   * {@code completeReconciliation} is refused once: 400, {@code title}, {@code detail} — and the
   * refusal rolled everything back: the reconciliation is still {@code DRAFT} with the stored
   * figures and flags it had before (D-9: a refusal stores nothing), and its statement is not
   * {@code RECONCILED}.
   */
  private void assertCompleteRefused(UUID reconciliationId, String title, String detail) {
    assertCompleteRefused(books, memberId, reconciliationId, title, detail);
  }

  private void assertCompleteRefused(
      TrustBooksFixture.Tenant tenant,
      UUID member,
      UUID reconciliationId,
      String title,
      String detail) {
    var before = getReconciliation(tenant, member, reconciliationId);
    var statementBefore = statementStatus(tenant, before.bankStatementId());
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
    var after = getReconciliation(tenant, member, reconciliationId);
    assertThat(after.status()).as("still DRAFT after the refusal").isEqualTo("DRAFT");
    assertThat(after.completedBy()).as("not completed by anyone").isNull();
    assertThat(after.completedAt()).as("no completion time").isNull();
    assertThat(after)
        .as("stored figures and flags unchanged by the refused complete (rolled back)")
        .isEqualTo(before);
    assertThat(statementStatus(tenant, before.bankStatementId()))
        .as("the statement is not RECONCILED after the refusal")
        .isEqualTo(statementBefore)
        .isNotEqualTo("RECONCILED");
  }

  private TrustReconciliationResponse getReconciliation(
      TrustBooksFixture.Tenant tenant, UUID member, UUID reconciliationId) {
    var recon = new TrustReconciliationResponse[1];
    tenant.as(member, () -> recon[0] = reconciliationService.getReconciliation(reconciliationId));
    return recon[0];
  }
}
