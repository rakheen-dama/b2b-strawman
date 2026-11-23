package io.b2mash.b2b.b2bstrawman.takeon.reader;

import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestFiles.csv;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestFiles.csvWindows1252;
import static io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestFiles.csvWithBom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnTestFiles;
import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnParseOptions.DecimalSeparator;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Phase 93 / 653A (task 653.6): the CSV take-on reader (§17.3.1). Plain JUnit. */
class CsvTakeOnReaderTest {

  private final CsvTakeOnReader reader = new CsvTakeOnReader();

  private TakeOnSheet read(byte[] file) {
    return reader.read(file, null, 1, TakeOnParseOptions.defaults());
  }

  @Test
  void oneSyntheticSheet() {
    assertThat(reader.sheetNames(csv("a,b\n1,2\n")))
        .containsExactly(new TakeOnFileReader.SheetName("CSV", false));
  }

  @Test
  void utf8BomIsStripped_andSemicolonIsDetected_suggestingADecimalComma() {
    var sheet = read(csvWithBom("Name;Email;Balance\r\nZanele;z@example.test;1 234,56\r\n"));
    assertThat(sheet.headers()).containsExactly("Name", "Email", "Balance");
    assertThat(sheet.headers().getFirst()).doesNotStartWith("\uFEFF");
    assertThat(sheet.detected().delimiter()).isEqualTo(';');
    assertThat(sheet.detected().decimalSeparator()).isEqualTo(DecimalSeparator.COMMA);
    assertThat(sheet.detected().encoding()).isEqualTo("UTF-8");
    assertThat(sheet.rows()).hasSize(1);
    assertThat(sheet.rows().getFirst().rowNumber()).isEqualTo(2);
    assertThat(sheet.rows().getFirst().values())
        .containsEntry("Name", "Zanele")
        .containsEntry("Balance", "1 234,56");
  }

  @Test
  void tabAndPipeAreDetected() {
    assertThat(read(csv("A\tB\tC\n1\t2\t3\n")).detected().delimiter()).isEqualTo('\t');
    var piped = read(csv("A|B\n1|2\n"));
    assertThat(piped.detected().delimiter()).isEqualTo('|');
    assertThat(piped.detected().decimalSeparator()).isEqualTo(DecimalSeparator.POINT);
    assertThat(piped.rows().getFirst().values()).containsEntry("B", "2");
  }

  @Test
  void aGivenDelimiterWinsOverDetection() {
    var options = TakeOnParseOptions.defaults().withDelimiter(';');
    var sheet = reader.read(csv("A,B;C\n1,2;3\n"), "CSV", 1, options);
    assertThat(sheet.headers()).containsExactly("A,B", "C");
  }

  @Test
  void windows1252FallsBackWithAWarning() {
    var sheet = read(csvWindows1252("Name,City\nJosé,Paarl\n"));
    assertThat(sheet.detected().encoding()).isEqualTo("windows-1252");
    assertThat(sheet.rows().getFirst().values()).containsEntry("Name", "José");
    assertThat(sheet.warnings())
        .contains(
            "This file is not UTF-8. It was read as Windows-1252 — check accented names in the"
                + " preview.");
  }

  @Test
  void quotedNewlineStaysInOneCellAndOneRow() {
    var sheet = read(csv("Name,Address\n\"A\",\"1 Long St\nCape Town\"\n\"B\",\"2 Short St\"\n"));
    assertThat(sheet.rows()).hasSize(2);
    assertThat(sheet.rows().get(0).rowNumber()).isEqualTo(2);
    assertThat(sheet.rows().get(0).values()).containsEntry("Address", "1 Long St\nCape Town");
    assertThat(sheet.rows().get(1).rowNumber()).isEqualTo(3);
  }

  @Test
  void doubledQuoteIsAnEscapedQuote_andDelimitersInsideQuotesAreData() {
    var sheet = read(csv("Name,Note\n\"Smith, J\",\"He said \"\"hi\"\"\"\n"));
    assertThat(sheet.rows().getFirst().values())
        .containsEntry("Name", "Smith, J")
        .containsEntry("Note", "He said \"hi\"");
  }

  @Test
  void blankLinesAreSkippedAndCounted() {
    var sheet = read(csv("A,B\n1,2\n\n,\n3,4\n"));
    assertThat(sheet.rows()).extracting(TakeOnRawRow::rowNumber).containsExactly(2, 5);
    assertThat(sheet.blankSkipped()).isEqualTo(2);
  }

  @Test
  void fewerCellsArePadded_moreCellsAreMalformedAndSkipped() {
    var sheet = read(csv("A,B,C\n1\n1,2,3,4\n5,6,7\n"));
    assertThat(sheet.rows()).extracting(TakeOnRawRow::rowNumber).containsExactly(2, 4);
    assertThat(sheet.rows().getFirst().values()).containsEntry("B", "").containsEntry("C", "");
    assertThat(sheet.malformedCount()).isEqualTo(1);
    assertThat(sheet.warnings())
        .contains("Row 3 has more cells than the header row and was skipped.");
  }

  @Test
  void unterminatedQuoteAtEndOfFileIsMalformed() {
    var sheet = read(csv("A,B\n1,2\n\"3,4\n"));
    assertThat(sheet.rows()).hasSize(1);
    assertThat(sheet.malformedCount()).isEqualTo(1);
  }

