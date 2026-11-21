package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.template.VariableFormatter;
import io.b2mash.b2b.b2bstrawman.testutil.BooksSnapshot;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture.StatementLine;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.InvestmentBasis;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.TrustAccountService.CreateTrustAccountRequest;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustBooksService.BankPosition;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedger;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransaction;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Phase 92 slice 647A (task 647.5; architecture §16.3.5, §16.9.2 paths 12 and 13; ADR-380,
 * ADR-381): record from statement. An unmatched statement line is recorded as {@code
 * FUND_INTEREST}, {@code FUND_SWEEP}, {@code BANK_CHARGE} or {@code CHARGE_REFUND}: one posted
 * {@code NON_CLIENT} row on the account's Fund interest-and-charges ledger, dated the line's date,
 * linked to the line, which becomes {@code RECORDED}. Unmatching a recorded line reverses what it
 * created with a contra dated the line's date. Every posting step is asserted through the {@link
 * TrustBooksHarness} ({@code assertMoves}); the class ends with {@code assertFirmBalanced(today)}.
 *
 * <p>647B adds {@code UNIDENTIFIED_DEPOSIT} on the same endpoint (a credit line, on the account's
 * unidentified-deposits ledger, opening an item — its own path 14 lives in {@code
 * UnidentifiedDepositIntegrationTest}); it is refused on an investment account. The "not yet
 * supported" case now uses 648B's {@code INVESTMENT_INTEREST}.
 *
 * <p>One tenant; every test uses a fresh trust account; HTTP status, titles and details are
 * asserted through MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatementLinePostingIntegrationTest {

  private static final String ORG_ID = "org_statement_line_posting";
  private static final String OWNER = "user_slp_owner";
  private static final String MEMBER = "user_slp_member";
  private static final String APPROVER = "user_slp_approver";
  private static final String RECORD_TITLE = "Cannot record statement line";
  private static final String FUND = "FUND_INTEREST_AND_CHARGES";
  private static final String FUND_INTEREST_LABEL =
      "Interest received for the Legal Practitioners' Fidelity Fund";

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private TrustBooksHarness harness;
  @Autowired private TrustTransactionRepository transactionRepository;
  @Autowired private BankStatementLineRepository lineRepository;
  @Autowired private BankStatementRepository statementRepository;
  @Autowired private TrustBooksService booksService;
  @Autowired private TrustAccountService trustAccountService;
  @Autowired private AuditEventRepository auditEventRepository;
  @Autowired private JdbcTemplate jdbc;

  private TrustBooksFixture.Tenant books;
  private UUID ownerId;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService
            .provisionTenant(ORG_ID, "Statement Line Posting Org", null)
            .schemaName();
    ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "slp_owner@test.com", "SLP Owner", "owner"));
    TestMemberHelper.syncMember(
        mockMvc, ORG_ID, MEMBER, "slp_member@test.com", "SLP Member", "member");
    // 648B: approves section 86(4) placements (single-approval mode refuses self-approval)
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, APPROVER, "slp_approver@test.com", "SLP Approver", "admin"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, ownerId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  /** A date ten days back: in the past, so a recorded line can also be unmatched. */
  private LocalDate pastDate() {
    return books.today().minusDays(10);
  }

  // --- path 12: each classification -------------------------------------------------------

  @Test
  void recordsFundInterest_fromACreditLine_dated_the_lines_date() throws Exception {
    var account = books.freshAccount(false);
    var date = pastDate();
    var line = books.unmatchedLine(account, date, "1234.56", "INT-OCT");

    var body = new String[1];
    moves(
        account,
        Expected.cashbookAndFund("1234.56"),
        () ->
            body[0] =
                record(line.lineId(), "{\"classification\":\"FUND_INTEREST\"}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.line.id").value(line.lineId().toString()))
                    .andExpect(jsonPath("$.line.matchStatus").value("RECORDED"))
                    .andExpect(jsonPath("$.line.classification").value("FUND_INTEREST"))
                    .andExpect(jsonPath("$.line.classificationLabel").value(FUND_INTEREST_LABEL))
                    .andExpect(jsonPath("$.line.suggestedClassification").isEmpty())
                    .andExpect(jsonPath("$.line.matchConfidence").isEmpty())
                    .andExpect(jsonPath("$.transactions.length()").value(1))
                    .andExpect(jsonPath("$.transactions[0].transactionType").value("FUND_INTEREST"))
                    .andExpect(jsonPath("$.transactions[0].typeLabel").value(FUND_INTEREST_LABEL))
                    .andExpect(jsonPath("$.transactions[0].ledger").value("NON_CLIENT"))
                    .andExpect(jsonPath("$.transactions[0].status").value("RECORDED"))
                    .andExpect(jsonPath("$.transactions[0].amount").value(1234.56))
                    .andExpect(jsonPath("$.transactions[0].transactionDate").value(date.toString()))
                    .andExpect(jsonPath("$.transactions[0].chargeRecoverable").isEmpty())
                    .andExpect(jsonPath("$.transactions[0].chargeVatAmount").isEmpty())
                    .andExpect(jsonPath("$.unidentifiedDepositId").isEmpty())
                    .andExpect(jsonPath("$.investmentInterestId").isEmpty())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());

    var rowId = UUID.fromString(JsonPath.read(body[0], "$.transactions[0].id"));
    assertThat(JsonPath.<String>read(body[0], "$.line.createdTransactionIds[0]"))
        .isEqualTo(rowId.toString());
    assertThat(JsonPath.<String>read(body[0], "$.line.trustTransactionId"))
        .isEqualTo(rowId.toString());

    var row = row(rowId);
    assertThat(row.getTransactionType()).isEqualTo("FUND_INTEREST");
    assertThat(row.getBankStatementLineId()).isEqualTo(line.lineId());
    assertThat(row.getCreditorAccountId()).isEqualTo(books.creditorAccount(account, FUND));
    assertThat(row.getCustomerId()).isNull();
    assertThat(row.getProjectId()).isNull();
    assertThat(row.getLedger()).isEqualTo(TrustLedger.NON_CLIENT);
    assertThat(row.getReference()).isEqualTo("INT-OCT");
    assertThat(row.getDescription()).isEqualTo("Trust books statement line 1");
    assertThat(row.getTransactionDate()).isEqualTo(date);
    assertThat(row.getChargeRecoverable()).isNull();
    assertThat(row.getChargeVatAmount()).isNull();

    var recorded = line(line.lineId());
    assertThat(recorded.getMatchStatus()).isEqualTo("RECORDED");
    assertThat(recorded.getClassification()).isEqualTo("FUND_INTEREST");
    assertThat(recorded.getTrustTransactionId()).isEqualTo(rowId);
    assertThat(recorded.getMatchConfidence()).isNull();
    assertThat(matchedCount(line)).isEqualTo(1);

    // Dated the line's date: not in the books the day before, in them on the day.
    assertThat(books.snapshotAsAt(account, date.minusDays(1)).fundLedger())
        .isEqualByComparingTo("0");
    assertThat(books.snapshotAsAt(account, date).fundLedger()).isEqualByComparingTo("1234.56");
    assertThat(books.snapshotAsAt(account, date).cashbook()).isEqualByComparingTo("1234.56");
  }

  @Test
  void recordsFundSweep_fromADebitLine_withTheStatementReference_andNoSufficiencyCheck()
      throws Exception {
    var account = books.freshAccount(false);
    var date = pastDate();
    var line = books.unmatchedLine(account, date, "-500.00", null);

    var rowId = recordAndMove(account, line, "{\"classification\":\"FUND_SWEEP\"}", "-500.00");

    assertThat(row(rowId).getReference())
        .isEqualTo("STMT-1-" + date.format(DateTimeFormatter.BASIC_ISO_DATE));
    // The Fund ledger was empty: a sweep drives it into debit, and nothing refuses it.
    var snapshot = snapshot(account);
    assertThat(snapshot.fundLedger()).isEqualByComparingTo("-500.00");
    assertThat(snapshot.cashbook()).isEqualByComparingTo("-500.00");
    assertBalanced(account);
  }

  @Test
  void recordsBankCharge_recoverable_withVat() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, pastDate(), "-57.50", "SERVICE FEE");

    var body = new String[1];
    moves(
        account,
        Expected.cashbookAndFund("-57.50"),
        () ->
            body[0] =
                record(
                        line.lineId(),
                        "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":true,"
                            + "\"chargeVatAmount\":7.50}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.line.classificationLabel").value("Bank charge"))
                    .andExpect(jsonPath("$.transactions[0].transactionType").value("BANK_CHARGE"))
                    .andExpect(jsonPath("$.transactions[0].direction").value("OUT"))
                    .andExpect(jsonPath("$.transactions[0].amount").value(57.5))
                    .andExpect(jsonPath("$.transactions[0].chargeRecoverable").value(true))
                    .andExpect(jsonPath("$.transactions[0].chargeVatAmount").value(7.5))
                    .andReturn()
                    .getResponse()
                    .getContentAsString());

    var row = row(UUID.fromString(JsonPath.read(body[0], "$.transactions[0].id")));
    assertThat(row.getChargeRecoverable()).isTrue();
    assertThat(row.getChargeVatAmount()).isEqualByComparingTo("7.50");
  }

  @Test
  void recordsBankCharge_notRecoverable_zeroVat() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, pastDate(), "-12.00", "CASH FEE");

    var rowId =
        recordAndMove(
            account,
            line,
            "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":false,"
                + "\"chargeVatAmount\":0}",
            "-12.00");

    var row = row(rowId);
    assertThat(row.getChargeRecoverable()).isFalse();
    assertThat(row.getChargeVatAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void recordsChargeRefund_fromACreditLine() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, pastDate(), "57.50", "FEE REFUND");

    var rowId = recordAndMove(account, line, "{\"classification\":\"CHARGE_REFUND\"}", "57.50");

    assertThat(row(rowId).getTransactionType()).isEqualTo("CHARGE_REFUND");
    assertThat(line(line.lineId()).getClassification()).isEqualTo("CHARGE_REFUND");
  }

  // --- path 13: the Fund ledger in debit --------------------------------------------------

  @Test
  void aChargeBeforeAnyInterest_drivesTheFundLedgerIntoDebit_andTheBooksStayBalanced()
      throws Exception {
    var account = books.freshAccount(false);
    var interestDate = pastDate();
    var chargeDate = interestDate.minusDays(5);
    var charge = books.unmatchedLine(account, chargeDate, "-57.50", "CHG-EARLY");
    var interestLine = books.addLine(charge.statementId(), 2, interestDate, "100.00", "INT-LATE");

    recordAndMove(
        account,
        charge,
        "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":true,\"chargeVatAmount\":7.50}",
        "-57.50");

    assertThat(snapshot(account).fundLedger()).isEqualByComparingTo("-57.50");
    assertBalanced(account);
    books.assertBalancedAsAt(account, chargeDate);
    assertThat(books.snapshotAsAt(account, chargeDate).fundLedger()).isEqualByComparingTo("-57.50");

    recordAndMove(
        account,
        new StatementLine(charge.statementId(), interestLine),
        "{\"classification\":\"FUND_INTEREST\"}",
        "100.00");

    assertThat(snapshot(account).fundLedger()).isEqualByComparingTo("42.50");
    books.assertBalancedAsAt(account, interestDate);
    assertThat(matchedCount(charge)).isEqualTo(2);
  }

  // --- path 12: unmatch reverses on the line's date ---------------------------------------

  @Test
  void aRecordedLine_reversesOnUnmatch_dated_the_lines_date() throws Exception {
    var account = books.freshAccount(false);
    var date = pastDate();
    var line = books.unmatchedLine(account, date, "-300.00", "LPFF-SWEEP");
    var beforeOnTheDay = books.snapshotAsAt(account, date);
    var rowId = recordAndMove(account, line, "{\"classification\":\"FUND_SWEEP\"}", "-300.00");

    moves(
        account,
        Expected.cashbookAndFund("300.00"),
        () -> unmatch(line.lineId()).andExpect(status().isOk()));

    var contras = reversalsOf(rowId);
    assertThat(contras).hasSize(1);
    var contra = contras.getFirst();
    assertThat(contra.getTransactionDate()).as("contra dated the line's date").isEqualTo(date);
    assertThat(contra.getStatus()).isEqualTo("RECORDED");
    assertThat(contra.getLedger()).isEqualTo(TrustLedger.NON_CLIENT);
    assertThat(contra.getCreditorAccountId()).isEqualTo(books.creditorAccount(account, FUND));
    assertThat(contra.getBankStatementLineId()).isNull();
    assertThat(contra.getChargeRecoverable()).isNull();

    var original = row(rowId);
    assertThat(original.getStatus()).isEqualTo("REVERSED");
    assertThat(original.getReversedById()).isEqualTo(contra.getId());
    assertThat(original.getBankStatementLineId()).as("original unlinked").isNull();

    var unmatched = line(line.lineId());
    assertThat(unmatched.getMatchStatus()).isEqualTo("UNMATCHED");
    assertThat(unmatched.getClassification()).isNull();
    assertThat(unmatched.getTrustTransactionId()).isNull();
    assertThat(unmatched.getSuggestedClassification()).isNull();
    assertThat(matchedCount(line)).isZero();

    // The pair nets to zero on the line's date: as at that date nothing changed.
    var afterOnTheDay = books.snapshotAsAt(account, date);
    assertThat(afterOnTheDay.cashbook()).isEqualByComparingTo(beforeOnTheDay.cashbook());
    assertThat(afterOnTheDay.fundLedger()).isEqualByComparingTo(beforeOnTheDay.fundLedger());
    books.assertBalancedAsAt(account, date);
  }

  @Test
  void aRecordedChargeRefund_reversesOnUnmatch_dated_the_lines_date() throws Exception {
    var account = books.freshAccount(false);
    var date = pastDate();
    var line = books.unmatchedLine(account, date, "57.50", "FEE REFUND");
    var rowId = recordAndMove(account, line, "{\"classification\":\"CHARGE_REFUND\"}", "57.50");

    moves(
        account,
        Expected.cashbookAndFund("-57.50"),
        () -> unmatch(line.lineId()).andExpect(status().isOk()));

    var contras = reversalsOf(rowId);
    assertThat(contras).hasSize(1);
    assertThat(contras.getFirst().getTransactionDate()).isEqualTo(date);
    assertThat(contras.getFirst().getCreditorAccountId())
        .isEqualTo(books.creditorAccount(account, FUND));
    assertThat(row(rowId).getStatus()).isEqualTo("REVERSED");
    assertThat(row(rowId).getBankStatementLineId()).isNull();
    assertThat(line(line.lineId()).getMatchStatus()).isEqualTo("UNMATCHED");
    assertThat(line(line.lineId()).getClassification()).isNull();
    assertThat(matchedCount(line)).isZero();
    books.assertBalancedAsAt(account, date);
  }

  @Test
  void afterUnmatch_theCancellingPairIsNotAnOutstandingItem() throws Exception {
    var account = books.freshAccount(false);
    var date = pastDate();
    var line = books.unmatchedLine(account, date, "1234.56", "INT-PAIR");
    var dates = List.of(date, date.plusDays(1), books.today());
    var before = dates.stream().map(asAt -> bankPosition(account, line, asAt)).toList();

    recordAndMove(account, line, "{\"classification\":\"FUND_INTEREST\"}", "1234.56");
    moves(
        account,
        Expected.cashbookAndFund("-1234.56"),
        () -> unmatch(line.lineId()).andExpect(status().isOk()));

    for (int i = 0; i < dates.size(); i++) {
      var after = bankPosition(account, line, dates.get(i));
      assertThat(after.outstandingDeposits())
          .as("outstanding deposits as at %s", dates.get(i))
          .isEqualByComparingTo(before.get(i).outstandingDeposits());
      assertThat(after.outstandingPayments())
          .as("outstanding payments as at %s", dates.get(i))
          .isEqualByComparingTo(before.get(i).outstandingPayments());
    }
  }

  @Test
  void unmatch_afterTheRowWasReversedGenerically_resetsTheLine_andMovesNothing() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, pastDate(), "-300.00", "SWEEP-GEN");
    var rowId = recordAndMove(account, line, "{\"classification\":\"FUND_SWEEP\"}", "-300.00");

    // FUND_SWEEP reverses IMMEDIATE on the generic endpoint; the contra carries the Fund ledger
    // (HF-1 through the real record path).
    var generic = books.reverse(account, rowId, Expected.cashbookAndFund("300.00"));
    assertThat(generic.creditorAccountId()).isEqualTo(books.creditorAccount(account, FUND));

    moves(account, Expected.none(), () -> unmatch(line.lineId()).andExpect(status().isOk()));

    assertThat(line(line.lineId()).getMatchStatus()).isEqualTo("UNMATCHED");
    assertThat(line(line.lineId()).getClassification()).isNull();
    assertThat(row(rowId).getBankStatementLineId()).isNull();
    assertThat(reversalsOf(rowId)).as("no second contra").hasSize(1);
    assertThat(matchedCount(line)).isZero();
  }

  // --- refusals: 400, and nothing moves ---------------------------------------------------

  @Test
  void aLineAlreadyMatched_isRefused() throws Exception {
    var account = books.freshAccount(false);
    var date = pastDate();
    var interestId = books.postNonClientDirect(account, "FUND_INTEREST", "10.00", date);
    var lineId = books.matchedLineFor(account, interestId, date);

    assertRecordRefused(
        account,
        lineId,
        "{\"classification\":\"FUND_INTEREST\"}",
        RECORD_TITLE,
        "This statement line is already matched, recorded or excluded");
    assertThat(line(lineId).getMatchStatus()).isEqualTo("MANUALLY_MATCHED");
  }

  @Test
  void aLineAlreadyRecorded_isRefused() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, pastDate(), "25.00", "INT-TWICE");
    var rowId = recordAndMove(account, line, "{\"classification\":\"FUND_INTEREST\"}", "25.00");

    assertRecordRefused(
        account,
        line.lineId(),
        "{\"classification\":\"CHARGE_REFUND\"}",
        RECORD_TITLE,
        "This statement line is already matched, recorded or excluded");
    assertThat(line(line.lineId()).getTrustTransactionId()).isEqualTo(rowId);
    assertThat(line(line.lineId()).getClassification()).isEqualTo("FUND_INTEREST");
    assertThat(matchedCount(line)).isEqualTo(1);
  }

  @Test
  void anExcludedLine_isRefused() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, pastDate(), "9.00", "EXCL");
    mockMvc
        .perform(
            post("/api/bank-statement-lines/{lineId}/exclude", line.lineId())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Bank error\"}"))
        .andExpect(status().isOk());

    assertRecordRefused(
        account,
        line.lineId(),
        "{\"classification\":\"FUND_INTEREST\"}",
        RECORD_TITLE,
        "This statement line is already matched, recorded or excluded");
    assertThat(line(line.lineId()).getMatchStatus()).isEqualTo("EXCLUDED");
  }

  @Test
  void aClassificationAgainstTheLinesSign_isRefused_inBothDirections() throws Exception {
    var account = books.freshAccount(false);
    var debit = books.unmatchedLine(account, pastDate(), "-20.00", "DR");
    var credit = books.unmatchedLine(account, pastDate(), "20.00", "CR");

    assertRecordRefused(
        account,
        debit.lineId(),
        "{\"classification\":\"FUND_INTEREST\"}",
        "Classification does not match the line",
        FUND_INTEREST_LABEL + " is recorded from a credit line; this line is a debit");
    assertRecordRefused(
        account,
        credit.lineId(),
        "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":true,\"chargeVatAmount\":0}",
        "Classification does not match the line",
        "Bank charge is recorded from a debit line; this line is a credit");
  }

  @Test
  void aBankChargeWithoutOrWithBadDetail_andDetailOnANonCharge_areRefused() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, pastDate(), "-57.50", "CHG");
    var missing = "A bank charge needs whether it is recoverable and its VAT amount";
    var vatRange = "The VAT amount must be zero or more and less than the charge";

    assertRecordRefused(
        account,
        line.lineId(),
        "{\"classification\":\"BANK_CHARGE\",\"chargeVatAmount\":7.50}",
        RECORD_TITLE,
        missing);
    assertRecordRefused(
        account,
        line.lineId(),
        "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":true}",
        RECORD_TITLE,
        missing);
    assertRecordRefused(
        account,
        line.lineId(),
        "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":true,\"chargeVatAmount\":57.50}",
        RECORD_TITLE,
        vatRange);
    assertRecordRefused(
        account,
        line.lineId(),
        "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":true,\"chargeVatAmount\":-1}",
        RECORD_TITLE,
        vatRange);
    assertRecordRefused(
        account,
        line.lineId(),
        "{\"classification\":\"FUND_SWEEP\",\"chargeRecoverable\":true}",
        RECORD_TITLE,
        "Recoverable and VAT apply only to a bank charge");
    // More than two decimals would round to the charge itself in numeric(15,2) and break
    // chk_trust_txn_charge (a 500): refused as a validation error before the service runs.
    moves(
        account,
        Expected.none(),
        () ->
            record(
                    line.lineId(),
                    "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":true,"
                        + "\"chargeVatAmount\":7.499}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("chargeVatAmount"))
                .andExpect(
                    jsonPath("$.fieldErrors[0].message")
                        .value("The VAT amount has at most two decimal places")));
    assertThat(line(line.lineId()).getMatchStatus()).isEqualTo("UNMATCHED");
    assertThat(matchedCount(line)).isZero();
  }

  @Test
  void aLineOnAClosedAccount_isRefused() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, pastDate(), "15.00", "LATE");
    books.as(ownerId, () -> trustAccountService.closeTrustAccount(account));

    assertRecordRefused(
        account,
        line.lineId(),
        "{\"classification\":\"FUND_INTEREST\"}",
        RECORD_TITLE,
        "Trust account is closed");
  }

  /**
   * FU-6a (ruling F-647C): a stored line dated after today (imported before the import refused one)
   * is not recorded, whatever its classification — 400 naming the line's date; nothing moves and
   * the line stays {@code UNMATCHED}.
   */
  @Test
  void aLineDatedAfterToday_isRefused_forEveryClassification() throws Exception {
    var account = books.freshAccount(false);
    var tomorrow = books.today().plusDays(1);
    var credit = books.unmatchedLine(account, tomorrow, "40.00", "FUT-CR");
    var debitId = books.addLine(credit.statementId(), 2, tomorrow, "-6.50", "FUT-DR");
    var detail =
        "This statement line is dated "
            + VariableFormatter.formatDateText(tomorrow)
            + ", which is after today. Record it on or after that date.";

    for (var json :
        List.of(
            "{\"classification\":\"FUND_INTEREST\"}",
            "{\"classification\":\"CHARGE_REFUND\"}",
            "{\"classification\":\"UNIDENTIFIED_DEPOSIT\"}")) {
      assertRecordRefused(account, credit.lineId(), json, RECORD_TITLE, detail);
    }
    for (var json :
        List.of(
            "{\"classification\":\"FUND_SWEEP\"}",
            "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":false,"
                + "\"chargeVatAmount\":0}")) {
      assertRecordRefused(account, debitId, json, RECORD_TITLE, detail);
    }
    assertThat(line(credit.lineId()).getMatchStatus()).isEqualTo("UNMATCHED");
    assertThat(line(debitId).getMatchStatus()).isEqualTo("UNMATCHED");
    assertThat(matchedCount(credit)).isZero();
  }

  /**
   * FU-6a: section 86(4) interest from a line dated after today was already refused before FU-6a
   * (the entry is dated the line's date, and an entry date is never in the future —
   * TrustInvestmentService); pinned here so the record paths stay aligned.
   */
  @Test
  void investmentInterest_fromALineDatedAfterToday_isRefused() throws Exception {
    var held = s86_4Investment("10000.00");
    var line =
        books.unmatchedLine(
            held.investmentAccount(), books.today().plusDays(1), "95.00", "FUT-INT");

    assertRecordRefused(
        held.investmentAccount(),
        line.lineId(),
        interestJson(held.investmentId(), "NET", "100.00", null),
        "Cannot record investment interest",
        "The entry date cannot be in the future.");
    assertThat(line(line.lineId()).getMatchStatus()).isEqualTo("UNMATCHED");
  }

  /** FU-6a boundary: a line dated today is recorded, and can be unmatched the same day. */
  @Test
  void aLineDatedToday_isRecorded_andUnmatches() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, books.today(), "18.00", "TODAY-INT");
    recordAndMove(account, line, "{\"classification\":\"FUND_INTEREST\"}", "18.00");

    moves(
        account,
        Expected.cashbookAndFund("-18.00"),
        () -> unmatch(line.lineId()).andExpect(status().isOk()));
    assertThat(line(line.lineId()).getMatchStatus()).isEqualTo("UNMATCHED");
  }

  @Test
  void investmentInterestOffASection86_4Account_andAZeroLine_areRefused() throws Exception {
    // 648B: INVESTMENT_INTEREST is a real classification now — it is refused off the account that
    // holds the investment (the "not yet supported" Jackson refusal of 647A is gone).
    var held = s86_4Investment("1000.00");
    var account = books.freshAccount(false);
    var credit = books.unmatchedLine(account, pastDate(), "15.00", "UNKNOWN DEPOSIT");
    var zero = books.unmatchedLine(account, pastDate(), "0.00", "BALANCE B/F");

    assertRecordRefused(
        account,
        credit.lineId(),
        interestJson(held.investmentId(), "NET", "15.80", null),
        RECORD_TITLE,
        "This statement line is not on the investment account that holds the investment.");
    // No classification at all (@NotNull).
    moves(
        account,
        Expected.none(),
        () ->
            record(credit.lineId(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("1 field(s) have validation errors"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("classification")));
    assertRecordRefused(
        account,
        zero.lineId(),
        "{\"classification\":\"FUND_INTEREST\"}",
        RECORD_TITLE,
        "A zero-amount statement line cannot be recorded");
    assertThat(line(credit.lineId()).getMatchStatus()).isEqualTo("UNMATCHED");
  }

  // --- 648B: section 86(4) investment interest (§16.3.5, §16.3.8, ADR-379) -----------------

  @Test
  void fundInterest_onASection86_4InvestmentAccount_isRefused() throws Exception {
    // 647A found.md D1 (owner 648B): the line is routed to the classification that knows the
    // 95/5 split.
    var held = s86_4Investment("1000.00");
    var line = books.unmatchedLine(held.investmentAccount(), pastDate(), "50.00", "INT-S864");

    assertRecordRefused(
        held.investmentAccount(),
        line.lineId(),
        "{\"classification\":\"FUND_INTEREST\"}",
        RECORD_TITLE,
        "Interest on a section 86(4) investment is recorded against the investment — choose"
            + " Section 86(4) investment interest");
    assertThat(line(line.lineId()).getMatchStatus()).isEqualTo("UNMATCHED");
  }

  @Test
  void recordsInvestmentInterest_gross_fromACreditLine() throws Exception {
    var held = s86_4Investment("30000.00");
    var date = pastDate();
    var line = books.unmatchedLine(held.investmentAccount(), date, "400.00", "INT-GROSS");

    var body = new String[1];
    moves(
        held.investmentAccount(),
        new Expected(
            new BigDecimal("400.00"),
            BigDecimal.ZERO,
            new BigDecimal("380.00"),
            new BigDecimal("20.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO),
        () ->
            body[0] =
                record(line.lineId(), interestJson(held.investmentId(), "GROSS", null, null))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.line.matchStatus").value("RECORDED"))
                    .andExpect(jsonPath("$.line.classification").value("INVESTMENT_INTEREST"))
                    .andExpect(
                        jsonPath("$.line.classificationLabel")
                            .value("Section 86(4) investment interest"))
                    .andExpect(jsonPath("$.transactions.length()").value(2))
                    .andExpect(jsonPath("$.transactions[0].transactionType").value("INV_INTEREST"))
                    .andExpect(jsonPath("$.transactions[0].amount").value(380.00))
                    .andExpect(jsonPath("$.transactions[0].ledger").value("CLIENT_INVESTMENT"))
                    .andExpect(
                        jsonPath("$.transactions[1].transactionType").value("FUND_INV_SHARE"))
                    .andExpect(jsonPath("$.transactions[1].amount").value(20.00))
                    .andExpect(jsonPath("$.transactions[1].ledger").value("NON_CLIENT"))
                    .andExpect(jsonPath("$.unidentifiedDepositId").isEmpty())
                    .andExpect(jsonPath("$.investmentInterestId").isNotEmpty())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());

    var netId = UUID.fromString(JsonPath.read(body[0], "$.transactions[0].id"));
    var fundId = UUID.fromString(JsonPath.read(body[0], "$.transactions[1].id"));
    var interestId = UUID.fromString(JsonPath.read(body[0], "$.investmentInterestId"));
    for (var row : List.of(row(netId), row(fundId))) {
      assertThat(row.getBankStatementLineId()).isEqualTo(line.lineId());
      assertThat(row.getInvestmentId()).isEqualTo(held.investmentId());
      assertThat(row.getTransactionDate()).isEqualTo(date);
      assertThat(row.getReference()).isEqualTo("INT-GROSS");
      assertThat(row.getDescription()).isEqualTo("Trust books statement line 1");
      assertThat(row.getStatus()).isEqualTo("RECORDED");
    }
    assertThat(row(netId).getCustomerId()).isEqualTo(held.client().id());
    assertThat(row(netId).getProjectId()).isEqualTo(held.client().matter());
    assertThat(row(fundId).getCreditorAccountId())
        .isEqualTo(books.creditorAccount(held.investmentAccount(), FUND));
    assertThat(entry(interestId))
        .isEqualTo(
            "%s|%s|400.00|20.00|380.00|GROSS|%s|%s|%s|POSTED"
                .formatted(held.investmentId(), date, netId, fundId, line.lineId()));
    var recorded = line(line.lineId());
    assertThat(recorded.getTrustTransactionId()).isEqualTo(netId);
    assertThat(recorded.getClassification()).isEqualTo("INVESTMENT_INTEREST");
    assertThat(matchedCount(line)).isEqualTo(1);

    // The later 5% debit leaves as an ordinary transfer to the Fund: the Fund ledger is back at 0.
    var sweep = books.addLine(line.statementId(), 2, date, "-20.00", "LPFF-5");
    moves(
        held.investmentAccount(),
        Expected.cashbookAndFund("-20.00"),
        () -> record(sweep, "{\"classification\":\"FUND_SWEEP\"}").andExpect(status().isCreated()));
    assertThat(snapshot(held.investmentAccount()).fundLedger()).isEqualByComparingTo("0");
    assertThat(snapshot(held.investmentAccount()).investmentCreditors())
        .isEqualByComparingTo("30380.00");

    var lineAudit = audit("bank_statement_line", line.lineId(), "bank_statement_line.recorded");
    assertThat(lineAudit.getDetails())
        .containsEntry("classification", "INVESTMENT_INTEREST")
        .containsEntry("amount", "400.00")
        .containsEntry("transaction_ids", List.of(netId.toString(), fundId.toString()));
    assertThat(audit("trust_transaction", netId, "trust_inv_interest.recorded").getDetails())
        .containsEntry("amount", "380.00")
        .containsEntry("investment_id", held.investmentId().toString())
        .doesNotContainKey("creditor_account_id");
    assertThat(audit("trust_transaction", fundId, "trust_fund_inv_share.recorded").getDetails())
        .containsEntry("amount", "20.00")
        .containsEntry(
            "creditor_account_id",
            books.creditorAccount(held.investmentAccount(), FUND).toString());
  }

  @Test
  void recordsInvestmentInterest_net_fromACreditLine() throws Exception {
    var held = s86_4Investment("30000.00");
    var date = pastDate();
    var line = books.unmatchedLine(held.investmentAccount(), date, "95.00", null);

    var body = new String[1];
    moves(
        held.investmentAccount(),
        Expected.cashbookAndInvestment("95.00"),
        () ->
            body[0] =
                record(line.lineId(), interestJson(held.investmentId(), "NET", "100.00", null))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.transactions.length()").value(1))
                    .andExpect(jsonPath("$.transactions[0].transactionType").value("INV_INTEREST"))
                    .andExpect(jsonPath("$.transactions[0].amount").value(95.00))
                    .andReturn()
                    .getResponse()
                    .getContentAsString());

    var netId = UUID.fromString(JsonPath.read(body[0], "$.transactions[0].id"));
    var interestId = UUID.fromString(JsonPath.read(body[0], "$.investmentInterestId"));
    assertThat(entry(interestId))
        .isEqualTo(
            "%s|%s|100.00|5.00|95.00|NET|%s|NULL|%s|POSTED"
                .formatted(held.investmentId(), date, netId, line.lineId()));
    assertThat(row(netId).getReference())
        .isEqualTo("STMT-1-" + date.format(DateTimeFormatter.BASIC_ISO_DATE));

    // A net line's amount is the client's 95%: a gross that does not net to it is refused
    var other = books.addLine(line.statementId(), 2, date, "95.00", "INT-NET-2");
    assertRecordRefused(
        held.investmentAccount(),
        other,
        interestJson(held.investmentId(), "NET", "120.00", null),
        RECORD_TITLE,
        "The statement line is R95,00; gross R120,00 less the Fund's 5% (R6,00) is R114,00.");
  }

  @Test
  void unmatch_ofAnInvestmentInterestLine_reversesTheEntry_andIsRefusedOnceWithdrawn()
      throws Exception {
    var held = s86_4Investment("2000.00");
    var date = pastDate();
    var line = books.unmatchedLine(held.investmentAccount(), date, "95.00", "INT-UNDO");
    var interestId = recordInterestLine(held, line.lineId(), "95.00", "100.00");

    moves(
        held.investmentAccount(),
        Expected.cashbookAndInvestment("-95.00"),
        () -> unmatch(line.lineId()).andExpect(status().isOk()));
    assertThat(entryStatus(interestId)).isEqualTo("REVERSED");
    assertThat(line(line.lineId()).getMatchStatus()).isEqualTo("UNMATCHED");
    assertThat(matchedCount(line)).isZero();
    assertThat(
            audit("trust_investment", held.investmentId(), "trust_investment.interest_reversed")
                .getDetails())
        .containsEntry("interest_id", interestId.toString())
        .containsEntry("net", "95.00");

    // Recorded again, then the investment is withdrawn with the interest: the line stays recorded
    var again = recordInterestLine(held, line.lineId(), "95.00", "100.00");
    books.withdraw(
        held.funding(),
        Expected.cashbookAndClient("2095.00"),
        held.investmentAccount(),
        Expected.cashbookAndInvestment("-2095.00"),
        held.investmentId(),
        books.today());
    moves(
        held.investmentAccount(),
        Expected.none(),
        () ->
            unmatch(line.lineId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Cannot unmatch statement line"))
                .andExpect(
                    jsonPath("$.detail")
                        .value(
                            "The investment has been withdrawn with this interest; its interest"
                                + " entries can no longer be reversed.")));
    assertThat(entryStatus(again)).isEqualTo("POSTED");
    assertThat(line(line.lineId()).getMatchStatus()).isEqualTo("RECORDED");
  }

  @Test
  void import_suggestsInvestmentInterest_onASection86_4Account() throws Exception {
    // 647C found.md #1 (owner 648B): the parser suggests FUND_INTEREST for an interest credit; the
    // import maps it for a section 86(4) investment account, where the Fund classification is
    // refused. A section 86(3) account keeps FUND_INTEREST.
    var held = s86_4Investment("1000.00");
    assertThat(importedSuggestions(held.investmentAccount()))
        .containsExactly("INVESTMENT_INTEREST", "NULL");
    assertThat(importedSuggestions(investmentAccount())).containsExactly("FUND_INTEREST", "NULL");
  }

  // --- 647B: an unidentified deposit on the shared endpoint ---------------------------------

  @Test
  void recordsUnidentifiedDeposit_fromACreditLine_openingAnItem() throws Exception {
    var account = books.freshAccount(false);
    var date = pastDate();
    var line = books.unmatchedLine(account, date, "300.00", "UNKNOWN-CR");
    books.addLine(line.statementId(), 2, date, "11.00", "OTHER");

    var body = new String[1];
    moves(
        account,
        Expected.cashbookAndUnidentified("300.00"),
        () ->
            body[0] =
                record(line.lineId(), "{\"classification\":\"UNIDENTIFIED_DEPOSIT\"}")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.line.matchStatus").value("RECORDED"))
                    .andExpect(jsonPath("$.line.classification").value("UNIDENTIFIED_DEPOSIT"))
                    .andExpect(jsonPath("$.line.classificationLabel").value("Unidentified deposit"))
                    .andExpect(
                        jsonPath("$.transactions[0].transactionType").value("UNIDENTIFIED_DEPOSIT"))
                    .andExpect(
                        jsonPath("$.transactions[0].typeLabel").value("Unidentified deposit"))
                    .andExpect(jsonPath("$.transactions[0].ledger").value("NON_CLIENT"))
                    .andExpect(jsonPath("$.unidentifiedDepositId").isNotEmpty())
                    .andExpect(jsonPath("$.investmentInterestId").isEmpty())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());

    var rowId = UUID.fromString(JsonPath.read(body[0], "$.transactions[0].id"));
    var row = row(rowId);
    assertThat(row.getCreditorAccountId())
        .isEqualTo(books.creditorAccount(account, "UNIDENTIFIED_DEPOSITS"));
    assertThat(row.getUnidentifiedDepositId())
        .isEqualTo(UUID.fromString(JsonPath.read(body[0], "$.unidentifiedDepositId")));
    assertThat(matchedCount(line)).isEqualTo(1);

    mockMvc
        .perform(
            get("/api/bank-statements/{statementId}", line.statementId())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matchedCount").value(1))
        .andExpect(jsonPath("$.lines[0].matchStatus").value("RECORDED"))
        .andExpect(jsonPath("$.lines[0].classification").value("UNIDENTIFIED_DEPOSIT"))
        .andExpect(jsonPath("$.lines[0].classificationLabel").value("Unidentified deposit"))
        .andExpect(jsonPath("$.lines[0].createdTransactionIds[0]").value(rowId.toString()))
        .andExpect(jsonPath("$.lines[1].matchStatus").value("UNMATCHED"));
  }

  @Test
  void anUnidentifiedDeposit_onAnInvestmentAccount_orFromADebitLine_isRefused() throws Exception {
    var investmentAccount = investmentAccount();
    var credit = books.unmatchedLine(investmentAccount, pastDate(), "300.00", "INV-CR");
    assertRecordRefused(
        investmentAccount,
        credit.lineId(),
        "{\"classification\":\"UNIDENTIFIED_DEPOSIT\"}",
        RECORD_TITLE,
        "An unidentified deposit is recorded only on a trust banking account. On an investment"
            + " account, exclude the line and take it up with the bank.");
    assertThat(line(credit.lineId()).getMatchStatus()).isEqualTo("UNMATCHED");

    var account = books.freshAccount(false);
    var debit = books.unmatchedLine(account, pastDate(), "-300.00", "DEBIT");
    assertRecordRefused(
        account,
        debit.lineId(),
        "{\"classification\":\"UNIDENTIFIED_DEPOSIT\"}",
        "Classification does not match the line",
        "Unidentified deposit is recorded from a credit line; this line is a debit");
    assertThat(line(debit.lineId()).getMatchStatus()).isEqualTo("UNMATCHED");
  }

  // --- who, and no approval ---------------------------------------------------------------

  @Test
  void aMemberWithViewTrustOnly_isForbidden_andNothingMoves() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, pastDate(), "40.00", "INT-MEMBER");

    moves(
        account,
        Expected.none(),
        () ->
            mockMvc
                .perform(
                    post("/api/bank-statement-lines/{lineId}/record", line.lineId())
                        .with(TestJwtFactory.memberJwt(ORG_ID, MEMBER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classification\":\"FUND_INTEREST\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access denied"))
                .andExpect(
                    jsonPath("$.detail").value("Insufficient permissions for this operation")));
    assertThat(line(line.lineId()).getMatchStatus()).isEqualTo("UNMATCHED");

    recordAndMove(account, line, "{\"classification\":\"FUND_INTEREST\"}", "40.00");
    moves(
        account,
        Expected.none(),
        () ->
            mockMvc
                .perform(
                    post("/api/bank-statement-lines/{lineId}/unmatch", line.lineId())
                        .with(TestJwtFactory.memberJwt(ORG_ID, MEMBER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Access denied"))
                .andExpect(
                    jsonPath("$.detail").value("Insufficient permissions for this operation")));
    assertThat(line(line.lineId()).getMatchStatus()).isEqualTo("RECORDED");
  }

  @Test
  void noApprovalStep_theRowIsRecordedAtOnce_byTheCaller() throws Exception {
    var account = books.freshAccount(true);
    var line = books.unmatchedLine(account, pastDate(), "-80.00", "SWEEP-DUAL");

    var rowId = recordAndMove(account, line, "{\"classification\":\"FUND_SWEEP\"}", "-80.00");

    var row = row(rowId);
    assertThat(row.getStatus()).isEqualTo("RECORDED");
    assertThat(row.getPostedAt()).isNotNull();
    assertThat(row.getRecordedBy()).isEqualTo(ownerId);
    assertThat(row.getApprovedBy()).isNull();
    assertThat(
            books.read(
                () ->
                    transactionRepository.findAll().stream()
                        .filter(t -> account.equals(t.getTrustAccountId()))
                        .filter(t -> "AWAITING_APPROVAL".equals(t.getStatus()))
                        .count()))
        .isZero();
  }

  // --- the statement GET and the audit trail ----------------------------------------------

  @Test
  void statementGet_carriesTheLineClassificationAndCreatedIds() throws Exception {
    var account = books.freshAccount(false);
    var date = pastDate();
    var first = books.unmatchedLine(account, date, "-57.50", "CHG-GET");
    books.addLine(first.statementId(), 2, date, "11.00", "OTHER");
    var rowId =
        recordAndMove(
            account,
            first,
            "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":false,"
                + "\"chargeVatAmount\":7.50}",
            "-57.50");

    mockMvc
        .perform(
            get("/api/bank-statements/{statementId}", first.statementId())
                .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matchedCount").value(1))
        .andExpect(jsonPath("$.lines[0].matchStatus").value("RECORDED"))
        .andExpect(jsonPath("$.lines[0].classification").value("BANK_CHARGE"))
        .andExpect(jsonPath("$.lines[0].classificationLabel").value("Bank charge"))
        .andExpect(jsonPath("$.lines[0].suggestedClassification").isEmpty())
        .andExpect(jsonPath("$.lines[0].createdTransactionIds.length()").value(1))
        .andExpect(jsonPath("$.lines[0].createdTransactionIds[0]").value(rowId.toString()))
        .andExpect(jsonPath("$.lines[1].matchStatus").value("UNMATCHED"))
        .andExpect(jsonPath("$.lines[1].classification").isEmpty())
        .andExpect(jsonPath("$.lines[1].classificationLabel").isEmpty())
        .andExpect(jsonPath("$.lines[1].createdTransactionIds").isArray())
        .andExpect(jsonPath("$.lines[1].createdTransactionIds").isEmpty());
  }

  @Test
  void audit_recordAndUnmatch() throws Exception {
    var account = books.freshAccount(false);
    var line = books.unmatchedLine(account, pastDate(), "-57.50", "CHG-AUDIT");
    var rowId =
        recordAndMove(
            account,
            line,
            "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":false,"
                + "\"chargeVatAmount\":7.50,\"notes\":\"Cash fee\"}",
            "-57.50");
    moves(
        account,
        Expected.cashbookAndFund("57.50"),
        () -> unmatch(line.lineId()).andExpect(status().isOk()));

    var recorded = audit("bank_statement_line", line.lineId(), "bank_statement_line.recorded");
    assertThat(recorded.getDetails())
        .containsEntry("classification", "BANK_CHARGE")
        .containsEntry("amount", "57.50")
        .containsEntry("statement_id", line.statementId().toString())
        .containsEntry("transaction_ids", List.of(rowId.toString()))
        .containsEntry("notes", "Cash fee");
    assertThat(recorded.getActorId()).isEqualTo(ownerId);

    var charge = audit("trust_transaction", rowId, "trust_bank_charge.recorded");
    assertThat(charge.getDetails())
        .containsEntry("recoverable", "false")
        .containsEntry("vat_amount", "7.50")
        .containsEntry("amount", "57.50")
        .containsEntry("reference", "CHG-AUDIT")
        .containsEntry("bank_statement_line_id", line.lineId().toString())
        .containsEntry("creditor_account_id", books.creditorAccount(account, FUND).toString());

    var unmatched = audit("bank_statement_line", line.lineId(), "bank_statement_line.unmatched");
    assertThat(unmatched.getDetails())
        .containsEntry("classification", "BANK_CHARGE")
        .containsEntry("reversed_transaction_ids", List.of(rowId.toString()))
        .containsEntry("statement_id", line.statementId().toString());
  }

  // --- helpers ----------------------------------------------------------------------------

  /** A section 86(4) investment of {@code principal}, placed and approved (648A). */
  private record Held(
      UUID funding, UUID investmentAccount, TrustBooksFixture.Client client, UUID investmentId) {}

  private Held s86_4Investment(String principal) {
    var funding = books.freshAccount(false);
    var investmentAccount = books.freshInvestmentAccount(InvestmentBasis.CLIENT_INSTRUCTION);
    var client = books.newClient("s86(4) interest");
    books.deposit(funding, client, principal);
    var placed = books.requestPlacement(funding, investmentAccount, client, principal);
    books.approvePlacement(
        funding,
        Expected.cashbookAndClient("-" + principal),
        investmentAccount,
        Expected.cashbookAndInvestment(principal),
        placed.depositTransactionId(),
        approverId);
    return new Held(funding, investmentAccount, client, placed.id());
  }

  /** The record body for {@code INVESTMENT_INTEREST} (null fields are omitted). */
  private static String interestJson(
      UUID investmentId, String presentation, String gross, String fund) {
    var json = new StringBuilder("{\"classification\":\"INVESTMENT_INTEREST\"");
    json.append(",\"investmentId\":\"").append(investmentId).append('"');
    json.append(",\"presentation\":\"").append(presentation).append('"');
    if (gross != null) {
      json.append(",\"grossAmount\":").append(gross);
    }
    if (fund != null) {
      json.append(",\"fundShareAmount\":").append(fund);
    }
    return json.append('}').toString();
  }

  /** Records a NET interest line ({@code net} = the line), returning the entry id. */
  private UUID recordInterestLine(Held held, UUID lineId, String net, String gross) {
    var body = new String[1];
    moves(
        held.investmentAccount(),
        Expected.cashbookAndInvestment(net),
        () ->
            body[0] =
                record(lineId, interestJson(held.investmentId(), "NET", gross, null))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
    return UUID.fromString(JsonPath.read(body[0], "$.investmentInterestId"));
  }

  /** investment|date|gross|fund|net|presentation|net txn|fund txn|line|status of an entry. */
  private String entry(UUID interestId) {
    return jdbc.queryForObject(
        """
        SELECT investment_id || '|' || entry_date || '|' || gross_amount || '|'
               || fund_share_amount || '|' || net_amount || '|' || presentation || '|'
               || net_transaction_id || '|' || COALESCE(fund_share_transaction_id::text, 'NULL')
               || '|' || COALESCE(bank_statement_line_id::text, 'NULL') || '|' || status
          FROM "%s".trust_investment_interest WHERE id = ?
        """
            .formatted(books.schema()),
        String.class,
        interestId);
  }

  private String entryStatus(UUID interestId) {
    return jdbc.queryForObject(
        "SELECT status FROM \"%s\".trust_investment_interest WHERE id = ?"
            .formatted(books.schema()),
        String.class,
        interestId);
  }

  /** Imports a two-line CSV on {@code account}; the lines' stored suggestions in line order. */
  private List<String> importedSuggestions(UUID account) throws Exception {
    var date = pastDate();
    var day = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    var csv =
        """
        FNB Trust Account Statement - Account 71000000648
        Date,Description,Amount,Balance,Reference
        %1$s,Interest capitalised,95.00,95.00,INT-CAP
        %1$s,Sundry credit,10.00,105.00,SUNDRY
        """
            .formatted(day);
    var body =
        mockMvc
            .perform(
                multipart("/api/trust-accounts/{accountId}/bank-statements", account)
                    .file(
                        new MockMultipartFile(
                            "file",
                            "interest-648b.csv",
                            "text/csv",
                            csv.getBytes(StandardCharsets.UTF_8)))
                    .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<Object> suggestions = JsonPath.read(body, "$.lines[*].suggestedClassification");
    return suggestions.stream().map(v -> v == null ? "NULL" : v.toString()).toList();
  }

  /** A fresh non-primary INVESTMENT trust account (647B: unidentified deposits are refused). */
  private UUID investmentAccount() {
    var id = new UUID[1];
    books.as(
        ownerId,
        () ->
            id[0] =
                trustAccountService
                    .createTrustAccount(
                        new CreateTrustAccountRequest(
                            "SLP Investment " + UUID.randomUUID().toString().substring(0, 8),
                            "First National Bank",
                            "250655",
                            "SLP-INV-" + UUID.randomUUID().toString().substring(0, 8),
                            "INVESTMENT",
                            false,
                            false,
                            null,
                            LocalDate.of(2026, 1, 1),
                            "647B investment account",
                            InvestmentBasis.FIRM_DISCRETION))
                    .id());
    return id[0];
  }

  /** A MockMvc step that may throw. */
  @FunctionalInterface
  private interface Step {
    void run() throws Exception;
  }

  /** {@link TrustBooksHarness#assertMoves} as the owner, around a MockMvc step. */
  private void moves(UUID account, Expected expected, Step step) {
    books.as(
        ownerId,
        () ->
            harness.assertMoves(
                account,
                expected,
                () -> {
                  try {
                    step.run();
                  } catch (Exception e) {
                    throw new IllegalStateException(e);
                  }
                }));
  }

  private ResultActions record(UUID lineId, String json) throws Exception {
    return mockMvc.perform(
        post("/api/bank-statement-lines/{lineId}/record", lineId)
            .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  private ResultActions unmatch(UUID lineId) throws Exception {
    return mockMvc.perform(
        post("/api/bank-statement-lines/{lineId}/unmatch", lineId)
            .with(TestJwtFactory.ownerJwt(ORG_ID, OWNER)));
  }

  /** Records {@code line} (201), asserting cashbook and Fund move by {@code signedAmount}. */
  private UUID recordAndMove(UUID account, StatementLine line, String json, String signedAmount) {
    var body = new String[1];
    moves(
        account,
        Expected.cashbookAndFund(signedAmount),
        () ->
            body[0] =
                record(line.lineId(), json)
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString());
    return UUID.fromString(JsonPath.read(body[0], "$.transactions[0].id"));
  }

  /** The record is refused with 400 {@code title} / {@code detail}; nothing moves. */
  private void assertRecordRefused(
      UUID account, UUID lineId, String json, String title, String detail) {
    var statementId = line(lineId).getBankStatementId();
    var countBefore = matchedCount(statementId);
    moves(
        account,
        Expected.none(),
        () ->
            record(lineId, json)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.detail").value(detail)));
    assertThat(matchedCount(statementId)).as("matched_count after %s", json).isEqualTo(countBefore);
  }

  private BooksSnapshot snapshot(UUID account) {
    var snapshot = new BooksSnapshot[1];
    books.as(ownerId, () -> snapshot[0] = harness.snapshot(account));
    return snapshot[0];
  }

  private void assertBalanced(UUID account) {
    books.as(ownerId, () -> harness.assertBalanced(account));
  }

  private BankPosition bankPosition(UUID account, StatementLine line, LocalDate asAt) {
    var position = new BankPosition[1];
    books.as(
        ownerId,
        () ->
            position[0] =
                books.read(() -> booksService.bankPosition(account, line.statementId(), asAt)));
    return position[0];
  }

  private TrustTransaction row(UUID id) {
    return books.read(() -> transactionRepository.findById(id).orElseThrow());
  }

  private List<TrustTransaction> reversalsOf(UUID originalId) {
    return books.read(
        () ->
            transactionRepository.findAll().stream()
                .filter(t -> originalId.equals(t.getReversalOf()))
                .toList());
  }

  private BankStatementLine line(UUID lineId) {
    return books.read(() -> lineRepository.findById(lineId).orElseThrow());
  }

  private int matchedCount(StatementLine line) {
    return matchedCount(line.statementId());
  }

  private int matchedCount(UUID statementId) {
    return books.read(
        () -> statementRepository.findById(statementId).orElseThrow().getMatchedCount());
  }

  private AuditEvent audit(String entityType, UUID entityId, String eventType) {
    var events =
        books.read(
            () ->
                auditEventRepository
                    .findByFilter(
                        entityType, entityId, null, eventType, null, null, PageRequest.of(0, 10))
                    .getContent());
    assertThat(events).as("%s on %s", eventType, entityId).hasSize(1);
    return events.getFirst();
  }
}
