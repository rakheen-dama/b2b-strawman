package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * CSV parser for First National Bank (FNB) bank statements. Detects files containing "FNB" or
 * "First National" in the header. Date format: dd/MM/yyyy. Columns: Date, Description, Amount,
 * Balance, Reference.
 */
public class FnbCsvParser extends CsvBankStatementParser {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  @Override
  public boolean canParse(String fileName, String headerLine) {
    if (headerLine == null) {
      return false;
    }
    String upper = headerLine.toUpperCase(Locale.ROOT);
    return upper.contains("FNB") || upper.contains("FIRST NATIONAL");
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
    return 2;
  }

  @Override
  protected int balanceColumn() {
    return 3;
  }

  @Override
  protected int referenceColumn() {
    return 4;
  }
}
