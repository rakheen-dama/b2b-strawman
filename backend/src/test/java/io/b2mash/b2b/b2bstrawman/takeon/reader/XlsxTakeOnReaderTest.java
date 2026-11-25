package io.b2mash.b2b.b2bstrawman.takeon.reader;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestFiles;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnFileReader.SheetName;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiConsumer;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Phase 93 / 653A (task 653.6, FD-17, C-7, C-9): the streaming XLSX take-on reader. Fixtures are
 * generated in-test with POI usermodel; the reader itself uses the event API only.
 */
class XlsxTakeOnReaderTest {

  private final XlsxTakeOnReader reader = new XlsxTakeOnReader();

  private TakeOnSheet read(byte[] file, String sheet) {
    return reader.read(file, sheet, 1, TakeOnParseOptions.defaults());
  }

  /**
   * A one-sheet workbook whose header is {@code header} and whose row 2 is built by {@code row}.
   */
  private static byte[] oneRow(List<String> header, BiConsumer<XSSFWorkbook, Row> row) {
    return TakeOnTestFiles.xlsx(
        workbook -> {
          var sheet = workbook.createSheet("Clients");
          TakeOnTestFiles.writeRow(sheet.createRow(0), header);
          row.accept(workbook, sheet.createRow(1));
        });
  }

  private static TakeOnReadException.Code codeOf(Runnable read) {
    try {
      read.run();
    } catch (TakeOnReadException e) {
      return e.getCode();
    }
    throw new AssertionError("expected a TakeOnReadException");
  }

  @Test
  void sheetsInWorkbookOrder_withHiddenAndVeryHiddenFlags() {
    byte[] file =
        TakeOnTestFiles.xlsx(
            workbook -> {
              workbook.createSheet("Clients").createRow(0).createCell(0).setCellValue("Name");
              workbook.createSheet("Notes").createRow(0).createCell(0).setCellValue("Note");
              workbook.createSheet("Lookup").createRow(0).createCell(0).setCellValue("Code");
              workbook.setSheetHidden(1, true);
              workbook.setSheetVisibility(2, SheetVisibility.VERY_HIDDEN);
            });
    assertThat(reader.sheetNames(file))
        .containsExactly(
            new SheetName("Clients", false),
            new SheetName("Notes", true),
            new SheetName("Lookup", true));
    assertThat(read(file, "Notes").headers()).containsExactly("Note");
    assertThat(read(file, null).sheetName())
        .as("first visible sheet by default")
        .isEqualTo("Clients");
  }

  @Test
  void aPercentCellReadsAsTheDisplayedPercentageAndNumbersAreMarkedNumeric() {
    // 653A review: Excel stores 7.50% as 0.075; a CSV "7.50%" means 7.5 — one meaning for both.
    byte[] file =
        oneRow(
            List.of("Rate", "Principal", "Account", "Escaped"),
            (workbook, row) -> {
              CellStyle percent = workbook.createCellStyle();
              percent.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
              var rate = row.createCell(0);
              rate.setCellValue(0.075);
              rate.setCellStyle(percent);
              CellStyle grouped = workbook.createCellStyle();
              grouped.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
              var principal = row.createCell(1);
              principal.setCellValue(10000.5);
              principal.setCellStyle(grouped);
              row.createCell(2).setCellValue("0001");
              CellStyle literal = workbook.createCellStyle();
              literal.setDataFormat(workbook.createDataFormat().getFormat("0\"%\""));
              var escaped = row.createCell(3);
              escaped.setCellValue(12);
              escaped.setCellStyle(literal);
            });
    var row = read(file, "Clients").rows().getFirst();
    assertThat(row.values())
        .containsEntry("Rate", "7.5")
        .containsEntry("Principal", "10000.5")
        .containsEntry("Account", "0001")
        .as("a quoted %% is literal text, not a percent format")
        .containsEntry("Escaped", "12");
    assertThat(row.numericHeaders()).containsExactlyInAnyOrder("Rate", "Principal", "Escaped");
  }

  @Test
  void dateFormattedCellReadsAsIsoDate() {
    byte[] file =
        oneRow(
            List.of("Name", "Opened"),
            (workbook, row) -> {
              CellStyle date = workbook.createCellStyle();
              date.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
              row.createCell(0).setCellValue("A");
              var cell = row.createCell(1);
              cell.setCellValue(LocalDate.of(2026, 10, 31));
              cell.setCellStyle(date);
            });
    assertThat(read(file, "Clients").rows().getFirst().values())
        .containsEntry("Opened", "2026-10-31");
  }

