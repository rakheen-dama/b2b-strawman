package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.provisioning.TenantProvisioningService;
import io.b2mash.b2b.b2bstrawman.testutil.TestJwtFactory;
import io.b2mash.b2b.b2bstrawman.testutil.TestMemberHelper;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksFixture.TakeOn;
import io.b2mash.b2b.b2bstrawman.testutil.TrustBooksHarness.Expected;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.IntegrityMessages;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.books.TrustIntegrityService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.TrustReconciliationService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Phase 93 657D (task 657.31, §17.3.13, §17.3.11, FD-11, FD-32): the exit invariant — <b>the first
 * post-cutover reconciliation balances with no manual adjustment</b>. The §17.3.13 take-on on the
 * harness path (posted through the real {@code recordOpening}) at a floating cutover date C = the
 * last day of the month two months back (A-11: every statement line is in the past, FU-6a, and the
 * first month's end locks its Rule 54.15 list); the first month's statement is imported through the
 * API, the two items outstanding at cutover auto-match by reference, the interest and the bank fee
 * are recorded from their lines, and the reconciliation calculates and completes with the cashbook,
 * the adjusted bank balance and the trust creditors all R438 727,40. Around it: the first-statement
 * warning {@code OPENING_DIFFERS_FROM_TAKE_ON} (never blocking, first statement only), the refusal
 * of a statement wholly on or before C, and a mid-month C whose earlier lines are excluded at
 * import.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FirstPostCutoverReconciliationIntegrationTest {

  private static final String ORG_ID = "org_first_post_cutover_recon";
  private static final String OWNER = "user_fpc_owner";
  private static final DateTimeFormatter FNB_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final DateTimeFormatter LONG_DATE =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  @Autowired private MockMvc mockMvc;
  @Autowired private TenantProvisioningService provisioningService;
  @Autowired private TrustBooksFixture fixture;
  @Autowired private JdbcTemplate jdbc;

  private TrustBooksFixture.Tenant books;
  private UUID approverId;

  @BeforeAll
  void setup() throws Exception {
    var schema =
        provisioningService
            .provisionTenant(ORG_ID, "First Post-Cutover Reconciliation Org", null)
            .schemaName();
    var ownerId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc, ORG_ID, OWNER, "fpc_owner@test.com", "First Month Owner", "owner"));
    approverId =
        UUID.fromString(
            TestMemberHelper.syncMember(
                mockMvc,
                ORG_ID,
                "user_fpc_approver",
                "fpc_approver@test.com",
                "First Month Approver",
                "owner"));
    fixture.enableTrustModule(schema);
    books = fixture.tenant(schema, ORG_ID, ownerId);
  }

  @AfterAll
  void booksBalanceFirmWide() {
    books.assertFirmBalanced();
  }

  /** C: the last day of the month two months back (the 660.2 rule). */
  private LocalDate cutover() {
    return YearMonth.from(books.today()).minusMonths(2).atEndOfMonth();
  }

  // 1
  @Test
  void theWorkedFirstMonthBalances_andCompletes_withNoManualAdjustment() throws Exception {
    var takeOn = books.takeOnSection17313(cutover());
    var first = firstMonth(takeOn);

    // the first month's reconciliation: cashbook = adjusted bank = creditors = R438 727,40
    var calculated = first.calculated();
    assertThat((Boolean) JsonPath.read(calculated, "$.isBalanced")).isTrue();
    assertThat(money(calculated, "$.bankBalance")).isEqualByComparingTo("448727.40");
    assertThat(money(calculated, "$.outstandingDeposits")).isEqualByComparingTo("0");
    assertThat(money(calculated, "$.outstandingPayments")).isEqualByComparingTo("10000.00");
    assertThat(money(calculated, "$.adjustedBankBalance")).isEqualByComparingTo("438727.40");
    assertThat(money(calculated, "$.cashbookBalance")).isEqualByComparingTo("438727.40");
    assertThat(money(calculated, "$.creditorBreakdown.clientLedgers"))
        .isEqualByComparingTo("534500.00");
    assertThat(money(calculated, "$.creditorBreakdown.fundInterestAndCharges"))
        .isEqualByComparingTo("1477.40");
    assertThat(money(calculated, "$.creditorBreakdown.unidentifiedDeposits"))
        .isEqualByComparingTo("2750.00");
    assertThat(money(calculated, "$.creditorBreakdown.interAccount"))
        .isEqualByComparingTo("-100000.00");
    assertThat(money(calculated, "$.creditorBreakdown.total")).isEqualByComparingTo("438727.40");
    assertThat(money(calculated, "$.difference")).isEqualByComparingTo("0");
    assertThat(money(calculated, "$.excludedLinesTotal")).isEqualByComparingTo("0");
    assertThat(JsonPath.<Integer>read(calculated, "$.unresolvedLineCount")).isZero();
    assertThat(flagCodes(calculated)).doesNotContain(OPENING_DIFFERS);

    // both items outstanding at cutover were matched by their old references
    assertThat(lineLinkedTo(takeOn.depTransit())).isEqualTo("EFT B-0001");
    assertThat(lineLinkedTo(takeOn.payUnpres())).isEqualTo("PAY-778");

    // completed: the Rule 54.15 list for the month end is locked; no line was excluded by hand
    var completed = first.completed();
    assertThat(JsonPath.<String>read(completed, "$.status")).isEqualTo("COMPLETED");
    String listId = JsonPath.read(completed, "$.monthlyListId");
    assertThat(listId).isNotNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT period_end FROM \"%s\".trust_monthly_lists WHERE id = ?::uuid AND locked_at IS NOT NULL"
                    .formatted(books.schema()),
                LocalDate.class,
                listId))
        .isEqualTo(first.monthEnd());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".bank_statement_lines WHERE bank_statement_id = ?::uuid AND match_status = 'EXCLUDED'"
                    .formatted(books.schema()),
                Long.class,
                first.statementId()))
        .isZero();
    books.assertBalancedAsAt(takeOn.g(), first.monthEnd());
  }

  // 2
  @Test
  void anOpeningOfNil_raisesOpeningDiffersFromTakeOn_asAWarningThatNeverBlocks() throws Exception {
    // (a) a statement without a Balance column opens at R0,00: the warning, with the X3 sentence
    var takeOn = books.takeOnSection17313(cutover());
    var m = YearMonth.from(takeOn.cutover()).plusMonths(1);
    String noBalance =
        importStatement(
            takeOn.g(),
            line(m.atDay(2), "EFT B-0001", "5000.00", "", "EFT B-0001"),
            line(m.atDay(4), "PAY-778", "-20000.00", "", "PAY-778"));
    var flag = openingFlag(calculate(reconciliation(takeOn.g(), noBalance, m.atDay(4))));
    assertThat(flag).isNotNull();
    assertThat(flag.get("severity")).isEqualTo(TrustIntegrityService.WARNING);
    assertThat(flag.get("message"))
        .isEqualTo(
            "The statement's balance at the take-on date ("
                + IntegrityMessages.rand(BigDecimal.ZERO)
                + ") is not the bank balance entered at take-on ("
                + IntegrityMessages.rand(new BigDecimal("418500.00"))
                + "). Check this is the statement that follows the cutover.");

    // (b) a statement whose closing ties but whose opening reads R0,00 completes with the warning
    var other = books.takeOnSection17313(cutover());
    String wrongOpening =
        importStatement(
            other.g(),
            line(m.atDay(2), "EFT B-0001", "5000.00", "5000.00", "EFT B-0001"),
            line(m.atDay(4), "PAY-778", "-20000.00", "403500.00", "PAY-778"));
    autoMatch(wrongOpening);
    String reconciliation = reconciliation(other.g(), wrongOpening, m.atDay(4));
    var calculated = calculate(reconciliation);
    assertThat((Boolean) JsonPath.read(calculated, "$.isBalanced")).isTrue();
    assertThat(openingFlag(calculated)).isNotNull();
    complete(reconciliation, wrongOpening)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  // 3
  @Test
  void aStatementWhollyOnOrBeforeTheCutover_isRefused422_andNothingIsStored() throws Exception {
    var takeOn = books.takeOnSection17313(cutover());
    var c = takeOn.cutover();
    long before = statementCount(takeOn.g());

    importStatementResult(
            takeOn.g(),
            line(c.minusDays(5), "OLD DEPOSIT", "1000.00", "419500.00", "OLD-1"),
            line(c, "OLD PAYMENT", "-1000.00", "418500.00", "OLD-2"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.code").value(TrustReconciliationService.STATEMENT_BEFORE_TAKE_ON))
        .andExpect(jsonPath("$.title").value("Statement before the take-on"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Every line of this statement is dated on or before the take-on date "
                        + IntegrityMessages.date(c)
                        + ". It belongs to the previous system's books."));
    assertThat(statementCount(takeOn.g())).isEqualTo(before);
  }

  // 4
  @Test
  void aStatementSpanningAMidMonthCutover_excludesTheEarlierLines_andStillBalances()
      throws Exception {
    var c2 = YearMonth.from(books.today()).minusMonths(2).atDay(15);
    var takeOn = books.takeOnSection17313(c2);
    var monthEnd = YearMonth.from(c2).atEndOfMonth();

    String body =
        importStatementJson(
            takeOn.g(),
            line(c2.withDayOfMonth(10), "PRIOR DEPOSIT", "8500.00", "418500.00", "PRIOR-1"),
            line(c2.withDayOfMonth(20), "EFT B-0001", "5000.00", "423500.00", "EFT B-0001"),
            line(c2.withDayOfMonth(22), "PAY-778", "-20000.00", "403500.00", "PAY-778"));
    String statementId = JsonPath.read(body, "$.id");
    assertThat(JsonPath.<String>read(body, "$.lines[0].matchStatus")).isEqualTo("EXCLUDED");
    assertThat(JsonPath.<String>read(body, "$.lines[0].suggestedClassification")).isNull();
    assertThat(JsonPath.<String>read(body, "$.lines[1].matchStatus")).isEqualTo("UNMATCHED");
    assertThat(JsonPath.<Integer>read(body, "$.matchedCount")).isZero();
    assertThat(JsonPath.<List<String>>read(body, "$.warnings"))
        .containsExactly(
            "1 lines dated on or before the take-on date "
                + IntegrityMessages.date(c2)
                + " were excluded.");
    assertThat(
            jdbc.queryForObject(
                "SELECT excluded_reason FROM \"%s\".bank_statement_lines WHERE bank_statement_id = ?::uuid AND line_number = 1"
                    .formatted(books.schema()),
                String.class,
                statementId))
        .isEqualTo(TrustReconciliationService.BEFORE_TAKE_ON_REASON);
    assertThat(
            jdbc.queryForObject(
                "SELECT details ->> 'excluded_before_take_on' FROM \"%s\".audit_events WHERE entity_id = ?::uuid AND event_type = 'bank_statement.imported'"
                    .formatted(books.schema()),
                String.class,
                statementId))
        .isEqualTo("1");
    // no exclusion was audited line by line (set at construction, never through excludeLine)
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM \"%s\".audit_events WHERE event_type = 'bank_statement_line.excluded' AND entity_id = ?::uuid"
                    .formatted(books.schema()),
                Long.class,
                JsonPath.<String>read(body, "$.lines[0].id")))
        .isZero();

    autoMatch(statementId);
    String reconciliation = reconciliation(takeOn.g(), statementId, monthEnd);
    var calculated = calculate(reconciliation);
    assertThat((Boolean) JsonPath.read(calculated, "$.isBalanced")).isTrue();
    assertThat(money(calculated, "$.cashbookBalance")).isEqualByComparingTo("403500.00");
    assertThat(money(calculated, "$.adjustedBankBalance")).isEqualByComparingTo("403500.00");
    // the balance at C2 is the running balance of the last line on or before it = B
    assertThat(flagCodes(calculated)).doesNotContain(OPENING_DIFFERS);
    complete(reconciliation, statementId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  // 5
  @Test
  void theDuplicateAndFutureLineRulesStillApply_onATakenOnAccount() throws Exception {
    var takeOn = books.takeOnSection17313(cutover());
    var m = YearMonth.from(takeOn.cutover()).plusMonths(1);
    importStatement(
        takeOn.g(), line(m.atDay(2), "EFT B-0001", "5000.00", "423500.00", "EFT B-0001"));

    importStatementResult(
            takeOn.g(), line(m.atDay(2), "EFT B-0001", "5000.00", "423500.00", "EFT B-0001"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("bank_statement.duplicate_end_date"))
        .andExpect(jsonPath("$.title").value("A statement ending on this date is already imported"))
        .andExpect(
            jsonPath(
                "$.detail",
                allOf(
                    startsWith("Statement fpc-statement-"),
                    endsWith(
                        " of this trust account also ends on "
                            + m.atDay(2).format(LONG_DATE)
                            + ". Replace it with this statement, or import a statement that ends"
                            + " on another date."))));

    var tomorrow = books.today().plusDays(1);
    importStatementResult(
            takeOn.g(),
            line(m.atDay(3), "EFT B-0001", "5000.00", "423500.00", "EFT B-0001"),
            line(tomorrow, "TOMORROW", "1.00", "423501.00", "T-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Cannot import bank statement"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "This statement has a line dated "
                        + tomorrow.format(LONG_DATE)
                        + ", which is after today. Import the statement once the bank has"
                        + " processed that line."));
  }

  // 6
  @Test
  void theWarningIsOnlyOnTheFirstStatement() throws Exception {
    var takeOn = books.takeOnSection17313(cutover());
    firstMonth(takeOn);

    // the second statement opens at R0,00 (no Balance column): it is not the first, so no warning
    var day = books.today().withDayOfMonth(1);
    String second = importStatement(takeOn.g(), line(day, "SMALL DEPOSIT", "0.01", "", "SD-1"));
    var calculated = calculate(reconciliation(takeOn.g(), second, day));
    assertThat(flagCodes(calculated)).doesNotContain(OPENING_DIFFERS);
  }

  // 7
  @Test
  void anInvestmentAccountsFirstStatement_isNotComparedWithTheBankingAccountsBalance()
      throws Exception {
    // i3 is marked taken on by the batch, but the batch's entered bank balance (R418 500,00) is
    // the banking account g's: i3's first statement (opening R100 000,00) raises no warning
    var takeOn = books.takeOnSection17313(cutover());
    var m = YearMonth.from(takeOn.cutover()).plusMonths(1);
    String statement =
        importStatement(takeOn.i3(), line(m.atDay(5), "INTEREST", "100.00", "100100.00", "I3-INT"));
    var calculated = calculate(reconciliation(takeOn.i3(), statement, m.atDay(5)));
    assertThat(flagCodes(calculated)).doesNotContain(OPENING_DIFFERS);
  }

  // --- the worked first month ---------------------------------------------------------------

  private record FirstMonth(
      String statementId, LocalDate monthEnd, String calculated, String completed) {}

  /**
   * §17.3.13 "November in Kazi" on the month M after C: a deposit C/C1 R60 000 on day 10, an
   * approved payment B/B1 R15 000 on day 20, an approved payment A/A1 R10 000 on day 28 (not on the
   * statement); statement S1 (opening R418 500,00, closing R448 727,40) auto-matched; the interest
   * and the service fee recorded from their lines; reconciled at M's end and completed.
   */
  private FirstMonth firstMonth(TakeOn takeOn) throws Exception {
    var g = takeOn.g();
    var m = YearMonth.from(takeOn.cutover()).plusMonths(1);
    var monthEnd = m.atEndOfMonth();

    books.deposit(g, takeOn.c(), "60000.00", m.atDay(10));
    var paymentB = books.requestPayment(g, takeOn.b(), "15000.00", m.atDay(20));
    books.approve(g, paymentB, approverId, Expected.cashbookAndClient("-15000.00"));
    var paymentA = books.requestPayment(g, takeOn.a(), "10000.00", m.atDay(28));
    books.approve(g, paymentA, approverId, Expected.cashbookAndClient("-10000.00"));

    String body =
        importStatementJson(
            g,
            line(m.atDay(2), "EFT B-0001", "5000.00", "423500.00", "EFT B-0001"),
            line(m.atDay(4), "PAY-778", "-20000.00", "403500.00", "PAY-778"),
            line(m.atDay(10), "C1-DEP", "60000.00", "463500.00", "C1-DEP"),
            line(m.atDay(20), "B1-PAY", "-15000.00", "448500.00", "B1-PAY"),
            line(monthEnd, "INTEREST", "312.40", "448812.40", "INTEREST"),
            line(monthEnd, "SERVICE FEE", "-85.00", "448727.40", "SERVICE FEE"));
    String statementId = JsonPath.read(body, "$.id");
    assertThat(money(body, "$.openingBalance")).isEqualByComparingTo("418500.00");
    assertThat(money(body, "$.closingBalance")).isEqualByComparingTo("448727.40");
    assertThat(JsonPath.<List<String>>read(body, "$.warnings")).isEmpty();

    var matched = JsonPath.<Integer>read(autoMatch(statementId), "$.autoMatched");
    assertThat(matched).isEqualTo(4);

    List<Map<String, Object>> lines = JsonPath.read(body, "$.lines");
    record(lineId(lines, "INTEREST"), "{\"classification\":\"FUND_INTEREST\"}");
    record(
        lineId(lines, "SERVICE FEE"),
        "{\"classification\":\"BANK_CHARGE\",\"chargeRecoverable\":true,\"chargeVatAmount\":0}");

    String reconciliation = reconciliation(g, statementId, monthEnd);
    String calculated = calculate(reconciliation);
    String completed = json(complete(reconciliation, statementId).andExpect(status().isOk()));
    return new FirstMonth(statementId, monthEnd, calculated, completed);
  }

  // --- helpers ------------------------------------------------------------------------------

  private static final String OPENING_DIFFERS = TrustIntegrityService.OPENING_DIFFERS_FROM_TAKE_ON;

  private static String line(
      LocalDate date, String description, String amount, String balance, String reference) {
    return "%s,%s,%s,%s,%s"
        .formatted(FNB_DATE.format(date), description, amount, balance, reference);
  }

  private ResultActions importStatementResult(UUID account, String... lines) throws Exception {
    var csv =
        "FNB Trust Account Statement - Account 62000000000\n"
            + "Date,Description,Amount,Balance,Reference\n"
            + String.join("\n", lines)
            + "\n";
    return mockMvc.perform(
        multipart("/api/trust-accounts/{accountId}/bank-statements", account)
            .file(
                new MockMultipartFile(
                    "file",
                    "fpc-statement-" + UUID.randomUUID() + ".csv",
                    "text/csv",
                    csv.getBytes(StandardCharsets.UTF_8)))
            .with(owner()));
  }

  private String importStatementJson(UUID account, String... lines) throws Exception {
    return json(importStatementResult(account, lines).andExpect(status().isCreated()));
  }

  private String importStatement(UUID account, String... lines) throws Exception {
    return JsonPath.read(importStatementJson(account, lines), "$.id");
  }

  private String autoMatch(String statementId) throws Exception {
    return json(
        mockMvc
            .perform(post("/api/bank-statements/{id}/auto-match", statementId).with(owner()))
            .andExpect(status().isOk()));
  }

  private void record(String lineId, String request) throws Exception {
    mockMvc
        .perform(
            post("/api/bank-statement-lines/{lineId}/record", lineId)
                .with(owner())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
        .andExpect(status().isCreated());
  }

  private String reconciliation(UUID account, String statementId, LocalDate periodEnd)
      throws Exception {
    return JsonPath.read(
        json(
            mockMvc
                .perform(
                    post("/api/trust-accounts/{accountId}/reconciliations", account)
                        .with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            "{\"periodEnd\": \"%s\", \"bankStatementId\": \"%s\"}"
                                .formatted(periodEnd, statementId)))
                .andExpect(status().isCreated())),
        "$.id");
  }

  private String calculate(String reconciliationId) throws Exception {
    String statementId =
        jdbc.queryForObject(
            "SELECT bank_statement_id::text FROM \"%s\".trust_reconciliations WHERE id = ?::uuid"
                .formatted(books.schema()),
            String.class,
            reconciliationId);
    return json(
        mockMvc
            .perform(
                post("/api/trust-reconciliations/{id}/calculate", reconciliationId)
                    .with(owner())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedBankStatementId\": \"%s\"}".formatted(statementId)))
            .andExpect(status().isOk()));
  }

  private ResultActions complete(String reconciliationId, String statementId) throws Exception {
    return mockMvc.perform(
        post("/api/trust-reconciliations/{id}/complete", reconciliationId)
            .with(owner())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedBankStatementId\": \"%s\"}".formatted(statementId)));
  }

  private static String lineId(List<Map<String, Object>> lines, String reference) {
    return lines.stream()
        .filter(l -> reference.equals(l.get("reference")))
        .map(l -> (String) l.get("id"))
        .findFirst()
        .orElseThrow();
  }

  private String lineLinkedTo(UUID transactionId) {
    return jdbc.queryForObject(
        ("SELECT l.reference FROM \"%s\".bank_statement_lines l JOIN \"%s\".trust_transactions t"
                + " ON t.bank_statement_line_id = l.id WHERE t.id = ?")
            .formatted(books.schema(), books.schema()),
        String.class,
        transactionId);
  }

  private long statementCount(UUID account) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM \"%s\".bank_statements WHERE trust_account_id = ?"
            .formatted(books.schema()),
        Long.class,
        account);
  }

  private static List<String> flagCodes(String reconciliation) {
    return JsonPath.read(reconciliation, "$.integrityFlags[*].code");
  }

  private static Map<String, Object> openingFlag(String reconciliation) {
    List<Map<String, Object>> flags =
        JsonPath.read(reconciliation, "$.integrityFlags[?(@.code == '" + OPENING_DIFFERS + "')]");
    return flags.isEmpty() ? null : flags.getFirst();
  }

  private static BigDecimal money(String json, String path) {
    Object value = JsonPath.read(json, path);
    return new BigDecimal(value.toString());
  }

  private static String json(ResultActions result) throws Exception {
    return result.andReturn().getResponse().getContentAsString();
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
    return TestJwtFactory.ownerJwt(ORG_ID, OWNER);
  }
}
