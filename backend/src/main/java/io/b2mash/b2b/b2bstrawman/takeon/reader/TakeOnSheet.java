package io.b2mash.b2b.b2bstrawman.takeon.reader;

import java.util.List;

/**
 * The result of reading one sheet (or a CSV file): headers in file order, the kept data rows, the
 * malformed and blank-row counts, user-facing warnings, and the parse options actually used or
 * detected (encoding, delimiter, and a suggested decimal separator).
 */
public record TakeOnSheet(
    String sheetName,
    List<String> headers,
    List<TakeOnRawRow> rows,
    int malformedCount,
    int blankSkipped,
    List<String> warnings,
    TakeOnParseOptions detected) {}
