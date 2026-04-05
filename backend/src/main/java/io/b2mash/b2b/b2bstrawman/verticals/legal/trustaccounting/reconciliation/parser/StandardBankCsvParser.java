package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * CSV parser for Standard Bank bank statements. Detects files containing "Standard Bank" in the
 * header. Date format: yyyy-MM-dd. Columns: Date, Description, Reference, Amount, Balance.
 */
public class StandardBankCsvParser extends CsvBankStatementParser {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  @Override
  public boolean canParse(String fileName, String headerLine) {
    if (headerLine == null) {
      return false;
    }
    return headerLine.toUpperCase(Locale.ROOT).contains("STANDARD BANK");
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
