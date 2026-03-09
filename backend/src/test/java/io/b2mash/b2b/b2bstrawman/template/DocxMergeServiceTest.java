package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

class DocxMergeServiceTest {

  private final DocxMergeService service = new DocxMergeService();

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
}
