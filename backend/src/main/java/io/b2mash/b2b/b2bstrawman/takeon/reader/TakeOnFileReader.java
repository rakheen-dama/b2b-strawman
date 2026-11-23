package io.b2mash.b2b.b2bstrawman.takeon.reader;

import java.util.List;

/**
 * One row model for CSV and XLSX (§17.3.1, FD-17): both readers produce {@code Map<header, text>}
 * per row; typing happens later, at mapping. Plain classes — no Spring, no logging of values.
 */
public sealed interface TakeOnFileReader permits CsvTakeOnReader, XlsxTakeOnReader {

  long MAX_FILE_BYTES = 10_485_760L;
  int MAX_DATA_ROWS = 10_000;
  int MAX_COLUMNS = 100;
  int MAX_HEADER_ROW = 50;

  /**
   * Defence in depth: the service checks the byte size before reading (C-10, 422); the readers
   * refuse an oversized file too, before decoding or unzipping anything.
   */
  static void requireWithinSizeLimit(byte[] file) {
    if (file.length > MAX_FILE_BYTES) {
      throw new TakeOnReadException(
          TakeOnReadException.Code.FILE_TOO_LARGE,
          "This file is larger than 10 MB. Split it into smaller files.");
    }
  }

  /** A sheet in workbook order and whether it is hidden (or very hidden). */
  record SheetName(String name, boolean hidden) {}

  /** The sheets of the file, in workbook order. CSV: one synthetic visible sheet "CSV". */
  List<SheetName> sheetNames(byte[] file);

  /**
   * Reads one sheet. Rows above {@code headerRow} (1–50) are title rows and ignored.
   *
   * @throws TakeOnReadException when the file cannot be read or breaks a limit
   */
  TakeOnSheet read(byte[] file, String sheetName, int headerRow, TakeOnParseOptions options);
}
