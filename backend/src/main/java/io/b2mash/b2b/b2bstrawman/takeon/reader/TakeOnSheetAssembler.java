package io.b2mash.b2b.b2bstrawman.takeon.reader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Turns a stream of physical rows (cells in column order) into a {@link TakeOnSheet}: the shared
 * row-model rules of both readers (§17.3.1, C-8) — title rows above the header row ignored, header
 * names cleaned, blank rows skipped and counted, malformed rows counted, and the row and column
 * limits enforced while reading (so a huge sheet stops early).
 *
 * <p>Package-private helper; not a {@link TakeOnFileReader}. Never logs cell values.
 */
final class TakeOnSheetAssembler {

  /** Warnings kept verbatim; the rest are summarised in one line (bounded memory). */
  static final int MAX_WARNINGS = 100;

  /**
   * Blank and malformed rows after the header are cheap to send and are not data rows, so they get
   * their own cap: past it the sheet is refused as too many rows rather than scanned forever.
   */
  static final int MAX_SKIPPED_ROWS = TakeOnFileReader.MAX_DATA_ROWS;

  private final String sheetName;
  private final int headerRow;
  private final List<String> warnings = new ArrayList<>();
  private final List<TakeOnRawRow> rows = new ArrayList<>();
  private List<String> headers;
  private int malformedCount;
  private int malformedSkipped;
  private int blankSkipped;
  private int warningsDropped;

  TakeOnSheetAssembler(String sheetName, int headerRow) {
    if (headerRow < 1 || headerRow > TakeOnFileReader.MAX_HEADER_ROW) {
      throw new IllegalArgumentException(
          "The header row must be between 1 and " + TakeOnFileReader.MAX_HEADER_ROW + ".");
    }
    this.sheetName = sheetName;
    this.headerRow = headerRow;
  }

  void warn(String warning) {
    if (warnings.size() < MAX_WARNINGS) {
      warnings.add(warning);
    } else {
      warningsDropped++;
    }
  }

  /**
   * Accepts one physical row.
   *
   * @param rowNumber the 1-based row number the user sees
   * @param cells cell texts in column order (index = column); null entries read as blank
   * @param overflow a non-blank cell lies beyond the column limit (XLSX only)
   * @param errorCell the row holds an Excel error cell: counted malformed, but kept (C-8)
   */
  void accept(int rowNumber, List<String> cells, boolean overflow, boolean errorCell) {
    accept(rowNumber, cells, overflow, errorCell, Set.of());
  }

  /**
   * As {@link #accept(int, List, boolean, boolean)}, with the 0-based columns whose XLSX cell was a
   * number (their text is point-decimal whatever separator the user later chooses).
   */
  void accept(
      int rowNumber,
      List<String> cells,
      boolean overflow,
      boolean errorCell,
      Set<Integer> numericColumns) {
    if (rowNumber < headerRow) {
      return; // a title row above the headers
    }
    if (headers == null) {
      if (rowNumber > headerRow) {
        // The header row itself was absent (an empty XLSX row is not emitted).
        throw noHeader();
      }
      readHeaders(cells, overflow);
      return;
    }
    if (overflow || lastNonBlank(cells) >= headers.size()) {
      skipMalformed("Row " + rowNumber + " has more cells than the header row and was skipped.");
      return;
    }
    if (lastNonBlank(cells) < 0) {
      blankSkipped++;
      requireSkippedWithinLimit();
      return;
    }
    if (rows.size() >= TakeOnFileReader.MAX_DATA_ROWS) {
      throw tooManyRows();
    }
    var values = new LinkedHashMap<String, String>();
    var numericHeaders = new HashSet<String>();
    for (int i = 0; i < headers.size(); i++) {
      String value = cell(cells, i);
      values.put(headers.get(i), value);
      if (numericColumns.contains(i) && !value.isEmpty()) {
        numericHeaders.add(headers.get(i));
      }
    }
    if (errorCell) {
      malformedCount++;
    }
    rows.add(new TakeOnRawRow(rowNumber, values, numericHeaders));
  }

  /** A record that cannot be read at all (e.g. an unterminated CSV quote): counted and skipped. */
  void skipMalformed(String warning) {
    malformedCount++;
    malformedSkipped++;
    warn(warning);
    requireSkippedWithinLimit();
  }

  private void requireSkippedWithinLimit() {
    if (blankSkipped + malformedSkipped > MAX_SKIPPED_ROWS) {
      throw tooManyRows();
    }
  }

  private static TakeOnReadException tooManyRows() {
    return new TakeOnReadException(
        TakeOnReadException.Code.TOO_MANY_ROWS,
        "This sheet has more than 10 000 rows. Split it into smaller files.");
  }

  TakeOnSheet finish(TakeOnParseOptions detected) {
    if (headers == null) {
      throw noHeader();
    }
    if (rows.isEmpty() && malformedSkipped > 0) {
      throw new TakeOnReadException(
          TakeOnReadException.Code.ALL_ROWS_MALFORMED,
          "No row of this file could be read. Check that it is a .csv or .xlsx export and that"
              + " every row has no more cells than the header row.");
    }
    if (warningsDropped > 0) {
      warnings.add("%d more warnings are not shown.".formatted(warningsDropped));
    }
    return new TakeOnSheet(
        sheetName,
        List.copyOf(headers),
        List.copyOf(rows),
        malformedCount,
        blankSkipped,
        List.copyOf(warnings),
        detected);
  }

  private void readHeaders(List<String> cells, boolean overflow) {
    int width = lastNonBlank(cells) + 1;
    if (overflow || width > TakeOnFileReader.MAX_COLUMNS) {
      throw new TakeOnReadException(
          TakeOnReadException.Code.TOO_MANY_COLUMNS, "This sheet has more than 100 columns.");
    }
    if (width == 0) {
      throw noHeader();
    }
    var names = new ArrayList<String>(width);
    Set<String> taken = new HashSet<>();
    for (int i = 0; i < width; i++) {
      String name = cell(cells, i);
      if (name.isEmpty()) {
        name = "Column " + (i + 1);
      }
      String unique = name;
      int k = 2;
      while (taken.contains(unique)) {
        unique = name + " (" + k++ + ")";
      }
      if (!unique.equals(name)) {
        warn(
            k == 3
                ? "Two columns are named \"%s\"; the second is shown as \"%s\"."
                    .formatted(name, unique)
                : "More than two columns are named \"%s\"; this one is shown as \"%s\"."
                    .formatted(name, unique));
      }
      taken.add(unique);
      names.add(unique);
    }
    this.headers = names;
  }

  private TakeOnReadException noHeader() {
    return new TakeOnReadException(
        TakeOnReadException.Code.NO_HEADER,
        "The header row " + headerRow + " is empty or beyond the end of the sheet.");
  }

  private static String cell(List<String> cells, int index) {
    if (index >= cells.size()) {
      return "";
    }
    String value = cells.get(index);
    return value == null ? "" : value.strip();
  }

  private static int lastNonBlank(List<String> cells) {
    for (int i = cells.size() - 1; i >= 0; i--) {
      String value = cells.get(i);
      if (value != null && !value.isBlank()) {
        return i;
      }
    }
    return -1;
  }
}
