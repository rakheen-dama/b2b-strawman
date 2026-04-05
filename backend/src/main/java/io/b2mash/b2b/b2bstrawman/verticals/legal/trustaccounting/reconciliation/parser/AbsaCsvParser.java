package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * CSV parser for ABSA bank statements. Detects files containing "Absa" or "ABSA" in the header.
 * Date format: dd/MM/yyyy. Columns: Date, Description, Reference, Amount, Balance.
 */
public class AbsaCsvParser extends CsvBankStatementParser {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  @Override
  public boolean canParse(String fileName, String headerLine) {
    if (headerLine == null) {
      return false;
    }
    String upper = headerLine.toUpperCase(Locale.ROOT);
    return upper.contains("ABSA");
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
