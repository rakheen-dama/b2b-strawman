package io.b2mash.b2b.b2bstrawman.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.report.ReportController.MatterProfitabilitySummary;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Plain unit test for the Profitability page CSV formatting rules (no Spring context). */
class ProfitabilityCsvExporterTest {

  private static final String HEADER =
      "Matter,Client,Currency,Billable Hours,Revenue,Cost,Margin,Margin %";
  private static final String CRLF = "\r\n";

  private final ProfitabilityCsvExporter exporter = new ProfitabilityCsvExporter();

  @Test
  void emptyRows_writesHeaderOnlyWithCrlf() {
    assertThat(csv(List.of())).isEqualTo(HEADER + CRLF);
  }

  @Test
  void numericCells_areTwoDecimalPlainHalfUp_fromNativeSqlScale() {
    // Native SQL "/ 60.0" arithmetic yields scale-16 values; the CSV must not echo them verbatim.
    var row =
        row(
            "High Margin Matter",
            "Acme",
            "ZAR",
            "2.0000000000000000",
            "300.0000000000000000",
            "120.0000000000000000",
            "180.0000000000000000",
            "60.00");

    assertThat(csv(List.of(row)))
        .isEqualTo(
            HEADER + CRLF + "High Margin Matter,Acme,ZAR,2.00,300.00,120.00,180.00,60.00" + CRLF);
  }

  @Test
  void numericCells_roundHalfUpAndNeverUseScientificNotation() {
    var row = row("M", "C", "ZAR", "1.335", "1E+3", "0.005", "999.995", "33.333");

    assertThat(lines(csv(List.of(row)))[1]).isEqualTo("M,C,ZAR,1.34,1000.00,0.01,1000.00,33.33");
  }

  @Test
  void negativeMargin_staysUnprefixed() {
    var row = row("Loss Matter", "Acme", "ZAR", "1", "100", "250", "-150", "-150");

    assertThat(lines(csv(List.of(row)))[1])
        .isEqualTo("Loss Matter,Acme,ZAR,1.00,100.00,250.00,-150.00,-150.00");
  }

  @Test
  void nullNumericAndTextCells_areEmpty() {
    var row = row("No Cost Matter", null, "ZAR", "1", "150", null, null, null);

    assertThat(lines(csv(List.of(row)))[1]).isEqualTo("No Cost Matter,,ZAR,1.00,150.00,,,");
  }

  @Test
  void textWithCommaAndQuote_isQuotedWithDoubledQuotes() {
    var row = row("Smith, \"Jnr\" v RAF", "Acme", "ZAR", "1", "150", "60", "90", "60");

    assertThat(lines(csv(List.of(row)))[1])
        .isEqualTo("\"Smith, \"\"Jnr\"\" v RAF\",Acme,ZAR,1.00,150.00,60.00,90.00,60.00");
  }

  @Test
  void textWithLineBreak_isQuoted() {
    var row = row("Line one\nLine two", "Acme", "ZAR", "1", "150", "60", "90", "60");

    assertThat(csv(List.of(row)))
        .contains("\"Line one\nLine two\",Acme,ZAR,1.00,150.00,60.00,90.00,60.00" + CRLF);
  }

  @Test
  void textStartingWithFormulaTrigger_isDefusedWithApostrophe() {
    var rows =
        List.of(
            row("=SUM(A1)", "+27 Holdings", "ZAR", "1", "1", "1", "0", "0"),
            row("-Dash Matter", "@Acme", "ZAR", "1", "1", "1", "0", "0"),
            row("\tTabbed", "Plain", "ZAR", "1", "1", "1", "0", "0"));

    var lines = lines(csv(rows));

    assertThat(lines[1]).startsWith("'=SUM(A1),'+27 Holdings,ZAR,");
    assertThat(lines[2]).startsWith("'-Dash Matter,'@Acme,ZAR,");
    assertThat(lines[3]).startsWith("'\tTabbed,Plain,ZAR,");
  }

  @Test
  void defusedTextThatAlsoNeedsQuoting_isPrefixedInsideTheQuotes() {
    var row = row("=A1,B1", "Acme", "ZAR", "1", "1", "1", "0", "0");

    assertThat(lines(csv(List.of(row)))[1]).startsWith("\"'=A1,B1\",Acme,ZAR,");
  }

  @Test
  void textStartingWithLineBreak_isDefusedInsideTheQuotes() {
    var rows =
        List.of(
            row("\n=HYPERLINK(\"x\")", "Acme", "ZAR", "1", "1", "1", "0", "0"),
            row("\r=1+1", "Acme", "ZAR", "1", "1", "1", "0", "0"));

    var csv = csv(rows);

    assertThat(csv).contains(CRLF + "\"'\n=HYPERLINK(\"\"x\"\")\",Acme,ZAR,");
    assertThat(csv).contains(CRLF + "\"'\r=1+1\",Acme,ZAR,");
  }

  @Test
  void rows_areWrittenInListOrder() {
    var rows =
        List.of(
            row("Second By Name", "A", "ZAR", "1", "1", "1", "5", "5"),
            row("First By Name", "A", "ZAR", "1", "1", "1", "9", "9"));

    var lines = lines(csv(rows));

    assertThat(lines).hasSize(3);
    assertThat(lines[1]).startsWith("Second By Name,");
    assertThat(lines[2]).startsWith("First By Name,");
  }

  @Test
  void output_isUtf8WithoutBom() {
    var row = row("Ndlovu v RAF — Depth II", "Sipho", "ZAR", "2", "5000", null, null, null);

    byte[] bytes = exporter.toCsv(List.of(row));

    assertThat(bytes[0]).isEqualTo((byte) 'M');
    assertThat(new String(bytes, StandardCharsets.UTF_8))
        .contains("Ndlovu v RAF — Depth II,Sipho,ZAR,2.00,5000.00,,," + CRLF);
  }

  private String csv(List<MatterProfitabilitySummary> rows) {
    return new String(exporter.toCsv(rows), StandardCharsets.UTF_8);
  }

  private static String[] lines(String csv) {
    assertThat(csv).endsWith(CRLF);
    return csv.substring(0, csv.length() - CRLF.length()).split(CRLF, -1);
  }

  private static MatterProfitabilitySummary row(
      String matter,
      String client,
      String currency,
      String hours,
      String revenue,
      String cost,
      String margin,
      String marginPercent) {
    return new MatterProfitabilitySummary(
        UUID.randomUUID(),
        matter,
        client,
        currency,
        decimal(hours),
        decimal(revenue),
        decimal(cost),
        decimal(margin),
        decimal(marginPercent));
  }

  private static BigDecimal decimal(String value) {
    return value == null ? null : new BigDecimal(value);
  }
}
