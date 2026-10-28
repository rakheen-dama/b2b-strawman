package io.b2mash.b2b.b2bstrawman.report;

import io.b2mash.b2b.b2bstrawman.report.ReportController.MatterProfitabilitySummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * RFC 4180 CSV for the Profitability page's "Matter Profitability" table: one row per {@link
 * MatterProfitabilitySummary} returned by {@code GET /api/reports/profitability}, in the same
 * order, with the page's column labels. UTF-8, CRLF line endings, no BOM and no metadata lines, so
 * the file opens cleanly in a spreadsheet.
 *
 * <p>Numbers are written as plain 2dp decimals (the native SQL leaves them at scale 16) with no
 * currency symbol, grouping or percent sign; the currency has its own column. A null value is an
 * empty cell, never {@code 0} or "N/A", so a missing cost is not mistaken for a zero cost.
 *
 * <p>Text cells are defused against spreadsheet formula injection (OWASP) by prefixing an
 * apostrophe when they start with a formula trigger. Numeric cells are never defused: they are
 * produced by this class from a {@link BigDecimal}, so they cannot carry a formula, and a negative
 * margin must stay {@code -150.00} to remain a number in the spreadsheet.
 */
@Component
public class ProfitabilityCsvExporter {

  static final String HEADER = "Matter,Client,Currency,Billable Hours,Revenue,Cost,Margin,Margin %";

  private static final String CRLF = "\r\n";
  private static final String FORMULA_TRIGGERS = "=+-@\t\r";

  public byte[] toCsv(List<MatterProfitabilitySummary> rows) {
    var sb = new StringBuilder(HEADER).append(CRLF);
    for (var row : rows) {
      sb.append(text(row.matterName()))
          .append(',')
          .append(text(row.clientName()))
          .append(',')
          .append(text(row.currency()))
          .append(',')
          .append(number(row.billableHours()))
          .append(',')
          .append(number(row.billableValue()))
          .append(',')
          .append(number(row.costValue()))
          .append(',')
          .append(number(row.margin()))
          .append(',')
          .append(number(row.marginPercent()))
          .append(CRLF);
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  static String number(BigDecimal value) {
    return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  static String text(String value) {
    if (value == null) {
      return "";
    }
    if (!value.isEmpty() && FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0) {
      value = "'" + value;
    }
    boolean needsQuoting =
        value.indexOf(',') >= 0
            || value.indexOf('"') >= 0
            || value.indexOf('\r') >= 0
            || value.indexOf('\n') >= 0;
    return needsQuoting ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
  }
}
