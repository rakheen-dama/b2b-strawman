package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * CSV parser for Nedbank bank statements. Detects files containing "Nedbank" in the header. Date
 * format: dd MMM yyyy (e.g., "15 Jan 2026"). Columns: Date, Description, Reference, Amount,
 * Balance.
 */
public class NedbankCsvParser extends CsvBankStatementParser {

  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

  @Override
  public boolean canParse(String fileName, String headerLine) {
    if (headerLine == null) {
      return false;
    }
    return headerLine.toUpperCase(Locale.ROOT).contains("NEDBANK");
  }

  @Override
  protected DateTimeFormatter dateFormatter() {
    return DATE_FORMAT;
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
    return 3;
  }

  @Override
  protected int referenceColumn() {
    return 2;
  }

  @Override
  protected int balanceColumn() {
    return 4;
  }
}
