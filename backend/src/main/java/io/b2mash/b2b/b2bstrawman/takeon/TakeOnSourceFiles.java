package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.takeon.reader.CsvTakeOnReader;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnFileReader;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnReadException;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnSheet;
import io.b2mash.b2b.b2bstrawman.takeon.reader.XlsxTakeOnReader;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reads take-on files: picks the reader of a format, maps every reader refusal to a 422 problem
 * (C-10), and fetches a stored source's bytes back from S3 for the preview and the sheet picker
 * (C-6 — the schema keeps no headers, and {@code raw_values} jsonb keeps no column order).
 */
@Component
public class TakeOnSourceFiles {

  private static final TakeOnFileReader CSV = new CsvTakeOnReader();
  private static final TakeOnFileReader XLSX = new XlsxTakeOnReader();

  private final StorageService storageService;

  public TakeOnSourceFiles(StorageService storageService) {
    this.storageService = storageService;
  }

  public static TakeOnFileReader readerFor(TakeOnSource.FileFormat format) {
    return format == TakeOnSource.FileFormat.XLSX ? XLSX : CSV;
  }

  /** The sheets of a file, in workbook order (422 when it cannot be opened). */
  public List<TakeOnFileReader.SheetName> sheetNames(byte[] bytes, TakeOnSource.FileFormat format) {
    try {
      return readerFor(format).sheetNames(bytes);
    } catch (TakeOnReadException e) {
      throw TakeOnProblems.unreadable(e);
    }
  }

  /** Reads one sheet (422 on any reader refusal: limits, no header, unreadable). */
  public TakeOnSheet read(
      byte[] bytes,
      TakeOnSource.FileFormat format,
      String sheetName,
      int headerRow,
      TakeOnParseOptions options) {
    try {
      return readerFor(format).read(bytes, sheetName, headerRow, options);
    } catch (TakeOnReadException e) {
      throw TakeOnProblems.unreadable(e);
    }
  }

  /**
   * As {@link #read}, but a file that opens and yields no row at {@code headerRow} — every row has
   * more cells than that "header" (a title line above the real header row), or the sheet is empty
   * in a workbook of several sheets — reads as no rows with {@link #headerRowWarning} instead of a
   * 422, so the upload is kept and the wizard asks for the sheet and header row (OBS F-1). Every
   * other refusal (unreadable, limits, an empty single-sheet file) is still a 422.
   */
  public TakeOnSheet readOrNoRows(
      byte[] bytes,
      TakeOnSource.FileFormat format,
      String sheetName,
      int headerRow,
      TakeOnParseOptions options,
      int sheetCount) {
    try {
      return readerFor(format).read(bytes, sheetName, headerRow, options);
    } catch (TakeOnReadException e) {
      boolean noRowsHere =
          e.getCode() == TakeOnReadException.Code.ALL_ROWS_MALFORMED
              || (e.getCode() == TakeOnReadException.Code.NO_HEADER && sheetCount > 1);
      if (!noRowsHere) {
        throw TakeOnProblems.unreadable(e);
      }
      return new TakeOnSheet(
          sheetName, List.of(), List.of(), 0, 0, List.of(headerRowWarning(headerRow)), options);
    }
  }

  /** The warning of a file read as no rows at {@code headerRow} ({@link #readOrNoRows}). */
  public static String headerRowWarning(int headerRow) {
    return ("No row could be read with row %d as the header row. Choose the sheet and the row"
            + " that holds the column names.")
        .formatted(headerRow);
  }

  /**
   * Re-reads a stored source with its current sheet, header row and parse options; a source kept
   * with no rows ({@link #readOrNoRows}) re-reads as no rows with the same warning.
   */
  public TakeOnSheet reread(TakeOnSource source) {
    return readOrNoRows(
        download(source),
        source.getFileFormat(),
        source.getSheetName(),
        source.getHeaderRow(),
        readOptions(source),
        source.getSheetNames() == null ? 1 : source.getSheetNames().size());
  }

  /** The raw bytes of a stored source; 404 {@code TAKE_ON_FILE_PURGED} once purged. */
  public byte[] download(TakeOnSource source) {
    if (source.isPurged() || source.getS3Key() == null) {
      throw TakeOnProblems.fileGone();
    }
    return storageService.download(source.getS3Key());
  }

  /**
   * The options a re-read uses: the stored ones, but the CSV delimiter and encoding re-detected
   * only when none was recorded.
   */
  static TakeOnParseOptions readOptions(TakeOnSource source) {
    return TakeOnParseOptions.fromMap(source.getParseOptions());
  }

  /** The sheet list as stored in {@code take_on_sources.sheet_names}. */
  static List<Map<String, Object>> sheetNamesJson(List<TakeOnFileReader.SheetName> sheets) {
    return sheets.stream()
        .map(s -> Map.<String, Object>of("name", s.name(), "hidden", s.hidden()))
        .toList();
  }

  /** The only visible sheet, else the first visible one, else the first. */
  static String defaultSheet(List<TakeOnFileReader.SheetName> sheets) {
    return sheets.stream()
        .filter(s -> !s.hidden())
        .map(TakeOnFileReader.SheetName::name)
        .findFirst()
        .orElse(sheets.isEmpty() ? null : sheets.getFirst().name());
  }
}
