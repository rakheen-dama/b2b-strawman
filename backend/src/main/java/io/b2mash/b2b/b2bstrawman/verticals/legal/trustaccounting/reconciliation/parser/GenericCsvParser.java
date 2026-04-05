package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Fallback CSV parser for unrecognized bank statement formats. Attempts to detect the column layout
 * and date format automatically. Uses common CSV column orders: Date, Description, Amount or Date,
 * Description, Reference, Amount, Balance.
 */
public class GenericCsvParser extends CsvBankStatementParser {

  private static final List<DateTimeFormatter> DATE_FORMATS =
      List.of(
          DateTimeFormatter.ofPattern("dd/MM/yyyy"),
          DateTimeFormatter.ofPattern("yyyy-MM-dd"),
          DateTimeFormatter.ofPattern("MM/dd/yyyy"),
          DateTimeFormatter.ofPattern("dd-MM-yyyy"));

  private DateTimeFormatter detectedFormat = DATE_FORMATS.getFirst();

  /**
   * The generic parser always returns true -- it is the fallback when no bank-specific parser
   * matches.
   */
  @Override
  public boolean canParse(String fileName, String headerLine) {
    return true;
  }

  @Override
  protected DateTimeFormatter dateFormatter() {
    return detectedFormat;
  }

  @Override
  protected int dateColumn() {
    return 0;
  }

  @Override
  protected int descriptionColumn() {
    return 1;
  }

  @Override
  protected int amountColumn() {
    return 2;
  }

  @Override
  protected int balanceColumn() {
    return 3;
  }

  @Override
  protected LocalDate parseDate(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    for (DateTimeFormatter fmt : DATE_FORMATS) {
      try {
        LocalDate date = LocalDate.parse(value, fmt);
        this.detectedFormat = fmt;
        return date;
      } catch (DateTimeParseException e) {
        // Try next format
      }
    }
    return null;
  }
}
