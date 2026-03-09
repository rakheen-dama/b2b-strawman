package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class DocxMergeServiceTest {

  private final DocxMergeService service = new DocxMergeService();

  // --- Existing merge tests ---

  @Test
  void merge_singleRunField_replacesCorrectly() throws Exception {
    byte[] docx = createSimpleMergeDocx();
    Map<String, Object> context = Map.of("customer", Map.of("name", "Acme Corp"));

    byte[] result = service.merge(new ByteArrayInputStream(docx), context);

    try (XWPFDocument merged = new XWPFDocument(new ByteArrayInputStream(result))) {
      String text = merged.getParagraphs().get(0).getText();
      assertThat(text).isEqualTo("Hello Acme Corp, welcome!");
    }
  }

  @Test
  void merge_splitRunField_replacesAcrossRuns() throws Exception {
    byte[] docx = createSplitRunDocx();
    Map<String, Object> context = Map.of("customer", Map.of("name", "Acme Corp"));

    byte[] result = service.merge(new ByteArrayInputStream(docx), context);

    try (XWPFDocument merged = new XWPFDocument(new ByteArrayInputStream(result))) {
      String text = merged.getParagraphs().get(0).getText();
      assertThat(text).isEqualTo("Dear Acme Corp, welcome to our service.");
    }
  }

  @Test
  void merge_multipleFieldsInParagraph_replacesAll() throws Exception {
    byte[] docx = createMultipleFieldsDocx();
    Map<String, Object> context =
        Map.of(
            "customer", Map.of("name", "Acme Corp"),
            "project", Map.of("name", "Alpha"));

    byte[] result = service.merge(new ByteArrayInputStream(docx), context);

    try (XWPFDocument merged = new XWPFDocument(new ByteArrayInputStream(result))) {
      String text = merged.getParagraphs().get(0).getText();
      assertThat(text).isEqualTo("Dear Acme Corp, your project Alpha is ready.");
    }
  }

  @Test
  void merge_missingVariable_replacesWithEmpty() throws Exception {
    byte[] docx = createSimpleMergeDocx();
    Map<String, Object> context = Map.of();

    byte[] result = service.merge(new ByteArrayInputStream(docx), context);

    try (XWPFDocument merged = new XWPFDocument(new ByteArrayInputStream(result))) {
      String text = merged.getParagraphs().get(0).getText();
      assertThat(text).isEqualTo("Hello , welcome!");
    }
  }

  @Test
  void merge_nullValue_replacesWithEmpty() throws Exception {
    byte[] docx = createSimpleMergeDocx();
    java.util.HashMap<String, Object> customerMap = new java.util.HashMap<>();
    customerMap.put("name", null);
    Map<String, Object> context = Map.of("customer", customerMap);

    byte[] result = service.merge(new ByteArrayInputStream(docx), context);

    try (XWPFDocument merged = new XWPFDocument(new ByteArrayInputStream(result))) {
      String text = merged.getParagraphs().get(0).getText();
      assertThat(text).isEqualTo("Hello , welcome!");
    }
  }

  @Test
  void merge_nestedDotPath_resolvesCorrectly() throws Exception {
    byte[] docx = createDocxWithField("Contact: {{org.contact.email}}");
    Map<String, Object> context =
        Map.of("org", Map.of("contact", Map.of("email", "info@acme.com")));

    byte[] result = service.merge(new ByteArrayInputStream(docx), context);

    try (XWPFDocument merged = new XWPFDocument(new ByteArrayInputStream(result))) {
      String text = merged.getParagraphs().get(0).getText();
      assertThat(text).isEqualTo("Contact: info@acme.com");
    }
  }

  @Test
  void merge_noFields_returnsUnchanged() throws Exception {
    byte[] docx = createNoFieldsDocx();
    Map<String, Object> context = Map.of("customer", Map.of("name", "Acme Corp"));

    byte[] result = service.merge(new ByteArrayInputStream(docx), context);

    try (XWPFDocument merged = new XWPFDocument(new ByteArrayInputStream(result))) {
      String text = merged.getParagraphs().get(0).getText();
      assertThat(text).isEqualTo("This document has no merge fields.");
    }
  }

  @Test
  void resolveVariable_deeplyNested_works() {
    Map<String, Object> context = Map.of("a", Map.of("b", Map.of("c", Map.of("d", "deep-value"))));

    String result = service.resolveVariable("a.b.c.d", context);

    assertThat(result).isEqualTo("deep-value");
  }

  @Test
  void resolveVariable_missingIntermediateKey_returnsEmpty() {
    Map<String, Object> context = Map.of("a", Map.of("b", "leaf"));

    String result = service.resolveVariable("a.x.y", context);

    assertThat(result).isEmpty();
  }

  @Test
  void merge_preservesFormatting_afterReplace() throws Exception {
    byte[] docx = createSplitRunDocx();
    Map<String, Object> context = Map.of("customer", Map.of("name", "Acme Corp"));

    byte[] result = service.merge(new ByteArrayInputStream(docx), context);

    try (XWPFDocument merged = new XWPFDocument(new ByteArrayInputStream(result))) {
      XWPFParagraph para = merged.getParagraphs().get(0);
      // The split-run docx has run2 with bold=true. After merge, run2 should still have its
      // formatting preserved (even though its text is now empty).
      assertThat(para.getRuns()).hasSizeGreaterThanOrEqualTo(3);
      XWPFRun boldRun = para.getRuns().get(1);
      assertThat(boldRun.isBold()).isTrue();
    }
  }

  // --- Field discovery tests (322.14) ---

  @Test
  void discoverFields_simpleDoc_findsAllFields() throws Exception {
    byte[] docx = createMultipleFieldsDocx();

    List<String> fields = service.discoverFields(new ByteArrayInputStream(docx));

    assertThat(fields).containsExactly("customer.name", "project.name");
  }

  @Test
  void discoverFields_splitRuns_findsFields() throws Exception {
    byte[] docx = createSplitRunDocx();

    List<String> fields = service.discoverFields(new ByteArrayInputStream(docx));

    assertThat(fields).containsExactly("customer.name");
  }

  @Test
  void discoverFields_headersFooters_findsFields() throws Exception {
    byte[] docx = createHeaderFooterDocx();

    List<String> fields = service.discoverFields(new ByteArrayInputStream(docx));

    assertThat(fields).containsExactly("customer.name", "generatedAt", "org.name");
  }

  @Test
  void discoverFields_tableCells_findsFields() throws Exception {
    byte[] docx = createTableFieldsDocx();

    List<String> fields = service.discoverFields(new ByteArrayInputStream(docx));

    assertThat(fields).containsExactly("customer.email", "customer.name");
  }

  @Test
  void discoverFields_noFields_returnsEmpty() throws Exception {
    byte[] docx = createNoFieldsDocx();

    List<String> fields = service.discoverFields(new ByteArrayInputStream(docx));

    assertThat(fields).isEmpty();
  }

  // --- Header/footer/table merge tests (322.15) ---

  @Test
  void merge_headerField_replacesInHeader() throws Exception {
    byte[] docx = createHeaderFooterDocx();
    Map<String, Object> context =
        Map.of(
            "customer", Map.of("name", "Acme Corp"),
            "org", Map.of("name", "DocTeams"),
            "generatedAt", "2026-03-09");

    byte[] result = service.merge(new ByteArrayInputStream(docx), context);

    try (XWPFDocument merged = new XWPFDocument(new ByteArrayInputStream(result))) {
      XWPFHeader header = merged.getHeaderList().get(0);
      String headerText =
          header.getParagraphs().stream()
              .map(XWPFParagraph::getText)
              .filter(t -> t != null && !t.isBlank())
              .findFirst()
              .orElse("");
      assertThat(headerText).contains("DocTeams");
    }
  }

  @Test
  void merge_footerField_replacesInFooter() throws Exception {
    byte[] docx = createHeaderFooterDocx();
    Map<String, Object> context =
        Map.of(
            "customer", Map.of("name", "Acme Corp"),
            "org", Map.of("name", "DocTeams"),
            "generatedAt", "2026-03-09");

    byte[] result = service.merge(new ByteArrayInputStream(docx), context);

    try (XWPFDocument merged = new XWPFDocument(new ByteArrayInputStream(result))) {
      XWPFFooter footer = merged.getFooterList().get(0);
      String footerText =
          footer.getParagraphs().stream()
              .map(XWPFParagraph::getText)
              .filter(t -> t != null && !t.isBlank())
              .findFirst()
              .orElse("");
      assertThat(footerText).contains("2026-03-09");
    }
  }

  @Test
  void merge_tableField_replacesInCell() throws Exception {
    byte[] docx = createTableFieldsDocx();
    Map<String, Object> context =
        Map.of("customer", Map.of("name", "Acme Corp", "email", "info@acme.com"));

    byte[] result = service.merge(new ByteArrayInputStream(docx), context);

    try (XWPFDocument merged = new XWPFDocument(new ByteArrayInputStream(result))) {
      XWPFTable table = merged.getTables().get(0);
      String cellText = table.getRow(0).getCell(1).getText();
      assertThat(cellText).isEqualTo("Acme Corp");
      String cellText2 = table.getRow(1).getCell(1).getText();
      assertThat(cellText2).isEqualTo("info@acme.com");
    }
  }

  @Test
  void merge_emptyField_ignored() throws Exception {
    byte[] docx = createDocxWithField("Before {{}} after");
    Map<String, Object> context = Map.of("customer", Map.of("name", "Acme Corp"));

    byte[] result = service.merge(new ByteArrayInputStream(docx), context);

    try (XWPFDocument merged = new XWPFDocument(new ByteArrayInputStream(result))) {
      String text = merged.getParagraphs().get(0).getText();
      // The regex [^{}]+ requires at least one char, so {{}} is not matched and stays as-is
      assertThat(text).isEqualTo("Before {{}} after");
    }
  }

  // --- Test document creators ---

  private byte[] createSimpleMergeDocx() throws Exception {
    try (XWPFDocument doc = new XWPFDocument()) {
      XWPFParagraph para = doc.createParagraph();
      XWPFRun run = para.createRun();
      run.setText("Hello {{customer.name}}, welcome!");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    }
  }

  private byte[] createSplitRunDocx() throws Exception {
    try (XWPFDocument doc = new XWPFDocument()) {
      XWPFParagraph para = doc.createParagraph();
      XWPFRun run1 = para.createRun();
      run1.setText("Dear {{");
      XWPFRun run2 = para.createRun();
      run2.setBold(true);
      run2.setText("customer");
      XWPFRun run3 = para.createRun();
      run3.setText(".name}}, welcome to our service.");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    }
  }

  private byte[] createMultipleFieldsDocx() throws Exception {
    try (XWPFDocument doc = new XWPFDocument()) {
      XWPFParagraph para = doc.createParagraph();
      XWPFRun run = para.createRun();
      run.setText("Dear {{customer.name}}, your project {{project.name}} is ready.");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    }
  }

  private byte[] createNoFieldsDocx() throws Exception {
    try (XWPFDocument doc = new XWPFDocument()) {
      XWPFParagraph para = doc.createParagraph();
      XWPFRun run = para.createRun();
      run.setText("This document has no merge fields.");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    }
  }

  private byte[] createDocxWithField(String text) throws Exception {
    try (XWPFDocument doc = new XWPFDocument()) {
      XWPFParagraph para = doc.createParagraph();
      XWPFRun run = para.createRun();
      run.setText(text);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    }
  }

  private byte[] createHeaderFooterDocx() throws Exception {
    try (XWPFDocument doc = new XWPFDocument()) {
      // Body paragraph
      XWPFParagraph bodyPara = doc.createParagraph();
      bodyPara.createRun().setText("Body text with {{customer.name}}");

      // Header
      XWPFHeader header = doc.createHeader(HeaderFooterType.DEFAULT);
      XWPFParagraph headerPara = header.createParagraph();
      headerPara.createRun().setText("Header: {{org.name}}");

      // Footer
      XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
      XWPFParagraph footerPara = footer.createParagraph();
      footerPara.createRun().setText("Footer: {{generatedAt}}");

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    }
  }

  private byte[] createTableFieldsDocx() throws Exception {
    try (XWPFDocument doc = new XWPFDocument()) {
      XWPFTable table = doc.createTable(2, 2);
      // Row 0, Cell 0
      table.getRow(0).getCell(0).setText("Name:");
      // Row 0, Cell 1 - with merge field
      XWPFParagraph cellPara = table.getRow(0).getCell(1).getParagraphArray(0);
      cellPara.createRun().setText("{{customer.name}}");
      // Row 1, Cell 0
      table.getRow(1).getCell(0).setText("Email:");
      // Row 1, Cell 1
      XWPFParagraph cellPara2 = table.getRow(1).getCell(1).getParagraphArray(0);
      cellPara2.createRun().setText("{{customer.email}}");

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    }
  }

  private byte[] createMixedFormattingDocx() throws Exception {
    try (XWPFDocument doc = new XWPFDocument()) {
      XWPFParagraph para = doc.createParagraph();
      XWPFRun run1 = para.createRun();
      run1.setBold(true);
      run1.setText("Bold: {{");
      XWPFRun run2 = para.createRun();
      run2.setItalic(true);
      run2.setText("customer.name");
      XWPFRun run3 = para.createRun();
      run3.setText("}} end");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    }
  }
}
