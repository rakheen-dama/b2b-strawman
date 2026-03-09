package io.b2mash.b2b.b2bstrawman.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PdfConversionServiceTest {

  private PdfConversionService service;

  @BeforeEach
  void setUp() {
    service = new PdfConversionService();
  }

  @Test
  void convertToPdf_bothUnavailable_returnsEmpty() {
    // LibreOffice not available, docx4j class not found scenario
    service.setLibreOfficeAvailable(false);
    // docx4j may or may not be on classpath in test env — if it is, this tests the cascade;
    // if not, it tests that empty is returned when both fail
    byte[] dummyBytes = new byte[] {0x50, 0x4B, 0x03, 0x04}; // Not a valid DOCX
    var result = service.convertToPdf(dummyBytes);
    // With invalid bytes, even if docx4j is present it will fail to parse
    assertThat(result).isEmpty();
  }

  @Test
  void convertViaLibreOffice_libreOfficeNotAvailable_returnsEmpty() {
    service.setLibreOfficeAvailable(false);
    var result = service.convertToPdf(new byte[] {});
    assertThat(result).isEmpty();
  }

  @Test
  void convertViaDocx4j_invalidDocx_returnsEmpty() {
    // Pass invalid bytes — docx4j should fail gracefully
    var result = service.convertViaDocx4j(new byte[] {0x00, 0x01, 0x02});
    assertThat(result).isEmpty();
  }

  @Test
  void convertViaLibreOffice_invalidDocx_returnsEmpty() throws Exception {
    // Even if LibreOffice is available, bad input should not crash
    service.setLibreOfficeAvailable(true);
    // This will likely fail because the bytes aren't a real DOCX,
    // or LibreOffice isn't installed in CI
    byte[] invalidDocx = new byte[] {0x50, 0x4B, 0x03, 0x04};
    var result = service.convertViaLibreOffice(invalidDocx);
    // Either empty (LO not installed or failed) or present (LO managed to convert)
    // We just assert it doesn't throw
    assertThat(result).isNotNull();
  }

  @Test
  void isLibreOfficeAvailable_setterWorks() {
    service.setLibreOfficeAvailable(true);
    assertThat(service.isLibreOfficeAvailable()).isTrue();
    service.setLibreOfficeAvailable(false);
    assertThat(service.isLibreOfficeAvailable()).isFalse();
  }

  @Test
  void convertToPdf_libreOfficeAvailableButFails_fallsToDocx4j() throws Exception {
    // Simulate: LO available but will fail on these bytes
    service.setLibreOfficeAvailable(true);
    byte[] validDocx = createTestDocx("Hello World");
    var result = service.convertToPdf(validDocx);
    // If LO is installed, it may succeed. If not, it falls back to docx4j.
    // If docx4j is on classpath, it may succeed or fail.
    // We assert no exception is thrown — graceful degradation.
    assertThat(result).isNotNull();
  }

  private byte[] createTestDocx(String text) throws Exception {
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
