package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.StatementLineClassification;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BankStatementParserTest {

  private final FnbCsvParser fnbParser = new FnbCsvParser();
  private final StandardBankCsvParser standardBankParser = new StandardBankCsvParser();
  private final NedbankCsvParser nedbankParser = new NedbankCsvParser();
  private final AbsaCsvParser absaParser = new AbsaCsvParser();
  private final GenericCsvParser genericParser = new GenericCsvParser();

  // --- canParse detection tests ---

  @Test
  void fnbParser_detectsFnbFormat() {
    assertThat(
            fnbParser.canParse(
                "statement.csv", "FNB Trust Account Statement - Account 62012345678"))
        .isTrue();
    assertThat(fnbParser.canParse("statement.csv", "First National Bank Export")).isTrue();
    assertThat(fnbParser.canParse("statement.csv", "Standard Bank Trust Account")).isFalse();
    assertThat(fnbParser.canParse("statement.csv", null)).isFalse();
  }

  @Test
  void standardBankParser_detectsStandardBankFormat() {
    assertThat(
            standardBankParser.canParse(
                "statement.csv", "Standard Bank Trust Account - Acc 012345678"))
        .isTrue();
    assertThat(standardBankParser.canParse("statement.csv", "FNB Trust Account")).isFalse();
    assertThat(standardBankParser.canParse("statement.csv", null)).isFalse();
  }

  @Test
  void nedbankParser_detectsNedbankFormat() {
    assertThat(nedbankParser.canParse("statement.csv", "Nedbank Professional Trust Account"))
        .isTrue();
    assertThat(nedbankParser.canParse("statement.csv", "ABSA Trust Account")).isFalse();
    assertThat(nedbankParser.canParse("statement.csv", null)).isFalse();
  }

  @Test
  void absaParser_detectsAbsaFormat() {
    assertThat(absaParser.canParse("statement.csv", "ABSA Trust Account Statement")).isTrue();
    assertThat(absaParser.canParse("statement.csv", "Absa Business Account")).isTrue();
    assertThat(absaParser.canParse("statement.csv", "Nedbank Trust Account")).isFalse();
    assertThat(absaParser.canParse("statement.csv", null)).isFalse();
  }

  // --- Parsing tests ---

  @Test
  void fnbParser_extractsPeriodAndLinesCorrectly() throws IOException {
    var result = fnbParser.parse(fixtureStream("fnb-sample.csv"));

    assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(result.lines()).hasSize(11);

    // The zero-amount "Opening Balance" row is not a line (652G); it still gives the opening
    // balance
    assertThat(result.openingBalance()).isEqualByComparingTo(new BigDecimal("150000.00"));

    // Verify a credit line
    var depositLine = result.lines().get(0);
    assertThat(depositLine.date()).isEqualTo(LocalDate.of(2026, 3, 3));
    assertThat(depositLine.description()).isEqualTo("Deposit from Smith & Associates");
    assertThat(depositLine.amount()).isEqualByComparingTo(new BigDecimal("25000.00"));
    assertThat(depositLine.reference()).isEqualTo("REF-SM-001");

    // Verify a debit line
    var paymentLine = result.lines().get(1);
    assertThat(paymentLine.amount()).isEqualByComparingTo(new BigDecimal("-8500.00"));
  }

  @Test
  void standardBankParser_handlesYyyyMmDdDates() throws IOException {
    var result = standardBankParser.parse(fixtureStream("standard-bank-sample.csv"));

    assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(result.lines()).hasSize(10);

    // Verify yyyy-MM-dd date parsing
    var depositLine = result.lines().get(0);
    assertThat(depositLine.date()).isEqualTo(LocalDate.of(2026, 3, 4));
    assertThat(depositLine.description()).isEqualTo("Deposit - Van der Merwe Matter");
    assertThat(depositLine.reference()).isEqualTo("VDM-2026-001");
    assertThat(depositLine.amount()).isEqualByComparingTo(new BigDecimal("35000.00"));
  }

  @Test
  void nedbankParser_handlesDdMmmYyyyDates() throws IOException {
    var result = nedbankParser.parse(fixtureStream("nedbank-sample.csv"));

    assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(result.lines()).hasSize(10);

    // Verify "dd MMM yyyy" date parsing
    var depositLine = result.lines().get(0);
    assertThat(depositLine.date()).isEqualTo(LocalDate.of(2026, 3, 5));
    assertThat(depositLine.description()).isEqualTo("Deposit - Govender Matter");

    // Verify quoted field with comma is handled correctly
    var courierLine = result.lines().get(5);
    assertThat(courierLine.description()).isEqualTo("Disbursement, courier fees");
    assertThat(courierLine.amount()).isEqualByComparingTo(new BigDecimal("-450.00"));
  }

  @Test
  void absaParser_parsesAbsaStatementCorrectly() throws IOException {
    var result = absaParser.parse(fixtureStream("absa-sample.csv"));

    assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(result.lines()).hasSize(10);

    // The zero-amount "Opening Balance" row is not a line (652G); it still gives the opening
    // balance
    assertThat(result.openingBalance()).isEqualByComparingTo(new BigDecimal("95000.00"));

    // Verify a credit line
    var depositLine = result.lines().get(0);
    assertThat(depositLine.date()).isEqualTo(LocalDate.of(2026, 3, 4));
    assertThat(depositLine.description()).isEqualTo("Deposit from Willemse Inc");
    assertThat(depositLine.reference()).isEqualTo("WIL-DEP-001");
    assertThat(depositLine.amount()).isEqualByComparingTo(new BigDecimal("18000.00"));

    // Verify a debit line
    var sheriffLine = result.lines().get(1);
    assertThat(sheriffLine.amount()).isEqualByComparingTo(new BigDecimal("-4200.00"));
    assertThat(sheriffLine.reference()).isEqualTo("SHF-789");
  }

  @Test
  void genericParser_worksAsFallback() throws IOException {
    var result = genericParser.parse(fixtureStream("generic-sample.csv"));

    assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(result.lines()).hasSize(8);

    // Verify the generic parser detects dd/MM/yyyy format
    var transferLine = result.lines().get(2);
    assertThat(transferLine.date()).isEqualTo(LocalDate.of(2026, 3, 16));
    assertThat(transferLine.description()).isEqualTo("Transfer received");
    assertThat(transferLine.amount()).isEqualByComparingTo(new BigDecimal("28000.00"));
  }

  @Test
  void genericParser_alwaysReturnsTrueForCanParse() {
    assertThat(genericParser.canParse("anything.csv", "Some Random Header")).isTrue();
    assertThat(genericParser.canParse("file.csv", null)).isTrue();
    assertThat(genericParser.canParse("", "")).isTrue();
  }

  @Test
  void parser_handlesEmptyStatement() throws IOException {
    var emptyInput = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
    var result = genericParser.parse(emptyInput);

    assertThat(result.lines()).isEmpty();
    assertThat(result.openingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.closingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.periodStart()).isNull();
    assertThat(result.periodEnd()).isNull();
  }

  @Test
  void parser_handlesMalformedCsvGracefully() throws IOException {
    String malformedCsv =
        """
        Generic CSV Statement
        not-a-date,description,abc,100.00
        01/03/2026,Valid Transaction,500.00,500.00
        ,,,,
        broken
        01/03/2026,Another Valid,200.00,700.00
        """;
    var input = new ByteArrayInputStream(malformedCsv.getBytes(StandardCharsets.UTF_8));
    var result = genericParser.parse(input);

    // Should parse the valid lines and skip malformed ones
    assertThat(result.lines()).hasSize(2);
    assertThat(result.lines().getFirst().description()).isEqualTo("Valid Transaction");
    assertThat(result.lines().getLast().description()).isEqualTo("Another Valid");
  }

  // --- Zero-amount balance rows (Phase 92 / 652G, §16.9.3) ---

  /**
   * A bank export's "Opening Balance" row (amount 0,00, running balance = the opening balance) is
   * not a movement: imported as a line it can be neither matched nor recorded, and gate G4 would
   * hold the reconciliation on it. The statement's opening and closing balance still come from the
   * running-balance column.
   */
  @Test
  void zeroAmountBalanceRows_areNotImportedAsLines() throws IOException {
    var result = standardBankParser.parse(fixtureStream("standard-bank-sample.csv"));

    assertThat(result.lines())
        .noneMatch(
            line ->
                line.description().equalsIgnoreCase("Opening Balance")
                    && line.amount().signum() == 0);
    assertThat(result.lines()).hasSize(10);
    assertThat(result.lines().getFirst().description()).isEqualTo("Deposit - Van der Merwe Matter");
    assertThat(result.openingBalance()).isEqualByComparingTo(new BigDecimal("200000.00"));
    assertThat(result.closingBalance()).isEqualByComparingTo(new BigDecimal("282500.00"));
    assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
  }

  /**
   * Every marker kind (opening / closing / brought-forward / carried-forward), any case, runs of
   * whitespace collapsed: the zero-amount row is not a line, and still gives the period and the
   * opening / closing balance.
   */
  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "Opening Balance",
        "OPENING BALANCE",
        "Closing Balance",
        "Balance Brought Forward",
        "Brought Forward",
        "BALANCE B/F",
        "Bal b/f",
        "B/F",
        "Balance Carried Forward",
        "Carried Forward",
        "BALANCE C/F",
        "c/f",
        "Opening  Balance",
      })
  void zeroAmountBalanceMarkerRows_areNotLines(String marker) throws IOException {
    String csv =
        """
        Date,Description,Amount,Balance
        01/03/2026,%1$s,0.00,1000.00
        02/03/2026,Client deposit,500.00,1500.00
        31/03/2026,%1$s,0.00,1500.00
        """
            .formatted(marker);
    var result =
        genericParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(result.lines())
        .extracting(ParsedStatementLine::description)
        .containsExactly("Client deposit");
    assertThat(result.openingBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(result.closingBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));
    assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
  }

  /**
   * Skip only when both hold: a non-zero row with a marker description is money that moved (or an
   * export the firm must see), and a zero-amount row whose description merely contains a marker
   * word, or none, is still a line.
   */
  @Test
  void zeroAmountNonMarkerRow_andNonZeroMarkerRow_areStillLines() throws IOException {
    String csv =
        """
        Date,Description,Amount,Balance
        01/03/2026,Opening Balance,100.00,100.00
        05/03/2026,Interest,0.00,100.00
        06/03/2026,Balance enquiry,0.00,100.00
        """;
    var result =
        genericParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(result.lines())
        .extracting(ParsedStatementLine::description)
        .containsExactly("Opening Balance", "Interest", "Balance enquiry");
  }

  /**
   * A dormant month — only a balance row — imports as a statement with no lines, not as "all
   * malformed": the period still comes from the balance row ({@code period_start} is NOT NULL).
   */
  @Test
  void balanceRowOnlyStatement_hasNoLinesAndKeepsPeriodAndBalances() throws IOException {
    String csv =
        """
        Date,Description,Amount,Balance
        01/03/2026,Opening Balance,0.00,250.00
        """;
    var result =
        genericParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

    assertThat(result.lines()).isEmpty();
    assertThat(result.openingBalance()).isEqualByComparingTo(new BigDecimal("250.00"));
    assertThat(result.closingBalance()).isEqualByComparingTo(new BigDecimal("250.00"));
    assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 1));
  }

  // --- Parser suggestions (Phase 92 / 647C, §16.3.5 keyword table, ADR-380) ---

  private static final StatementLineClassification FUND_INTEREST =
      StatementLineClassification.FUND_INTEREST;
  private static final StatementLineClassification FUND_SWEEP =
      StatementLineClassification.FUND_SWEEP;
  private static final StatementLineClassification BANK_CHARGE =
      StatementLineClassification.BANK_CHARGE;
  private static final StatementLineClassification CHARGE_REFUND =
      StatementLineClassification.CHARGE_REFUND;

  /**
   * One case per keyword row, the exclusions, case-insensitivity, the two precedence choices (a
   * sweep wins over a charge on a debit; a charge refund wins over interest on a credit), a zero
   * amount and ordinary lines. The last three rows pin the substring matching the spec asks for
   * ("contains"): "fee" inside an ordinary payment's description suggests a bank charge — weak by
   * design; auto-match clears it when it matches and the firm always confirms.
   */
  @ParameterizedTest(name = "{0} {1} -> {2}")
  @CsvSource(
      nullValues = "NONE",
      value = {
        "LPFF TRANSFER, -1119.56, FUND_SWEEP",
        "Fidelity Fund interest, -20.00, FUND_SWEEP",
        "lpff sweep, -5.00, FUND_SWEEP",
        "LPFF service fee, -15.00, FUND_SWEEP",
        "INTEREST CREDIT, 1234.56, FUND_INTEREST",
        "interest earned, 156.75, FUND_INTEREST",
        "LPFF interest, 100.00, NONE",
        "Fidelity interest, 100.00, NONE",
        "CASH DEPOSIT FEE, -57.50, BANK_CHARGE",
        "Bank charges, -85.50, BANK_CHARGE",
        "Commission, -10.00, BANK_CHARGE",
        "Monthly service fee, -125.00, BANK_CHARGE",
        "Fee refund, 57.50, CHARGE_REFUND",
        "Charge reversal, 15.00, CHARGE_REFUND",
        "Interest fee refund, 12.00, CHARGE_REFUND",
        "Refund from supplier, 100.00, NONE",
        "Service fee, 10.00, NONE",
        "Interest, -10.00, NONE",
        "Interest, 0.00, NONE",
        "Bank charges, 0.00, NONE",
        "Deposit from Smith & Associates, 25000.00, NONE",
        "Payment to SARS, -12500.00, NONE",
        "Sheriff fees payment, -6200.00, BANK_CHARGE",
        "'Disbursement, courier fees', -450.00, BANK_CHARGE",
        "Advocate consultation fee, -6500.00, BANK_CHARGE",
      })
  void suggestClassification_followsTheKeywordTable(
      String description, String amount, StatementLineClassification expected) {
    assertThat(genericParser.suggestClassification(description, new BigDecimal(amount)))
        .isEqualTo(expected);
  }

  @Test
  void suggestClassification_nullDescriptionOrAmount_suggestsNothing() {
    assertThat(genericParser.suggestClassification(null, new BigDecimal("-10.00"))).isNull();
    assertThat(genericParser.suggestClassification("Bank charges", null)).isNull();
  }

  @Test
  void fnbFixture_suggestionsPerLine() throws IOException {
    assertThat(suggestions(fnbParser, "fnb-sample.csv"))
        .containsExactly(
            null,
            null,
            null,
            null,
            null,
            null,
            FUND_INTEREST,
            BANK_CHARGE,
            null,
            FUND_SWEEP,
            CHARGE_REFUND);
  }

  @Test
  void standardBankFixture_suggestionsPerLine() throws IOException {
    // index 1 "Sheriff fees payment" → BANK_CHARGE: a false positive by design ("FEE" substring)
    assertThat(suggestions(standardBankParser, "standard-bank-sample.csv"))
        .containsExactly(
            null,
            BANK_CHARGE,
            null,
            null,
            null,
            null,
            FUND_INTEREST,
            BANK_CHARGE,
            FUND_SWEEP,
            CHARGE_REFUND);
  }

  @Test
  void nedbankFixture_suggestionsPerLine() throws IOException {
    // index 5 "Disbursement, courier fees" → BANK_CHARGE: a false positive by design
    assertThat(suggestions(nedbankParser, "nedbank-sample.csv"))
        .containsExactly(
            null,
            null,
            null,
            null,
            null,
            BANK_CHARGE,
            FUND_INTEREST,
            BANK_CHARGE,
            FUND_SWEEP,
            CHARGE_REFUND);
  }

  @Test
  void absaFixture_suggestionsPerLine() throws IOException {
    // index 5 "Advocate consultation fee" → BANK_CHARGE: a false positive by design
    assertThat(suggestions(absaParser, "absa-sample.csv"))
        .containsExactly(
            null,
            null,
            null,
            null,
            null,
            BANK_CHARGE,
            FUND_INTEREST,
            BANK_CHARGE,
            FUND_SWEEP,
            CHARGE_REFUND);
  }

  @Test
  void genericFixture_suggestionsPerLine() throws IOException {
    assertThat(suggestions(genericParser, "generic-sample.csv"))
        .containsExactly(
            null, null, null, null, FUND_INTEREST, BANK_CHARGE, FUND_SWEEP, CHARGE_REFUND);
  }

  /**
   * UNIDENTIFIED_DEPOSIT (647B) and INVESTMENT_INTEREST (648B) are never suggested: every
   * suggestion over every fixture line is one of the four Fund classifications, or none.
   */
  @Test
  void suggestions_areOnlyTheFourFundClassifications() throws IOException {
    var all = new ArrayList<StatementLineClassification>();
    all.addAll(suggestions(fnbParser, "fnb-sample.csv"));
    all.addAll(suggestions(standardBankParser, "standard-bank-sample.csv"));
    all.addAll(suggestions(nedbankParser, "nedbank-sample.csv"));
    all.addAll(suggestions(absaParser, "absa-sample.csv"));
    all.addAll(suggestions(genericParser, "generic-sample.csv"));
    assertThat(all.stream().filter(Objects::nonNull).toList())
        .isNotEmpty()
        .allMatch(EnumSet.of(FUND_INTEREST, FUND_SWEEP, BANK_CHARGE, CHARGE_REFUND)::contains);
  }

  private List<StatementLineClassification> suggestions(
      CsvBankStatementParser parser, String fixture) throws IOException {
    return parser.parse(fixtureStream(fixture)).lines().stream()
        .map(ParsedStatementLine::suggestedClassification)
        .toList();
  }

  private InputStream fixtureStream(String fileName) {
    var stream = getClass().getResourceAsStream("/fixtures/trust/" + fileName);
    if (stream == null) {
      throw new IllegalStateException("Fixture not found: /fixtures/trust/" + fileName);
    }
    return stream;
  }
}
