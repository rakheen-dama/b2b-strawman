package io.b2mash.b2b.b2bstrawman.takeon.reader;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a take-on CSV file (§17.3.1): UTF-8 BOM stripped; strict UTF-8 with a Windows-1252 fallback
 * (and a warning); delimiter detected on the header line among {@code , ; \t |} unless given; RFC
 * 4180 quoting including {@code ""} escapes and quoted line breaks.
 *
 * <p>Disciplines of {@code CsvBankStatementParser} (blank lines skipped, malformed rows counted not
 * fatal, fatal only if every row is malformed) with new helpers: that parser's {@code splitCsvLine}
 * has no quoted newlines.
 *
 * <p>When the delimiter is detected as {@code ;}, {@link TakeOnSheet#detected()} suggests the
 * {@code COMMA} decimal separator (semicolon CSVs are what Excel writes in decimal-comma locales);
 * the suggestion is only a preview default.
 */
public final class CsvTakeOnReader implements TakeOnFileReader {

  public static final String SHEET_NAME = "CSV";
  static final String WINDOWS_1252 = "windows-1252";
  private static final char[] CANDIDATES = {',', ';', '\t', '|'};

  @Override
  public List<SheetName> sheetNames(byte[] file) {
    return List.of(new SheetName(SHEET_NAME, false));
  }

  @Override
  public TakeOnSheet read(
      byte[] file, String sheetName, int headerRow, TakeOnParseOptions options) {
    if (sheetName != null && !SHEET_NAME.equalsIgnoreCase(sheetName)) {
      throw new TakeOnReadException(
          TakeOnReadException.Code.SHEET_NOT_FOUND, "This file has no sheet named that.");
    }
    TakeOnFileReader.requireWithinSizeLimit(file);
    var given = options == null ? TakeOnParseOptions.defaults() : options;
    for (byte b : file) {
      if (b == 0) {
        throw new TakeOnReadException(
            TakeOnReadException.Code.UNSUPPORTED_FORMAT,
            "Save the file as .xlsx or .csv and upload it again.");
      }
    }
    var assembler = new TakeOnSheetAssembler(SHEET_NAME, headerRow);

    int offset = hasUtf8Bom(file) ? 3 : 0;
    String encoding = given.encoding();
    String text;
    if (isUtf8(encoding)) {
      try {
        text = decodeStrictUtf8(file, offset);
        encoding = TakeOnParseOptions.UTF_8;
      } catch (CharacterCodingException e) {
        text = new String(file, offset, file.length - offset, Charset.forName(WINDOWS_1252));
        encoding = WINDOWS_1252;
        assembler.warn(
            "This file is not UTF-8. It was read as Windows-1252 — check accented names in the"
                + " preview.");
      }
    } else {
      text = new String(file, offset, file.length - offset, charset(encoding));
    }

    Character delimiter = given.delimiter();
    var detected = given;
    if (delimiter == null) {
      delimiter = detectDelimiter(text, headerRow);
      if (delimiter == ';') {
        detected = detected.withDecimalSeparator(TakeOnParseOptions.DecimalSeparator.COMMA);
      }
    }
    detected = detected.withDelimiter(delimiter).withEncoding(encoding);

    parse(text, delimiter, assembler);
    return assembler.finish(detected);
  }

  // --- RFC 4180 state machine ---

  private static void parse(String text, char delimiter, TakeOnSheetAssembler assembler) {
    var cells = new ArrayList<String>();
    var field = new StringBuilder();
    boolean inQuotes = false;
    boolean recordStarted = false;
    int recordNumber = 1;
    int length = text.length();
    for (int i = 0; i < length; i++) {
      char c = text.charAt(i);
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < length && text.charAt(i + 1) == '"') {
            field.append('"');
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          field.append(c); // delimiters and line breaks inside quotes are data
        }
        continue;
      }
      if (c == '"') {
        inQuotes = true;
        recordStarted = true;
      } else if (c == delimiter) {
        cells.add(field.toString());
        field.setLength(0);
        recordStarted = true;
      } else if (c == '\r' || c == '\n') {
        if (c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
          i++;
        }
        cells.add(field.toString());
        field.setLength(0);
        assembler.accept(recordNumber++, cells, false, false);
        cells = new ArrayList<>();
        recordStarted = false;
      } else {
        field.append(c);
        recordStarted = true;
      }
    }
    if (inQuotes) {
      assembler.skipMalformed(
          "Row " + recordNumber + " has a quote that is never closed and was skipped.");
    } else if (recordStarted) {
      cells.add(field.toString());
      assembler.accept(recordNumber, cells, false, false);
    }
  }

  /**
   * Counts each candidate delimiter outside quotes on the header record and picks the most
   * frequent; a tie or no candidate at all means {@code ,}.
   */
  static char detectDelimiter(String text, int headerRow) {
    int[] counts = new int[CANDIDATES.length];
    boolean inQuotes = false;
    int record = 1;
    int length = text.length();
    for (int i = 0; i < length && record <= headerRow; i++) {
      char c = text.charAt(i);
      if (c == '"') {
        inQuotes = !inQuotes; // an escaped "" toggles twice
      } else if (!inQuotes && (c == '\r' || c == '\n')) {
        if (c == '\r' && i + 1 < length && text.charAt(i + 1) == '\n') {
          i++;
        }
        record++;
      } else if (!inQuotes && record == headerRow) {
        for (int k = 0; k < CANDIDATES.length; k++) {
          if (c == CANDIDATES[k]) {
            counts[k]++;
          }
        }
      }
    }
    int best = 0;
    for (int k = 1; k < CANDIDATES.length; k++) {
      if (counts[k] > counts[best]) {
        best = k;
      }
    }
    return counts[best] == 0 ? ',' : CANDIDATES[best];
  }

  // --- decoding ---

  private static boolean hasUtf8Bom(byte[] file) {
    return file.length >= 3
        && (file[0] & 0xFF) == 0xEF
        && (file[1] & 0xFF) == 0xBB
        && (file[2] & 0xFF) == 0xBF;
  }

  private static boolean isUtf8(String encoding) {
    String normalised = encoding.trim().toUpperCase(Locale.ROOT).replace("_", "-");
    return normalised.equals("UTF-8") || normalised.equals("UTF8");
  }

  private static String decodeStrictUtf8(byte[] file, int offset) throws CharacterCodingException {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(file, offset, file.length - offset))
        .toString();
  }

  private static Charset charset(String encoding) {
    try {
      return Charset.forName(encoding.trim());
    } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
      throw new TakeOnReadException(
          TakeOnReadException.Code.UNREADABLE,
          "This file's text encoding is not supported. Save it as UTF-8 and upload it again.",
          e);
    }
  }
}
