package io.b2mash.b2b.b2bstrawman.takeon.reader;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.NumberToTextConverter;

/**
 * The take-on number formatter for the streaming XLSX reader (§17.3.1, FD-17): a date-formatted
 * cell becomes an ISO {@code yyyy-MM-dd} date; any other number becomes Excel's own full-precision
 * text ({@link NumberToTextConverter}), ignoring display formats — {@code #,##0} showing {@code
 * 1,235} still reads {@code 1234.56}. Never {@code java.util.Date} / {@code Calendar}.
 */
public final class TakeOnDataFormatter extends DataFormatter {

  private final boolean use1904Windowing;

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
    return NumberToTextConverter.toText(value);
  }
}
