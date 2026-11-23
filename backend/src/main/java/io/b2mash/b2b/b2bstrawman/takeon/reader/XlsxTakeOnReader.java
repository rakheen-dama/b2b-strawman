package io.b2mash.b2b.b2bstrawman.takeon.reader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Reads a take-on {@code .xlsx} with POI's <strong>streaming event API only</strong> (§17.3.1,
 * §17.6.3, FD-17, C-7): {@code XSSFReader} + {@code XSSFSheetXMLHandler}, never the usermodel
 * workbook, so parsing never builds a DOM of the sheet.
 *
 * <ul>
 *   <li>Cached values only ({@code formulasNotResults = false}): a formula is never evaluated; one
 *       saved without a cached value reads blank.
 *   <li>Dates to ISO, numbers via {@code NumberToTextConverter} ({@link TakeOnDataFormatter}).
 *   <li>An error cell ({@code #REF!} …) keeps its token as the cell text; the row is kept and
 *       counted malformed (C-8) — validation turns the token into a row error.
 *   <li>Booleans read {@code true}/{@code false}; merged cells carry their value in the top-left
 *       cell only; hidden rows and columns are read.
 *   <li>{@code ZipSecureFile} zip-bomb defaults untouched; an OLE2 ({@code .xls}) or non-zip file
 *       fails cleanly with {@code UNSUPPORTED_FORMAT}.
 * </ul>
 */
public final class XlsxTakeOnReader implements TakeOnFileReader {

  private static final String UNSUPPORTED = "Save the file as .xlsx or .csv and upload it again.";
  private static final String ERROR_PREFIX = "ERROR:";

  /** Workbook-level facts read from {@code workbook.xml}. */
  private record WorkbookInfo(List<SheetName> sheets, boolean date1904) {}

  @Override
  public List<SheetName> sheetNames(byte[] file) {
    OPCPackage pkg = open(file);
    try {
      return workbookInfo(new XSSFReader(pkg)).sheets();
    } catch (OpenXML4JException e) {
      throw unsupported(e);
    } catch (RuntimeException e) {
      throw readFailure(e);
    } catch (IOException | XMLStreamException e) {
      throw unreadable(e);
    } finally {
      pkg.revert(); // read-only use: close without saving
    }
  }

  @Override
  public TakeOnSheet read(
      byte[] file, String sheetName, int headerRow, TakeOnParseOptions options) {
    var detected = options == null ? TakeOnParseOptions.defaults() : options;
    OPCPackage pkg = open(file);
    try {
      XSSFReader reader = new XSSFReader(pkg);
      WorkbookInfo info = workbookInfo(reader);
      String target = sheetName != null ? sheetName : firstVisible(info.sheets());
      StylesTable styles = reader.getStylesTable();
      var strings = new ReadOnlySharedStringsTable(pkg);

      var it = (XSSFReader.SheetIterator) reader.getSheetsData();
      while (it.hasNext()) {
        try (InputStream sheet = it.next()) {
          if (!it.getSheetName().equals(target)) {
            continue;
          }
          var assembler = new TakeOnSheetAssembler(target, headerRow);
          var handler = new RowHandler(assembler);
          XMLReader xml = XMLHelper.newXMLReader();
          xml.setContentHandler(
              new XSSFSheetXMLHandler(
                  styles, null, strings, handler, new TakeOnDataFormatter(info.date1904()), false));
          xml.parse(new InputSource(sheet));
          // Plain .xlsx carries no text encoding or delimiter: report the options as given.
          return assembler.finish(detected.withDelimiter(null));
        }
      }
      throw new TakeOnReadException(
          TakeOnReadException.Code.SHEET_NOT_FOUND, "This workbook has no sheet named that.");
    } catch (OpenXML4JException e) {
      throw unsupported(e);
    } catch (SAXException | RuntimeException e) {
      throw readFailure(e);
    } catch (IOException | XMLStreamException | ParserConfigurationException e) {
      throw unreadable(e);
    } finally {
      pkg.revert(); // read-only use: close without saving
    }
  }

  /** Opens the package from memory; anything that is not an OOXML zip is refused cleanly. */
  private static OPCPackage open(byte[] file) {
    TakeOnFileReader.requireWithinSizeLimit(file);
    try {
      return OPCPackage.open(new ByteArrayInputStream(file));
    } catch (OpenXML4JException e) {
      throw unsupported(e);
    } catch (RuntimeException e) {
      throw readFailure(e);
    } catch (IOException e) {
      throw unreadable(e);
    }
  }

  // --- workbook.xml: sheet order, hidden state, 1904 windowing (StAX) ---

  private static WorkbookInfo workbookInfo(XSSFReader reader)
      throws IOException, OpenXML4JException, XMLStreamException {
    var sheets = new ArrayList<SheetName>();
    boolean date1904 = false;
    try (InputStream workbook = reader.getWorkbookData()) {
      XMLStreamReader xml = XMLHelper.newXMLInputFactory().createXMLStreamReader(workbook);
      try {
        while (xml.hasNext()) {
          if (xml.next() != XMLStreamConstants.START_ELEMENT) {
            continue;
          }
          switch (xml.getLocalName()) {
            case "sheet" -> {
              String name = xml.getAttributeValue(null, "name");
              String state = xml.getAttributeValue(null, "state");
              sheets.add(new SheetName(name, state != null && !"visible".equals(state)));
            }
            case "workbookPr" -> {
              String flag = xml.getAttributeValue(null, "date1904");
              date1904 = "1".equals(flag) || "true".equalsIgnoreCase(flag);
            }
            default -> {
              // not needed
            }
          }
        }
      } finally {
        xml.close();
      }
    }
    return new WorkbookInfo(List.copyOf(sheets), date1904);
  }

  private static String firstVisible(List<SheetName> sheets) {
    return sheets.stream()
        .filter(s -> !s.hidden())
        .map(SheetName::name)
        .findFirst()
        .orElseGet(() -> sheets.isEmpty() ? "" : sheets.getFirst().name());
  }

  // --- SAX row handler ---

  /** Collects one row's cells (sparse → blank), capped at the column limit. */
  private static final class RowHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

    private final TakeOnSheetAssembler assembler;
    private List<String> cells = new ArrayList<>();
    private boolean overflow;
    private boolean errorCell;
    private int nextColumn;

    RowHandler(TakeOnSheetAssembler assembler) {
      this.assembler = assembler;
    }

    @Override
    public void startRow(int rowNum) {
      cells = new ArrayList<>();
      overflow = false;
      errorCell = false;
      nextColumn = 0;
    }

    @Override
    public void endRow(int rowNum) {
      assembler.accept(rowNum + 1, cells, overflow, errorCell); // POI rows are 0-based
    }

    @Override
    public void cell(String cellReference, String formattedValue, XSSFComment comment) {
      int column = cellReference == null ? nextColumn : new CellReference(cellReference).getCol();
      nextColumn = column + 1;
      String value = formattedValue == null ? "" : formattedValue;
      if (value.startsWith(ERROR_PREFIX)) {
        value = value.substring(ERROR_PREFIX.length());
        errorCell = true;
      } else if ("TRUE".equals(value) || "FALSE".equals(value)) {
        value = value.toLowerCase(Locale.ROOT);
      }
      if (column >= MAX_COLUMNS) {
        if (!value.isBlank()) {
          overflow = true;
        }
        return;
      }
      if (column < cells.size()) {
        cells.set(column, value);
        return;
      }
      while (cells.size() < column) {
        cells.add("");
      }
      cells.add(value);
    }
  }

  // --- failures ---

  /** A limit or row-model refusal thrown inside the SAX callback may arrive wrapped. */
  private static RuntimeException readFailure(Exception e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof TakeOnReadException read) {
        return read;
      }
      if (t instanceof NotOfficeXmlFileException || t instanceof POIXMLException) {
        return unsupported((Exception) t);
      }
    }
    return unreadable(e);
  }

  private static TakeOnReadException unsupported(Exception e) {
    return new TakeOnReadException(TakeOnReadException.Code.UNSUPPORTED_FORMAT, UNSUPPORTED, e);
  }

  private static TakeOnReadException unreadable(Exception e) {
    return new TakeOnReadException(
        TakeOnReadException.Code.UNREADABLE,
        "This workbook could not be read. Open it in Excel, save it as .xlsx and upload it again.",
        e);
  }
}
