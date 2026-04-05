package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.reconciliation.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for CSV-based bank statement parsers. Provides common CSV reading logic
 * including line splitting, amount parsing, and date parsing. Subclasses implement bank-specific
 * header detection, date formats, and column mappings.
 */
public abstract class CsvBankStatementParser implements BankStatementParser {

  private static final Logger log = LoggerFactory.getLogger(CsvBankStatementParser.class);

  /** Typed record for statement-level metadata extracted from header lines. */
  protected record StatementMetadata(
      LocalDate periodStart,
      LocalDate periodEnd,
      BigDecimal openingBalance,
      BigDecimal closingBalance) {}

  /**
   * Returns the date format used by this bank's CSV exports.
   *
   * @return the DateTimeFormatter for parsing transaction dates
   */
  protected abstract DateTimeFormatter dateFormatter();

  /**
   * Returns the column index (0-based) for the transaction date.
   *
   * @return column index
   */
  protected abstract int dateColumn();

  /**
   * Returns the column index (0-based) for the transaction description.
   *
   * @return column index
   */
  protected abstract int descriptionColumn();

  /**
   * Returns the column index (0-based) for the amount. The amount is signed: positive for credits,
   * negative for debits.
   *
   * @return column index
   */
  protected abstract int amountColumn();

  /**
   * Returns the column index (0-based) for the reference, or -1 if no reference column.
   *
   * @return column index, or -1
   */
  protected int referenceColumn() {
    return -1;
  }

  /**
   * Returns the column index (0-based) for the running balance, or -1 if not available.
   *
   * @return column index, or -1
   */
  protected int balanceColumn() {
    return -1;
  }

  /**
   * Returns the number of header/metadata lines to skip before data rows begin.
   *
   * @return number of lines to skip
   */
  protected int headerLinesToSkip() {
    return 1;
  }

  /**
   * Parses the statement-level metadata (period, balances) from the header lines. Subclasses can
   * override to extract this from bank-specific header rows. Default returns nulls for
   * period/balance fields, which are then derived from the transaction data.
   *
   * @param headerLines the header lines (up to headerLinesToSkip() lines)
   * @return metadata record with period and balance fields (null entries allowed)
   */
  protected StatementMetadata parseMetadata(List<String> headerLines) {
    return new StatementMetadata(null, null, null, null);
  }

  @Override
  public ParsedStatement parse(InputStream inputStream) throws IOException {
    var reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    var allLines = reader.lines().toList();

    if (allLines.isEmpty()) {
      return new ParsedStatement(null, null, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    int skip = headerLinesToSkip();
    var headerLines = allLines.subList(0, Math.min(skip, allLines.size()));
    var dataLines =
        skip < allLines.size() ? allLines.subList(skip, allLines.size()) : List.<String>of();

    StatementMetadata metadata = parseMetadata(headerLines);
    var periodStart = metadata.periodStart();
    var periodEnd = metadata.periodEnd();
    var openingBalance = metadata.openingBalance();
    var closingBalance = metadata.closingBalance();

    List<ParsedStatementLine> parsedLines = new ArrayList<>();
    int malformedCount = 0;

    for (String line : dataLines) {
      if (line.isBlank()) {
        continue;
      }
      String[] columns = splitCsvLine(line);
      try {
        var parsedLine = parseLine(columns);
        if (parsedLine != null) {
          parsedLines.add(parsedLine);
        } else {
          malformedCount++;
        }
      } catch (NumberFormatException | DateTimeParseException e) {
        malformedCount++;
        log.debug("Skipping malformed CSV line: {}", line, e);
      }
    }

    if (parsedLines.isEmpty() && malformedCount > 0) {
      throw new BankStatementParseException(
          "All " + malformedCount + " data lines were malformed; no valid transactions parsed");
    }

    // Derive period from data if not extracted from metadata
    if (!parsedLines.isEmpty()) {
      if (periodStart == null) {
        periodStart =
            parsedLines.stream()
                .map(ParsedStatementLine::date)
                .min(LocalDate::compareTo)
                .orElse(null);
      }
      if (periodEnd == null) {
        periodEnd =
            parsedLines.stream()
                .map(ParsedStatementLine::date)
                .max(LocalDate::compareTo)
                .orElse(null);
      }
      if (openingBalance == null && parsedLines.getFirst().runningBalance() != null) {
        openingBalance =
            parsedLines.getFirst().runningBalance().subtract(parsedLines.getFirst().amount());
      }
      if (closingBalance == null && parsedLines.getLast().runningBalance() != null) {
        closingBalance = parsedLines.getLast().runningBalance();
      }
    }

    if (openingBalance == null) {
      openingBalance = BigDecimal.ZERO;
    }
    if (closingBalance == null) {
      closingBalance = BigDecimal.ZERO;
    }

    return new ParsedStatement(periodStart, periodEnd, openingBalance, closingBalance, parsedLines);
  }

  private ParsedStatementLine parseLine(String[] columns) {
    if (columns.length <= Math.max(dateColumn(), Math.max(descriptionColumn(), amountColumn()))) {
      return null;
    }

    LocalDate date = parseDate(columns[dateColumn()].trim());
    if (date == null) {
      return null;
    }

    String description = columns[descriptionColumn()].trim();
    if (description.isEmpty()) {
      return null;
    }

    BigDecimal amount = parseAmount(columns[amountColumn()].trim());
    if (amount == null) {
      return null;
    }

    String reference = null;
    if (referenceColumn() >= 0 && referenceColumn() < columns.length) {
      String ref = columns[referenceColumn()].trim();
      reference = ref.isEmpty() ? null : ref;
    }

    BigDecimal balance = null;
    if (balanceColumn() >= 0 && balanceColumn() < columns.length) {
      balance = parseAmount(columns[balanceColumn()].trim());
    }

    return new ParsedStatementLine(date, description, reference, amount, balance);
  }

  protected LocalDate parseDate(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return LocalDate.parse(value, dateFormatter());
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  protected BigDecimal parseAmount(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      // Remove currency symbols, spaces, and thousands separators
      String cleaned = value.replaceAll("[R$€£\\s]", "").replace(",", "");
      return new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Splits a CSV line into fields, respecting quoted fields that may contain commas.
   *
   * @param line the CSV line
   * @return the fields
   */
  protected String[] splitCsvLine(String line) {
    List<String> fields = new ArrayList<>();
    boolean inQuotes = false;
    var current = new StringBuilder();

    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          // Escaped quote ("") inside a quoted field — append a literal quote
          current.append('"');
          i++; // skip the second quote
        } else {
          inQuotes = !inQuotes;
        }
      } else if (c == ',' && !inQuotes) {
        fields.add(current.toString());
        current = new StringBuilder();
      } else {
        current.append(c);
      }
    }
    fields.add(current.toString());

    return fields.toArray(new String[0]);
  }
}