  @Test
  void everyRowMalformed_throws() {
    assertThatThrownBy(() -> read(csv("A\n1,2\n3,4\n")))
        .isInstanceOfSatisfying(
            TakeOnReadException.class,
            e -> assertThat(e.getCode()).isEqualTo(TakeOnReadException.Code.ALL_ROWS_MALFORMED));
  }

  @Test
  void blankHeaderBecomesColumnN_duplicateHeaderGetsASuffixAndAWarning() {
    var sheet = read(csv("Name,,Name,Name\n1,2,3,4\n"));
    assertThat(sheet.headers()).containsExactly("Name", "Column 2", "Name (2)", "Name (3)");
    assertThat(sheet.warnings())
        .contains("Two columns are named \"Name\"; the second is shown as \"Name (2)\".");
    assertThat(sheet.rows().getFirst().values()).containsEntry("Name (3)", "4");
  }

  @Test
  void headerRowThreeSkipsTwoTitleRows() {
    var sheet =
        reader.read(
            csv("Client list;;\nExported 31 Oct;;\nName;Email\nA;a@example.test\n"),
            null,
            3,
            TakeOnParseOptions.defaults());
    assertThat(sheet.detected().delimiter()).isEqualTo(';');
    assertThat(sheet.headers()).containsExactly("Name", "Email");
    assertThat(sheet.rows().getFirst().rowNumber()).isEqualTo(4);
  }

  @Test
  void headerRowBeyondTheData_isNoHeader() {
    assertThatThrownBy(() -> reader.read(csv("A\n1\n"), null, 5, TakeOnParseOptions.defaults()))
        .isInstanceOfSatisfying(
            TakeOnReadException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo(TakeOnReadException.Code.NO_HEADER);
              assertThat(e.getMessage())
                  .isEqualTo("The header row 5 is empty or beyond the end of the sheet.");
            });
  }

  @Test
  void oneHundredAndOneColumns_isTooManyColumns() {
    assertThat(read(TakeOnTestFiles.csvOfSize(1, 100)).headers()).hasSize(100);
    assertThatThrownBy(() -> read(TakeOnTestFiles.csvOfSize(1, 101)))
        .isInstanceOfSatisfying(
            TakeOnReadException.class,
            e -> assertThat(e.getCode()).isEqualTo(TakeOnReadException.Code.TOO_MANY_COLUMNS));
  }

  @Test
  void tenThousandAndOneRows_isTooManyRows() {
    assertThat(read(TakeOnTestFiles.csvOfSize(10_000, 2)).rows()).hasSize(10_000);
    assertThatThrownBy(() -> read(TakeOnTestFiles.csvOfSize(10_001, 2)))
        .isInstanceOfSatisfying(
            TakeOnReadException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo(TakeOnReadException.Code.TOO_MANY_ROWS);
              assertThat(e.getMessage())
                  .isEqualTo("This sheet has more than 10 000 rows. Split it into smaller files.");
            });
  }

  @Test
  void nulByteIsRefusedAsUnsupported() {
    byte[] file = {'A', ',', 'B', '\n', 0, ',', '1', '\n'};
    assertThatThrownBy(() -> read(file))
        .isInstanceOfSatisfying(
            TakeOnReadException.class,
            e -> assertThat(e.getCode()).isEqualTo(TakeOnReadException.Code.UNSUPPORTED_FORMAT));
  }

  @Test
  void parseOptionsRoundTripThroughTheJsonbMap() {
    var options = new TakeOnParseOptions("yyyy-MM-dd", DecimalSeparator.COMMA, ';', "UTF-8");
    assertThat(TakeOnParseOptions.fromMap(options.toMap())).isEqualTo(options);
    assertThat(TakeOnParseOptions.fromMap(null)).isEqualTo(TakeOnParseOptions.defaults());
    assertThat(options.toMap()).containsKeys("dateFormat", "decimalSeparator", "delimiter");
    assertThat(List.copyOf(options.toMap().keySet()))
        .containsExactly("dateFormat", "decimalSeparator", "delimiter", "encoding");
  }

  @Test
  void skippedRowsAreBounded_andWarningsAreCapped() {
    var malformed = new StringBuilder("A\n");
    for (int i = 0; i < 150; i++) {
      malformed.append("x,y\n");
    }
    malformed.append("kept\n");
    var sheet = read(csv(malformed.toString()));
    assertThat(sheet.malformedCount()).isEqualTo(150);
    assertThat(sheet.warnings()).hasSize(101).last().isEqualTo("50 more warnings are not shown.");

    var blanks = "A\n1\n" + "\n".repeat(10_001);
    assertThatThrownBy(() -> read(csv(blanks)))
        .isInstanceOfSatisfying(
            TakeOnReadException.class,
            e -> assertThat(e.getCode()).isEqualTo(TakeOnReadException.Code.TOO_MANY_ROWS));
  }

  @Test
  void aFileOverTenMegabytesIsRefusedBeforeDecoding() {
    byte[] file = new byte[(int) TakeOnFileReader.MAX_FILE_BYTES + 1];
    Arrays.fill(file, (byte) 'a');
    assertThatThrownBy(() -> read(file))
        .isInstanceOfSatisfying(
            TakeOnReadException.class,
            e -> assertThat(e.getCode()).isEqualTo(TakeOnReadException.Code.FILE_TOO_LARGE));
    assertThatThrownBy(() -> new XlsxTakeOnReader().sheetNames(file))
        .isInstanceOfSatisfying(
            TakeOnReadException.class,
            e -> assertThat(e.getCode()).isEqualTo(TakeOnReadException.Code.FILE_TOO_LARGE));
  }
}
