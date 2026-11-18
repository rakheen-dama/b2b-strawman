package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService.FirmIntegrityReport;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService.IntegrityFlag;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService.TrustIntegrityReport;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.creditor.UnidentifiedDepositService.AllocateRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLineClassification;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLinePostingService.RecordFromLineRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService.BankStatementResponse;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionService.RecordDepositRequest;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 92 slice 649C (task 649.11, architecture §16.3.9, ADR-377): the trust integrity check.
 * Every flag is raised and cleared; blocking vs warning; the report's figures are the oracle's
 * ({@code assertServicesAgree} now runs the check too — R-9); the endpoints; and the cost of the
 * firm-wide check (open question #9).
 *
 * <p>Three tenants: the harness tenant (a fresh account per test; {@code assertFirmBalanced} at the
 * end), a tenant for books that deliberately break an equation (never firm-balanced), and one for
 * the statement count. Plus a tenant with the module off for the 403.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrustIntegrityServiceIntegrationTest {

  private static final String ORG_ID = "org_trust_integrity";
  private static final String OWNER = "user_tis_owner";
  private static final String NO_TRUST = "user_tis_no_trust";
  private static final String BROKEN_ORG_ID = "org_trust_integrity_broken";
  private static final String COUNT_ORG_ID = "org_trust_integrity_count";
  private static final String NO_MODULE_ORG_ID = "org_trust_integrity_nomod";
  private static final String NO_MODULE_OWNER = "user_tis_nomod_owner";

  private static final LocalDate MARCH_END = LocalDate.of(2026, 3, 31);
  private static final Locale ZAR = VariableFormatter.resolveLocale("ZAR");

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustIntegrityService integrityService;
  @Autowired private TrustBooksService trustBooksService;
  @Autowired private TrustReconciliationService reconciliationService;
  @Autowired private StatementLinePostingService statementLinePostingService;
  @Autowired private UnidentifiedDepositService unidentifiedDepositService;
  @Autowired private TrustTransactionService transactionService;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private TrustBooksFixture.Tenant books;
  private UUID memberId;
  private UUID approverId;
  private TrustBooksFixture.Tenant broken;
  private UUID brokenMemberId;
  private TrustBooksFixture.Tenant counted;
  private UUID countedMemberId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService.provisionTenant(ORG_ID, "Trust Integrity Org", null).schemaName();
    memberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "tis@test.com", "TIS Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_tis_approver",
                "tis_approver@test.com",
                "TIS Approver",
                "owner"));
    var noTrustId =
        TestMemberHelper.syncMember(
            mockMvc, ORG_ID, NO_TRUST, "tis_no_trust@test.com", "TIS No Trust", "member");
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);
    assignRole(owner, noTrustId, createCustomRole(owner, "TIS No Trust Role", "VIEW_LEGAL"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, memberId);

    var brokenSchema =
        provisioningService
            .provisionTenant(BROKEN_ORG_ID, "Trust Integrity Broken Org", null)
            .schemaName();
    brokenMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                BROKEN_ORG_ID,
                "user_tis_broken_owner",
                "tis_broken@test.com",
                "TIS Broken Owner",
                "owner"));
    fixture.enableTrustModule(brokenSchema);
    broken = fixture.tenant(brokenSchema, BROKEN_ORG_ID, brokenMemberId);

    var countSchema =
        provisioningService
            .provisionTenant(COUNT_ORG_ID, "Trust Integrity Count Org", null)
            .schemaName();
    countedMemberId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                COUNT_ORG_ID,
                "user_tis_count_owner",
                "tis_count@test.com",
                "TIS Count Owner",
                "owner"));
    fixture.enableTrustModule(countSchema);
    counted = fixture.tenant(countSchema, COUNT_ORG_ID, countedMemberId);

    provisioningService.provisionTenant(NO_MODULE_ORG_ID, "Trust Integrity No Module Org", null);
    TestMemberHelper.syncMember(
        mockMvc,
        NO_MODULE_ORG_ID,
        NO_MODULE_OWNER,
        "tis_nomod@test.com",
        "TIS NoMod Owner",
        "owner");
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  // --- balanced -------------------------------------------------------------------------------

  /** A deposit: no flag, balanced, and the report's position is the oracle's (R-9). */
  @Test
  void balancedAccount_hasNoBlockingFlag_andAgreesWithTheOracle() {
    var account = books.freshAccount(false);
    books.deposit(account, books.newClient("balanced"), "1000.00");

    var report = check(books, memberId, account, null, null);
    assertThat(report.asAt()).isEqualTo(books.today());
    assertThat(report.balanced()).isTrue();
    assertThat(report.flags()).isEmpty();
    assertThat(report.bank()).isNull();
    assertThat(report.position().cashbook()).isEqualByComparingTo("1000.00");
    assertThat(report.lastCompletedReconciliationId()).isNull();
    books.assertServicesAgree(account);
  }

  // --- warnings -------------------------------------------------------------------------------

  /**
   * A bank charge with no Fund interest: the Fund ledger in debit is a warning; interest clears it.
   */
  @Test
  void fundLedgerInDebit_isAWarning_andClearedByInterest() {
    var account = books.freshAccount(false);
    books.deposit(account, books.newClient("fund"), "1000.00");
    books.postNonClientDirect(account, "BANK_CHARGE", "57.50", LocalDate.of(2026, 3, 10));

    var report = check(books, memberId, account, null, null);
    assertThat(report.balanced()).as("a warning never unbalances").isTrue();
    var flag = only(report, TrustIntegrityService.FUND_LEDGER_IN_DEBIT);
    assertThat(flag.severity()).isEqualTo(TrustIntegrityService.WARNING);
    assertThat(flag.amount()).isEqualByComparingTo("57.50");
    assertThat(flag.message())
        .isEqualTo("The Fund interest and charges ledger is " + rand("57.50") + " in debit.");
    assertThat(flag.subjects())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.kind()).isEqualTo(TrustIntegrityService.SUBJECT_CREDITOR_ACCOUNT);
              assertThat(s.id())
                  .isEqualTo(books.creditorAccount(account, "FUND_INTEREST_AND_CHARGES"));
            });
    books.assertServicesAgree(account);

    books.postNonClientDirect(account, "FUND_INTEREST", "100.00", LocalDate.of(2026, 3, 20));
    assertThat(codes(check(books, memberId, account, null, null))).isEmpty();
    books.assertServicesAgree(account);
  }

  /**
   * A client paid R200 it never held (written past the sufficiency check) is in debit as at the
   * payment's date and after — one subject per client; another client in credit is not named.
   */
  @Test
  void clientLedgerInDebit_isAWarning_oneSubjectPerClient_asAtTheDate() {
    var account = books.freshAccount(false);
    books.deposit(account, books.newClient("in credit"), "1000.00");
    var overdrawn = books.newClient("overdrawn");
    books.postLedgerRowDirect(
        account,
        "PAYMENT",
        "200.00",
        LocalDate.of(2026, 3, 15),
        overdrawn.id(),
        overdrawn.matter(),
        null,
        null);

    var flag = only(check(books, memberId, account, null, null), "CLIENT_LEDGER_IN_DEBIT");
    assertThat(flag.severity()).isEqualTo(TrustIntegrityService.WARNING);
    assertThat(flag.amount()).isEqualByComparingTo("200.00");
    assertThat(flag.subjects())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.kind()).isEqualTo(TrustIntegrityService.SUBJECT_CLIENT);
              assertThat(s.id()).isEqualTo(overdrawn.id());
              assertThat(s.name()).isEqualTo(overdrawn.name());
              assertThat(s.amount()).isEqualByComparingTo("200.00");
            });
    assertThat(codes(check(books, memberId, account, LocalDate.of(2026, 3, 14), null)))
        .as("before the payment")
        .isEmpty();
    books.assertServicesAgree(account);
  }

  /**
   * An unidentified deposit received more than a year ago and still open is a warning; as at a date
   * within its first year, or once allocated to a client, it is not.
   */
  @Test
  void unidentifiedOverOneYear_isAWarning_onlyWhileOpen() {
    var account = books.freshAccount(false);
    var received = books.today().minusYears(1).minusDays(1);
    books.unidentifiedDepositDirect(account, "300.00", received);

    var flag = only(check(books, memberId, account, null, null), "UNIDENTIFIED_OVER_ONE_YEAR");
    assertThat(flag.severity()).isEqualTo(TrustIntegrityService.WARNING);
    assertThat(flag.amount()).isEqualByComparingTo("300.00");
    var item = flag.subjects().getFirst();
    assertThat(item.kind()).isEqualTo(TrustIntegrityService.SUBJECT_UNIDENTIFIED_DEPOSIT);
    assertThat(codes(check(books, memberId, account, received.plusMonths(1), null)))
        .as("within its first year")
        .isEmpty();
    books.assertServicesAgree(account);

    var client = books.newClient("owner found");
    var requested = new UnidentifiedDepositService.UnidentifiedDepositResponse[1];
    books.as(
        memberId,
        () ->
            requested[0] =
                unidentifiedDepositService.allocate(
                    item.id(), new AllocateRequest(client.id(), client.matter(), null, null)));
    books.approve(
        account,
        requested[0].resolutionTransactionId(),
        approverId,
        Expected.clientFromUnidentified("+300.00"));
    assertThat(codes(check(books, memberId, account, null, null))).as("allocated").isEmpty();
    books.assertServicesAgree(account);
  }

  /**
   * A non-recoverable charge recorded from the statement leaves R57,50 to refund; a recoverable one
   * does not; a refund clears it; a charge reversed by unmatching its line is not counted.
   */
  @Test
  void chargesToRefund_raisedByNonRecoverableCharge_clearedByRefund_ignoringReversedRows() {
    var charged = books.freshAccount(false);
    var line = books.unmatchedLine(charged, LocalDate.of(2026, 3, 20), "-57.50", "CHG-1");
    recordCharge(line.lineId(), false);
    var flag = only(check(books, memberId, charged, null, null), "CHARGES_TO_REFUND");
    assertThat(flag.severity()).isEqualTo(TrustIntegrityService.WARNING);
    assertThat(flag.amount()).isEqualByComparingTo("57.50");
    assertThat(flag.message())
        .isEqualTo(
            rand("57.50")
                + " of non-recoverable bank charges has been debited to trust and not yet"
                + " refunded from the business account.");
    books.postNonClientDirect(charged, "CHARGE_REFUND", "57.50", LocalDate.of(2026, 3, 25));
    assertThat(codes(check(books, memberId, charged, null, null))).isEmpty();
    books.assertServicesAgree(charged);

    var recoverable = books.freshAccount(false);
    var recoverableLine =
        books.unmatchedLine(recoverable, LocalDate.of(2026, 3, 20), "-40.00", "CHG-2");
    recordCharge(recoverableLine.lineId(), true);
    assertThat(codes(check(books, memberId, recoverable, null, null)))
        .containsExactly(TrustIntegrityService.FUND_LEDGER_IN_DEBIT);

    var reversed = books.freshAccount(false);
    var reversedLine = books.unmatchedLine(reversed, LocalDate.of(2026, 3, 20), "-30.00", "CHG-3");
    recordCharge(reversedLine.lineId(), false);
    assertThat(codes(check(books, memberId, reversed, null, null)))
        .contains(TrustIntegrityService.CHARGES_TO_REFUND);
    books.reverseForUnmatch(reversed, reversedLine.lineId(), Expected.cashbookAndFund("30.00"));
    assertThat(codes(check(books, memberId, reversed, null, null))).isEmpty();
    books.assertServicesAgree(reversed);
  }

  // --- statement lines and the bank ----------------------------------------------------------

  /**
   * An unresolved line warns on the dashboard (any statement not yet reconciled) and blocks with
   * its statement (the completion gate); once the statement is reconciled the warning goes.
   */
  @Test
  void unresolvedLines_warnOnTheDashboard_blockWithTheStatement() {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, LocalDate.of(2026, 3, 12), "100.00", "UNR-1");

    var dashboard = only(check(books, memberId, account, null, null), "UNRESOLVED_STATEMENT_LINES");
    assertThat(dashboard.severity()).isEqualTo(TrustIntegrityService.WARNING);
    assertThat(dashboard.subjects())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.kind()).isEqualTo(TrustIntegrityService.SUBJECT_BANK_STATEMENT);
              assertThat(s.id()).isEqualTo(line.statementId());
            });
    assertThat(check(books, memberId, account, null, null).balanced()).isTrue();

    var gate = check(books, memberId, account, MARCH_END, line.statementId());
    var blocking = only(gate, "UNRESOLVED_STATEMENT_LINES");
    assertThat(blocking.severity()).isEqualTo(TrustIntegrityService.BLOCKING);
    assertThat(blocking.message())
        .isEqualTo("1 statement line is neither matched, recorded nor excluded.");
    assertThat(gate.balanced()).isFalse();
    assertThat(gate.bank()).isNotNull();

    books.markStatementReconciledDirect(line.statementId());
    assertThat(codes(check(books, memberId, account, null, null))).isEmpty();
  }

  /**
   * EQ-1 is checked at a reconciliation's period end: the statement shows R1 000, the books R900.
   * The day before, no reconciliation has that period end, so there is no bank side.
   */
  @Test
  void bankDifference_raisedAtAReconciliationPeriodEnd() {
    var account = books.freshAccount(false);
    var depositId = books.deposit(account, books.newClient("bank"), "900.00");
    var statement =
        importStatement(account, "tis-bank.csv", "01/03/2026,Deposit received,900.00,1000.00,TB-1");
    books.as(
        memberId,
        () -> {
          reconciliationService.manualMatch(statement.lines().getFirst().id(), depositId);
          reconciliationService.createReconciliation(account, MARCH_END, statement.id());
        });

    var report = check(books, memberId, account, MARCH_END, null);
    assertThat(report.bank()).isNotNull();
    assertThat(report.bank().bankBalance()).isEqualByComparingTo("1000.00");
    var flag = only(report, TrustIntegrityService.BANK_DIFFERENCE);
    assertThat(flag.severity()).isEqualTo(TrustIntegrityService.BLOCKING);
    assertThat(flag.amount()).isEqualByComparingTo("100.00");
    assertThat(flag.message())
        .isEqualTo(
            "Adjusted bank balance "
                + rand("1000.00")
                + " differs from the cashbook "
                + rand("900.00")
                + " by "
                + rand("100.00")
                + " as at 31 March 2026.");
    assertThat(report.balanced()).isFalse();

    var dayBefore = check(books, memberId, account, MARCH_END.minusDays(1), null);
    assertThat(dayBefore.bank()).isNull();
    assertThat(codes(dayBefore)).doesNotContain(TrustIntegrityService.BANK_DIFFERENCE);
    books.assertServicesAgree(account, MARCH_END);
  }

  // --- blocking (books that break an equation, their own tenant) -----------------------------

  /**
   * EQ-2: an unpaired cashbook-neutral leg puts the client ledgers R50 above the cashbook — a
   * blocking flag naming both totals and the difference. The check never refuses a posting: a
   * deposit on the unbalanced account is still recorded (decision 7).
   */
  @Test
  void booksOutOfBalance_isBlocking_namesBothTotals_andNeverRefusesAPosting() {
    var account = broken.freshAccount(false);
    var client = broken.newClient("unpaired");
    broken.deposit(account, client, "1000.00");
    broken.postLedgerRowDirect(
        account,
        "TRANSFER_IN",
        "50.00",
        LocalDate.of(2026, 3, 5),
        client.id(),
        client.matter(),
        null,
        null);

    var report = check(broken, brokenMemberId, account, MARCH_END, null);
    var flag = only(report, TrustIntegrityService.BOOKS_OUT_OF_BALANCE);
    assertThat(flag.severity()).isEqualTo(TrustIntegrityService.BLOCKING);
    assertThat(flag.amount()).isEqualByComparingTo("50.00");
    assertThat(flag.message())
        .isEqualTo(
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
    assertThat(report.balanced()).isFalse();

    var deposit = new String[1];
    broken.as(
        brokenMemberId,
        () ->
            deposit[0] =
                transactionService
                    .recordDeposit(
                        account,
                        new RecordDepositRequest(
                            client.id(),
                            client.matter(),
                            new BigDecimal("25.00"),
                            "TIS-AFTER",
                            "Deposit on unbalanced books",
                            LocalDate.of(2026, 3, 6)))
                    .status());
    assertThat(deposit[0]).as("a posting is never refused by a flag").isEqualTo("RECORDED");
    assertThat(codes(check(broken, brokenMemberId, account, null, null)))
        .contains(TrustIntegrityService.BOOKS_OUT_OF_BALANCE);
  }

  /**
   * EQ-4: an open item recorded as reversed disagrees with its ledger (as at today); a future-dated
   * held item, whose balance is zero today, is not flagged (D-6).
   */
  @Test
  void unidentifiedItemsMismatch_isBlocking() {
    var account = broken.freshAccount(false);
    var rowId = broken.unidentifiedDepositDirect(account, "300.00", LocalDate.of(2026, 3, 10));
    assertThat(codes(check(broken, brokenMemberId, account, null, null)))
        .as("EQ-4 holds")
        .isEmpty();
    var itemId =
        broken.read(
            () -> transactionRepository.findById(rowId).orElseThrow().getUnidentifiedDepositId());
    broken.setItemStatusDirect(itemId, "REVERSED");

    var report = check(broken, brokenMemberId, account, null, null);
    var flag = only(report, TrustIntegrityService.UNIDENTIFIED_ITEMS_MISMATCH);
    assertThat(flag.severity()).isEqualTo(TrustIntegrityService.BLOCKING);
    assertThat(flag.message())
        .isEqualTo(
            "Unidentified deposit \"Trust books statement line 1\" of "
                + rand("300.00")
                + " is recorded as reversed but its ledger rows total "
                + rand("300.00")
                + " as at "
                + broken.today().format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
                + ".");
    assertThat(flag.subjects()).singleElement().extracting("id").isEqualTo(itemId);
    assertThat(report.balanced()).isFalse();
    assertThat(codes(check(broken, brokenMemberId, account, LocalDate.of(2026, 3, 31), null)))
        .as("rule (d) applies as at today only")
        .doesNotContain(TrustIntegrityService.UNIDENTIFIED_ITEMS_MISMATCH);

    var future = broken.freshAccount(false);
    broken.unidentifiedDepositDirect(future, "75.00", broken.today().plusDays(10));
    assertThat(codes(check(broken, brokenMemberId, future, null, null)))
        .as("a held item received after the date")
        .doesNotContain(TrustIntegrityService.UNIDENTIFIED_ITEMS_MISMATCH);
  }

  /**
   * EQ-5: an investment's record says R250 but its rows total R200 — blocking, naming the
   * investment; a tied investment raises nothing.
   */
  @Test
  void investmentBalanceMismatch_isBlocking() {
    var tied = books.freshAccount(false);
    var tiedClient = books.newClient("tied investment");
    placeDirect(books, tied, tiedClient, "200.00");
    assertThat(codes(check(books, memberId, tied, null, null))).isEmpty();
    books.assertServicesAgree(tied);

    var account = broken.freshAccount(false);
    var client = broken.newClient("investment");
    var investment = placeDirect(broken, account, client, "200.00");
    broken.setInvestmentPrincipalDirect(investment, "250.00");

    var report = check(broken, brokenMemberId, account, null, null);
    var flag = only(report, TrustIntegrityService.INVESTMENT_BALANCE_MISMATCH);
    assertThat(flag.severity()).isEqualTo(TrustIntegrityService.BLOCKING);
    assertThat(flag.amount()).isEqualByComparingTo("50.00");
    assertThat(flag.subjects())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.kind()).isEqualTo(TrustIntegrityService.SUBJECT_INVESTMENT);
              assertThat(s.id()).isEqualTo(investment);
              assertThat(s.amount()).isEqualByComparingTo("200.00");
            });
    assertThat(flag.message()).contains(rand("200.00")).contains(rand("250.00"));
    assertThat(report.balanced()).isFalse();
  }

  /**
   * EQ-3: a section 86(3) receipt with no placement on another account balances its own account (no
   * per-account flag) but not the firm — a firm-level blocking flag naming the account.
   */
  @Test
  void interAccountNotZero_isFirmLevelOnly() {
    var account = broken.freshAccount(false);
    var investment = broken.investmentDirect(account, account, null, "300.00");
    var row =
        broken.postLedgerRowDirect(
            account,
            "FIRM_INV_RECEIVE",
            "300.00",
            LocalDate.of(2026, 3, 8),
            null,
            null,
            null,
            investment);
    broken.linkPlacementDirect(investment, row);

    assertThat(codes(check(broken, brokenMemberId, account, null, null)))
        .doesNotContain(TrustIntegrityService.INTER_ACCOUNT_NOT_ZERO);
    var firm = checkFirm(broken, brokenMemberId);
    assertThat(firm.balanced()).isFalse();
    var flag =
        firm.flags().stream()
            .filter(f -> f.code().equals(TrustIntegrityService.INTER_ACCOUNT_NOT_ZERO))
            .findFirst()
            .orElseThrow();
    assertThat(flag.severity()).isEqualTo(TrustIntegrityService.BLOCKING);
    assertThat(flag.subjects()).extracting("id").contains(account);
  }

  // --- the firm report -------------------------------------------------------------------------

  /**
   * The firm report's totals are {@code TrustBooksService.firmPosition}'s, each account's report
   * carries its position, and Cashbook = Client ledgers + other trust creditors + Invested (the
   * inter-account terms cancel, EQ-3) — the four firm-wide tiles (ruling D-1).
   */
  @Test
  void checkFirm_totalsEqualFirmPosition_andEachAccountReport() {
    var account = books.freshAccount(false);
    books.deposit(account, books.newClient("firm"), "700.00");
    books.postNonClientDirect(account, "FUND_INTEREST", "30.00", LocalDate.of(2026, 3, 9));

    var firm = checkFirm(books, memberId);
    var position = new TrustBooksService.FirmTrustPosition[1];
    books.as(memberId, () -> position[0] = trustBooksService.firmPosition(books.today()));
    assertThat(firm.asAt()).isEqualTo(books.today());
    assertThat(firm.cashbook()).isEqualByComparingTo(position[0].cashbook());
    assertThat(firm.clientLedgers()).isEqualByComparingTo(position[0].clientLedgers());
    assertThat(firm.investmentCreditors()).isEqualByComparingTo(position[0].investmentCreditors());
    assertThat(firm.fundLedger()).isEqualByComparingTo(position[0].fundLedger());
    assertThat(firm.unidentifiedDeposits())
        .isEqualByComparingTo(position[0].unidentifiedDeposits());
    assertThat(firm.interAccount()).isEqualByComparingTo("0");
    assertThat(firm.creditorTotal()).isEqualByComparingTo(position[0].creditorTotal());
    assertThat(firm.difference()).isEqualByComparingTo("0");
    assertThat(firm.flags()).isEmpty();
    assertThat(firm.balanced()).as("the harness tenant has no blocking flag today").isTrue();
    assertThat(firm.accounts())
        .extracting(TrustIntegrityReport::trustAccountId)
        .containsExactlyElementsOf(
            position[0].accounts().stream()
                .map(TrustBooksService.TrustPosition::trustAccountId)
                .toList());
    assertThat(firm.accounts())
        .filteredOn(r -> r.trustAccountId().equals(account))
        .singleElement()
        .satisfies(r -> assertThat(r.position().cashbook()).isEqualByComparingTo("730.00"));
    var otherCreditors =
        firm.fundLedger().add(firm.unidentifiedDeposits()).add(firm.otherNonClient());
    assertThat(firm.cashbook())
        .isEqualByComparingTo(
            firm.clientLedgers().add(otherCreditors).add(firm.investmentCreditors()));
  }

  // --- endpoints ------------------------------------------------------------------------------

  /** {@code VIEW_TRUST}, the module, 404 on an unknown account, {@code asAt} absent = today. */
  @Test
  void endpoints_requireViewTrust_andDefaultAsAtToToday() throws Exception {
    var account = books.freshAccount(false);
    books.deposit(account, books.newClient("endpoint"), "100.00");
    var owner = TestJwtFactory.ownerJwt(ORG_ID, OWNER);

    mockMvc
        .perform(get("/api/trust-accounts/{id}/integrity", account).with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.trustAccountId").value(account.toString()))
        .andExpect(jsonPath("$.asAt").value(books.today().toString()))
        .andExpect(jsonPath("$.balanced").value(true))
        .andExpect(jsonPath("$.position.cashbook").value(100.00))
        .andExpect(jsonPath("$.bank").doesNotExist())
        .andExpect(jsonPath("$.flags").isArray());
    mockMvc
        .perform(
            get("/api/trust-accounts/{id}/integrity", account)
                .param("asAt", "2026-02-28")
                .with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asAt").value("2026-02-28"))
        .andExpect(jsonPath("$.position.cashbook").value(0));
    mockMvc
        .perform(get("/api/trust-accounts/{id}/integrity", UUID.randomUUID()).with(owner))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/api/trust-integrity").with(owner))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.asAt").value(books.today().toString()))
        .andExpect(jsonPath("$.accounts[?(@.trustAccountId == '%s')]".formatted(account)).exists());

    var noTrust = TestJwtFactory.memberJwt(ORG_ID, NO_TRUST);
    mockMvc
        .perform(get("/api/trust-accounts/{id}/integrity", account).with(noTrust))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/trust-integrity").with(noTrust)).andExpect(status().isForbidden());

    var noModule = TestJwtFactory.ownerJwt(NO_MODULE_ORG_ID, NO_MODULE_OWNER);
    mockMvc.perform(get("/api/trust-integrity").with(noModule)).andExpect(status().isForbidden());
    mockMvc
        .perform(get("/api/trust-accounts/{id}/integrity", account).with(noModule))
        .andExpect(status().isForbidden());
  }

  // --- open question #9 -------------------------------------------------------------------------

  /**
   * The firm-wide check costs a fixed number of statements per account (no N+1 inside an account):
   * measured for 1, 3 and 10 accounts each holding a deposit, printed for the PR body, asserted
   * only for its linear bound.
   */
  @Test
  void checkFirm_preparedStatements_growLinearly_forThreeAndTenAccounts() {
    Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    assertThat(stats.isStatisticsEnabled())
        .as("hibernate.generate_statistics must be on in application-test.yml")
        .isTrue();
    var client = counted.newClient("count");
    long one = 0;
    long three = 0;
    long ten = 0;
    for (int n = 1; n <= 10; n++) {
      counted.deposit(counted.freshAccount(false), client, "100.00");
      if (n == 1 || n == 3 || n == 10) {
        long statements = countedCheckFirm(stats);
        System.out.printf(
            "%n[TrustIntegrityServiceIntegrationTest] accounts=%d prepared statements=%d%n",
            n, statements);
        if (n == 1) {
          one = statements;
        } else if (n == 3) {
          three = statements;
        } else {
          ten = statements;
        }
      }
    }
    long perAccount = (three - one) / 2;
    System.out.printf(
        "%n[TrustIntegrityServiceIntegrationTest] per account=%d (1→3), 3→10 delta=%d%n",
        perAccount, ten - three);
    assertThat(perAccount).as("statements per account").isPositive().isLessThanOrEqualTo(12);
    assertThat(ten).as("10 accounts").isLessThanOrEqualTo(1 + 12 * 10 + 5);
    assertThat(ten - three).as("3 → 10 accounts grows linearly").isEqualTo(7 * perAccount);
  }

  // --- helpers -----------------------------------------------------------------------------------

  private long countedCheckFirm(Statistics stats) {
    long[] statements = new long[1];
    counted.as(
        countedMemberId,
        () -> {
          integrityService.checkFirm(null); // warm-up (plan caches) — not counted
          stats.clear();
          integrityService.checkFirm(null);
          statements[0] = stats.getPrepareStatementCount();
        });
    return statements[0];
  }

  /** A section 86(4) investment of {@code principal} held in {@code account}, tied (EQ-5). */
  private static UUID placeDirect(
      TrustBooksFixture.Tenant tenant, UUID account, TrustBooksFixture.Client client, String p) {
    var investment = tenant.investmentDirect(account, account, client, p);
    var received =
        tenant.postLedgerRowDirect(
            account,
            "INV_RECEIVE",
            p,
            LocalDate.of(2026, 3, 3),
            client.id(),
            client.matter(),
            null,
            investment);
    tenant.linkPlacementDirect(investment, received);
    return investment;
  }

  private void recordCharge(UUID lineId, boolean recoverable) {
    books.as(
        memberId,
        () ->
            statementLinePostingService.recordFromLine(
                lineId,
                new RecordFromLineRequest(
                    StatementLineClassification.BANK_CHARGE,
                    recoverable,
                    BigDecimal.ZERO,
                    null,
                    null,
                    null,
                    null,
                    null)));
  }

  private BankStatementResponse importStatement(UUID account, String fileName, String line) {
    var csv =
        """
        FNB Trust Account Statement - Account 62500000000
        Date,Description,Amount,Balance,Reference
        %s
        """
            .formatted(line);
    var file =
        new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    var statement = new BankStatementResponse[1];
    books.as(
        memberId, () -> statement[0] = reconciliationService.importBankStatement(account, file));
    return statement[0];
  }

  private TrustIntegrityReport check(
      TrustBooksFixture.Tenant tenant,
      UUID member,
      UUID account,
      LocalDate asAt,
      UUID statementId) {
    var report = new TrustIntegrityReport[1];
    tenant.as(member, () -> report[0] = integrityService.check(account, asAt, statementId));
    return report[0];
  }

  private FirmIntegrityReport checkFirm(TrustBooksFixture.Tenant tenant, UUID member) {
    var report = new FirmIntegrityReport[1];
    tenant.as(member, () -> report[0] = integrityService.checkFirm(null));
    return report[0];
  }

  private static List<String> codes(TrustIntegrityReport report) {
    return report.flags().stream().map(IntegrityFlag::code).toList();
  }

  /** The report's only flag of {@code code} (fails when absent). */
  private static IntegrityFlag only(TrustIntegrityReport report, String code) {
    var matching = report.flags().stream().filter(f -> f.code().equals(code)).toList();
    assertThat(matching).as("flag %s in %s", code, codes(report)).hasSize(1);
    return matching.getFirst();
  }

  private static String rand(String amount) {
    return VariableFormatter.formatCurrencyText(new BigDecimal(amount), ZAR);
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
