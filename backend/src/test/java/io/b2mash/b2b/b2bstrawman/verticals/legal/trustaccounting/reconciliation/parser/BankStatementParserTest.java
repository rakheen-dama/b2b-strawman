package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

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
    assertThat(result.lines()).hasSize(10);

    // Verify first data line (opening balance line)
    var firstLine = result.lines().getFirst();
    assertThat(firstLine.date()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(firstLine.description()).isEqualTo("Opening Balance");
    assertThat(firstLine.amount()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(firstLine.runningBalance()).isEqualByComparingTo(new BigDecimal("150000.00"));

    // Verify a credit line
    var depositLine = result.lines().get(1);
    assertThat(depositLine.date()).isEqualTo(LocalDate.of(2026, 3, 3));
    assertThat(depositLine.description()).isEqualTo("Deposit from Smith & Associates");
    assertThat(depositLine.amount()).isEqualByComparingTo(new BigDecimal("25000.00"));
    assertThat(depositLine.reference()).isEqualTo("REF-SM-001");

    // Verify a debit line
    var paymentLine = result.lines().get(2);
    assertThat(paymentLine.amount()).isEqualByComparingTo(new BigDecimal("-8500.00"));
  }

  @Test
  void standardBankParser_handlesYyyyMmDdDates() throws IOException {
    var result = standardBankParser.parse(fixtureStream("standard-bank-sample.csv"));

    assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(result.lines()).hasSize(9);

    // Verify yyyy-MM-dd date parsing
    var depositLine = result.lines().get(1);
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
    assertThat(result.lines()).hasSize(9);

    // Verify "dd MMM yyyy" date parsing
    var depositLine = result.lines().get(1);
    assertThat(depositLine.date()).isEqualTo(LocalDate.of(2026, 3, 5));
    assertThat(depositLine.description()).isEqualTo("Deposit - Govender Matter");

    // Verify quoted field with comma is handled correctly
    var courierLine = result.lines().get(6);
    assertThat(courierLine.description()).isEqualTo("Disbursement, courier fees");
    assertThat(courierLine.amount()).isEqualByComparingTo(new BigDecimal("-450.00"));
  }

  @Test
  void genericParser_worksAsFallback() throws IOException {
    var result = genericParser.parse(fixtureStream("generic-sample.csv"));

    assertThat(result.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(result.periodEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(result.lines()).hasSize(7);

    // Verify the generic parser detects dd/MM/yyyy format
    var transferLine = result.lines().get(3);
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

  private InputStream fixtureStream(String fileName) {
    var stream = getClass().getResourceAsStream("/fixtures/trust/" + fileName);
    if (stream == null) {
      throw new IllegalStateException("Fixture not found: /fixtures/trust/" + fileName);
    }
    return stream;
  }
}