  @Test
  void displayFormatIsIgnored_numbersReadAtFullPrecision() {
    byte[] file =
        oneRow(
            List.of("Balance", "Count"),
            (workbook, row) -> {
              CellStyle grouped = workbook.createCellStyle();
              grouped.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
              var cell = row.createCell(0);
              cell.setCellValue(1234.56);
              cell.setCellStyle(grouped);
              row.createCell(1).setCellValue(42);
            });
    assertThat(read(file, "Clients").rows().getFirst().values())
        .containsEntry("Balance", "1234.56")
        .containsEntry("Count", "42");
  }

  @Test
  void numericCellInADateColumn_readsAsTheNumber() {
    byte[] file =
        oneRow(List.of("Opened"), (workbook, row) -> row.createCell(0).setCellValue(45230));
    // The value parser refuses it as NUMERIC_IN_DATE (TakeOnValueParserTest).
    assertThat(read(file, "Clients").rows().getFirst().values()).containsEntry("Opened", "45230");
  }

  @Test
  void formulaWithACachedValue_readsTheCachedValue_withoutOne_readsBlank() {
    byte[] file =
        oneRow(
            List.of("A", "Cached", "Uncached", "Name"),
            (workbook, row) -> {
              row.createCell(0).setCellValue(21);
              var cached = row.createCell(1);
              cached.setCellFormula("A2*2");
              workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cached);
              row.createCell(2).setCellFormula("A2*3"); // never evaluated: no <v>
              row.createCell(3).setCellValue("Kept");
            });
    var values = read(file, "Clients").rows().getFirst().values();
    assertThat(values).containsEntry("Cached", "42").containsEntry("Uncached", "");
    assertThat(values).containsEntry("Name", "Kept");
  }

  @Test
  void errorCellKeepsItsToken_rowKept_countedMalformed() {
    byte[] file =
        oneRow(
            List.of("Name", "Balance"),
            (workbook, row) -> {
              row.createCell(0).setCellValue("A");
              row.createCell(1).setCellErrorValue(FormulaError.REF.getCode());
            });
    var sheet = read(file, "Clients");
    assertThat(sheet.rows()).hasSize(1);
    assertThat(sheet.rows().getFirst().values()).containsEntry("Balance", "#REF!");
    assertThat(sheet.malformedCount()).isEqualTo(1);
  }

  @Test
  void booleansReadLowerCase_mergedCellsCarryTheValueTopLeftOnly() {
    byte[] file =
        oneRow(
            List.of("Name", "Merged", "Neighbour", "Active"),
            (workbook, row) -> {
              row.createCell(0).setCellValue("A");
              row.createCell(1).setCellValue("Wide");
              row.getSheet().addMergedRegion(new CellRangeAddress(1, 1, 1, 2));
              row.createCell(3).setCellValue(true);
            });
    assertThat(read(file, "Clients").rows().getFirst().values())
        .containsEntry("Merged", "Wide")
        .containsEntry("Neighbour", "")
        .containsEntry("Active", "true");
  }

  @Test
  void titleRowsAboveTheHeaderRowAreIgnored_blankRowsCounted() {
    byte[] file =
        TakeOnTestFiles.xlsx(
            workbook -> {
              var sheet = workbook.createSheet("Clients");
              sheet.createRow(0).createCell(0).setCellValue("Client list — exported 31 Oct");
              TakeOnTestFiles.writeRow(sheet.createRow(2), List.of("Name", "Email"));
              TakeOnTestFiles.writeRow(sheet.createRow(3), List.of("A", "a@example.test"));
              TakeOnTestFiles.writeRow(sheet.createRow(4), List.of("", ""));
              TakeOnTestFiles.writeRow(sheet.createRow(5), List.of("B", "b@example.test"));
            });
    var sheet = reader.read(file, "Clients", 3, TakeOnParseOptions.defaults());
    assertThat(sheet.headers()).containsExactly("Name", "Email");
    assertThat(sheet.rows()).extracting(TakeOnRawRow::rowNumber).containsExactly(4, 6);
    assertThat(sheet.blankSkipped()).isEqualTo(1);
  }

  @Test
  void unknownSheet_andAbsentHeaderRow_areRefused() {
    byte[] file = TakeOnTestFiles.xlsxTable("Clients", List.of("Name"), List.of(List.of("A")));
    assertThat(codeOf(() -> read(file, "Nope")))
        .isEqualTo(TakeOnReadException.Code.SHEET_NOT_FOUND);
    assertThat(codeOf(() -> reader.read(file, "Clients", 9, TakeOnParseOptions.defaults())))
        .isEqualTo(TakeOnReadException.Code.NO_HEADER);
  }

  @Test
  void blankHeaderRowWithContentBelow_isHeaderRowBlank() {
    byte[] file =
        TakeOnTestFiles.xlsx(
            workbook -> {
              var sheet = workbook.createSheet("Clients");
              sheet.createRow(1).createCell(0).setCellValue("Client list — exported 31 Oct");
              TakeOnTestFiles.writeRow(sheet.createRow(3), List.of("Name", "Email"));
              TakeOnTestFiles.writeRow(sheet.createRow(4), List.of("A", "a@example.test"));
            });
    assertThat(codeOf(() -> reader.read(file, "Clients", 1, TakeOnParseOptions.defaults())))
        .isEqualTo(TakeOnReadException.Code.HEADER_ROW_BLANK);
    assertThat(codeOf(() -> reader.read(file, "Clients", 3, TakeOnParseOptions.defaults())))
        .isEqualTo(TakeOnReadException.Code.HEADER_ROW_BLANK);
    var sheet = reader.read(file, "Clients", 4, TakeOnParseOptions.defaults());
    assertThat(sheet.headers()).containsExactly("Name", "Email");
    assertThat(sheet.rows()).extracting(TakeOnRawRow::rowNumber).containsExactly(5);
    byte[] empty = TakeOnTestFiles.xlsx(workbook -> workbook.createSheet("Clients"));
    assertThat(codeOf(() -> reader.read(empty, "Clients", 1, TakeOnParseOptions.defaults())))
        .isEqualTo(TakeOnReadException.Code.NO_HEADER);
  }

  @Test
  void tenThousandAndOneRows_isTooManyRows() {
    assertThat(read(TakeOnTestFiles.xlsxLarge(10_000, 1, 0, 1L), "Data").rows()).hasSize(10_000);
    assertThat(codeOf(() -> read(TakeOnTestFiles.xlsxLarge(10_001, 1, 0, 1L), "Data")))
        .isEqualTo(TakeOnReadException.Code.TOO_MANY_ROWS);
  }

  @Test
  void oneHundredAndOneColumns_isTooManyColumns() {
    assertThat(codeOf(() -> read(TakeOnTestFiles.xlsxLarge(1, 101, 0, 1L), "Data")))
        .isEqualTo(TakeOnReadException.Code.TOO_MANY_COLUMNS);
  }

  @Test
  void ole2WorkbookAndNonZipBytes_areUnsupported() {
    assertThat(codeOf(() -> read(TakeOnTestFiles.xlsOle2(), null)))
        .isEqualTo(TakeOnReadException.Code.UNSUPPORTED_FORMAT);
    assertThat(codeOf(() -> reader.sheetNames(TakeOnTestFiles.xlsOle2())))
        .isEqualTo(TakeOnReadException.Code.UNSUPPORTED_FORMAT);
    assertThat(codeOf(() -> read("Name,Email\n".getBytes(), null)))
        .isEqualTo(TakeOnReadException.Code.UNSUPPORTED_FORMAT);
  }

  /** C-9 (1): the reader package never touches the usermodel workbook. */
  @Test
  void readerPackageNeverUsesTheUsermodelWorkbook() {
    var classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.b2mash.b2b.b2bstrawman.takeon.reader");
    assertThat(classes).isNotEmpty();
    noClasses()
        .that()
        .resideInAPackage("..takeon.reader..")
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("org.apache.poi.xssf.usermodel.XSSFWorkbook")
        .orShould()
        .dependOnClassesThat()
        .haveFullyQualifiedName("org.apache.poi.ss.usermodel.WorkbookFactory")
        .because("take-on XLSX is read with the streaming event API only (C-7, FD-17)")
        .check(classes);
  }

  /**
   * C-9 (2): a workbook just under the size limit (3 MB since FR-3; 10 000 rows, many unique
   * strings) reads to completion.
   */
  @Test
  void aWorkbookAtTheSizeLimitReadsToCompletion() {
    byte[] file = TakeOnTestFiles.xlsxLarge(10_000, 6, 58, 93L);
    assertThat((long) file.length)
        .as("fixture size %d bytes", file.length)
        .isBetween(2_500_000L, TakeOnFileReader.MAX_FILE_BYTES);
    var sheet = read(file, "Data");
    assertThat(sheet.headers()).hasSize(6);
    assertThat(sheet.rows()).hasSize(10_000);
    assertThat(sheet.rows().getLast().rowNumber()).isEqualTo(10_001);
  }
}
