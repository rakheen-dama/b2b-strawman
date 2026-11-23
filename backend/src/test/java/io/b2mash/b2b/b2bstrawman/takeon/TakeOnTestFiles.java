package io.b2mash.b2b.b2bstrawman.takeon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Take-on test files generated in-test (Phase 93 / 653A): CSV text as bytes in UTF-8, Windows-1252
 * or with a BOM, and XLSX workbooks built with POI usermodel ({@code XSSFWorkbook} for small
 * fixtures, {@code SXSSFWorkbook} for large ones). Tests may use usermodel to <em>generate</em>
 * fixtures; the reader itself never does (C-7). Reused by the Family 3 integration tests.
 */
public final class TakeOnTestFiles {

  private TakeOnTestFiles() {}

  // --- CSV ---

  public static byte[] csv(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  public static byte[] csvWithBom(String text) {
    byte[] body = csv(text);
    byte[] out = new byte[body.length + 3];
    out[0] = (byte) 0xEF;
    out[1] = (byte) 0xBB;
    out[2] = (byte) 0xBF;
    System.arraycopy(body, 0, out, 3, body.length);
    return out;
  }

  public static byte[] csvWindows1252(String text) {
    return text.getBytes(Charset.forName("windows-1252"));
  }

  /** A comma CSV with a header row of {@code columns} names and {@code rows} data rows. */
  public static byte[] csvOfSize(int rows, int columns) {
    var sb = new StringBuilder();
    for (int c = 0; c < columns; c++) {
      sb.append(c == 0 ? "" : ",").append("H").append(c + 1);
    }
    sb.append("\r\n");
    for (int r = 0; r < rows; r++) {
      for (int c = 0; c < columns; c++) {
        sb.append(c == 0 ? "" : ",").append("v").append(r).append('_').append(c);
      }
      sb.append("\r\n");
    }
    return csv(sb.toString());
  }

  // --- XLSX ---

  /** A small workbook built with {@code XSSFWorkbook}; {@code build} adds sheets and cells. */
  public static byte[] xlsx(Consumer<XSSFWorkbook> build) {
    try (var workbook = new XSSFWorkbook()) {
      build.accept(workbook);
      return write(workbook);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** One-sheet workbook from a header and string rows. */
  public static byte[] xlsxTable(String sheetName, List<String> header, List<List<String>> rows) {
    return xlsx(
        workbook -> {
          Sheet sheet = workbook.createSheet(sheetName);
          writeRow(sheet.createRow(0), header);
          for (int i = 0; i < rows.size(); i++) {
            writeRow(sheet.createRow(i + 1), rows.get(i));
          }
        });
  }

  /**
   * A streamed ({@code SXSSFWorkbook}) one-sheet workbook with a header and {@code rows} data rows
   * of {@code columns} cells. With {@code uniqueTextLength > 0} every cell is a distinct
   * pseudo-random string of that length (incompressible enough to make a large file); otherwise
   * short cell texts.
   */
  public static byte[] xlsxLarge(int rows, int columns, int uniqueTextLength, long seed) {
    var random = new Random(seed);
    try (var workbook = new SXSSFWorkbook(100)) {
      workbook.setCompressTempFiles(true);
      Sheet sheet = workbook.createSheet("Data");
      Row header = sheet.createRow(0);
      for (int c = 0; c < columns; c++) {
        header.createCell(c).setCellValue("Column header " + (c + 1));
      }
      for (int r = 1; r <= rows; r++) {
        Row row = sheet.createRow(r);
        for (int c = 0; c < columns; c++) {
          row.createCell(c)
              .setCellValue(
                  uniqueTextLength > 0 ? randomText(random, uniqueTextLength) : "r" + r + "c" + c);
        }
      }
      return write(workbook);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** An OLE2 ({@code .xls}) workbook — must be refused by the XLSX reader. */
  public static byte[] xlsOle2() {
    try (var workbook = new HSSFWorkbook()) {
      workbook.createSheet("Old").createRow(0).createCell(0).setCellValue("Name");
      return write(workbook);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static void writeRow(Row row, List<String> values) {
    for (int c = 0; c < values.size(); c++) {
      row.createCell(c).setCellValue(values.get(c));
    }
  }

  private static byte[] write(Workbook workbook) throws IOException {
    var out = new ByteArrayOutputStream();
    workbook.write(out);
    return out.toByteArray();
  }

  private static final char[] ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

  private static String randomText(Random random, int length) {
    var chars = new char[length];
    for (int i = 0; i < length; i++) {
      chars[i] = ALPHABET[random.nextInt(ALPHABET.length)];
    }
    return new String(chars);
  }
}
