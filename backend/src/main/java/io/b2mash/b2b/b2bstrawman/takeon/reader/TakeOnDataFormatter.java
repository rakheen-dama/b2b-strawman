package io.b2mash.b2b.b2bstrawman.takeon.reader;

import java.math.BigDecimal;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.NumberToTextConverter;

/**
 * The take-on number formatter for the streaming XLSX reader (§17.3.1, FD-17): a date-formatted
 * cell becomes an ISO {@code yyyy-MM-dd} date; any other number becomes Excel's own full-precision
 * text ({@link NumberToTextConverter}), ignoring display formats — {@code #,##0} showing {@code
 * 1,235} still reads {@code 1234.56}. Never {@code java.util.Date} / {@code Calendar}.
 *
 * <p>A percent-formatted cell reads as the percentage it displays: Excel stores {@code 7.50%} as
 * {@code 0.075}, read here as {@code 7.5} — the same value a CSV {@code "7.50%"} parses to (a
 * percent field means the displayed percentage, whatever the file type).
 *
 * <p>The reader asks {@link #takeNumeric()} after each cell whether it was a number, so the user's
 * decimal separator later applies to text cells only. One formatter per sheet read (not shared).
 */
public final class TakeOnDataFormatter extends DataFormatter {

  private final boolean use1904Windowing;
  private boolean numeric;

  public TakeOnDataFormatter() {
    this(false);
  }

  /** {@code use1904Windowing}: the workbook's {@code <workbookPr date1904="1"/>}. */
  public TakeOnDataFormatter(boolean use1904Windowing) {
    this.use1904Windowing = use1904Windowing;
  }

  @Override
  public String formatRawCellContents(double value, int formatIndex, String formatString) {
    return formatRawCellContents(value, formatIndex, formatString, use1904Windowing);
  }

  @Override
  public String formatRawCellContents(
      double value, int formatIndex, String formatString, boolean use1904Windowing) {
    if (formatString != null
        && DateUtil.isADateFormat(formatIndex, formatString)
        && DateUtil.isValidExcelDate(value)) {
      return DateUtil.getLocalDateTime(value, use1904Windowing).toLocalDate().toString();
    }
    numeric = true;
    String text = NumberToTextConverter.toText(value);
    if (isPercentFormat(formatString)) {
      return new BigDecimal(text).movePointRight(2).stripTrailingZeros().toPlainString();
    }
    return text;
  }

  /** Whether the last formatted cell was a (non-date) number; resets the flag. */
  public boolean takeNumeric() {
    boolean was = numeric;
    numeric = false;
    return was;
  }

  /** A {@code %} in the format code outside quoted text and backslash escapes. */
  static boolean isPercentFormat(String formatString) {
    if (formatString == null) {
      return false;
    }
    boolean quoted = false;
    for (int i = 0; i < formatString.length(); i++) {
      char c = formatString.charAt(i);
      if (c == '"') {
        quoted = !quoted;
      } else if (!quoted && c == '\\') {
        i++;
      } else if (!quoted && c == '%') {
        return true;
      }
    }
    return false;
  }
}
