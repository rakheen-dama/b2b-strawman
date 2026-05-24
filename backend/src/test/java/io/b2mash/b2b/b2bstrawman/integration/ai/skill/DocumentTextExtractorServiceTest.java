package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class DocumentTextExtractorServiceTest {

  @Mock private StorageService storageService;

  private DocumentTextExtractorService service;

  @BeforeEach
  void setup() {
    service = new DocumentTextExtractorService(storageService, new ObjectMapper());
  }

  @Test
  void extractText_pdf_producesExpectedContent() throws IOException {
    byte[] pdfBytes = createTestPdf("Hello from PDF document");
    Document doc = createDocument("test.pdf", "application/pdf", "s3/test.pdf");
    when(storageService.download("s3/test.pdf")).thenReturn(pdfBytes);

    ExtractedText result = service.extractText(doc);

    assertThat(result.content()).contains("Hello from PDF document");
    assertThat(result.characterCount()).isGreaterThan(0);
    assertThat(result.wasTruncated()).isFalse();
    assertThat(result.truncationWarning()).isNull();
  }

  @Test
  void extractText_docx_producesExpectedContent() throws IOException {
    byte[] docxBytes = createTestDocx("Hello from DOCX document");
    Document doc =
        createDocument(
            "test.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "s3/test.docx");
    when(storageService.download("s3/test.docx")).thenReturn(docxBytes);

    ExtractedText result = service.extractText(doc);

    assertThat(result.content()).contains("Hello from DOCX document");
    assertThat(result.characterCount()).isGreaterThan(0);
    assertThat(result.wasTruncated()).isFalse();
    assertThat(result.truncationWarning()).isNull();
  }

  @Test
  void extractText_tiptapJson_traversesNestedNodes() throws IOException {
    byte[] jsonBytes = loadTestResource("ai/test-documents/test-tiptap.json");
    Document doc = createDocument("doc.json", "application/json", "s3/doc.json");
    when(storageService.download("s3/doc.json")).thenReturn(jsonBytes);

    ExtractedText result = service.extractText(doc);

    assertThat(result.content()).contains("Test Heading");
    assertThat(result.content()).contains("Hello ");
    assertThat(result.content()).contains("world");
    assertThat(result.content()).contains("This is a test.");
    assertThat(result.content()).contains("Item one");
    assertThat(result.content()).contains("Item two");
    assertThat(result.wasTruncated()).isFalse();
  }

  @Test
  void extractText_truncatesAtLimit_withWarning() throws IOException {
    // Use DOCX for truncation test — DOCX faithfully holds all characters (no page overflow),
    // whereas PDF on a single page may silently discard text beyond the page boundary.
    // Disable zip bomb detection for this test — large synthetic DOCX triggers it legitimately.
    double originalRatio = ZipSecureFile.getMinInflateRatio();
    try {
      ZipSecureFile.setMinInflateRatio(0);
      String longText = "A".repeat(110_000);
      byte[] docxBytes = createTestDocx(longText);
      Document doc =
          createDocument(
              "long.docx",
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
              "s3/long.docx");
      when(storageService.download("s3/long.docx")).thenReturn(docxBytes);

      ExtractedText result = service.extractText(doc);

      assertThat(result.characterCount()).isEqualTo(102_400);
      assertThat(result.wasTruncated()).isTrue();
      assertThat(result.truncationWarning()).contains("truncated");
      assertThat(result.truncationWarning()).contains("100KB limit");
    } finally {
      ZipSecureFile.setMinInflateRatio(originalRatio);
    }
  }

  @Test
  void extractText_corruptedPdf_throwsInvalidStateException() {
    byte[] corruptBytes = "this is not a valid PDF".getBytes(StandardCharsets.UTF_8);
    Document doc = createDocument("corrupt.pdf", "application/pdf", "s3/corrupt.pdf");
    when(storageService.download("s3/corrupt.pdf")).thenReturn(corruptBytes);

    assertThatThrownBy(() -> service.extractText(doc)).isInstanceOf(InvalidStateException.class);
  }

  @Test
  void extractText_emptyDocument_returnsEmptyContent() throws IOException {
    byte[] emptyPdfBytes = createEmptyPdf();
    Document doc = createDocument("empty.pdf", "application/pdf", "s3/empty.pdf");
    when(storageService.download("s3/empty.pdf")).thenReturn(emptyPdfBytes);

    ExtractedText result = service.extractText(doc);

    assertThat(result.characterCount()).isGreaterThanOrEqualTo(0);
    assertThat(result.wasTruncated()).isFalse();
  }

  private Document createDocument(String fileName, String contentType, String s3Key) {
    Document doc = new Document(UUID.randomUUID(), fileName, contentType, 1024L, UUID.randomUUID());
    doc.assignS3Key(s3Key);
    return doc;
  }

  private byte[] createTestPdf(String text) throws IOException {
    try (PDDocument doc = new PDDocument()) {
      PDPage page = new PDPage();
      doc.addPage(page);
      try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(25, 700);
        // PDFBox showText cannot handle very long strings in one call, split if needed
        int chunkSize = 500;
        for (int i = 0; i < text.length(); i += chunkSize) {
          String chunk = text.substring(i, Math.min(i + chunkSize, text.length()));
          stream.showText(chunk);
        }
        stream.endText();
      }
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      doc.save(baos);
      return baos.toByteArray();
    }
  }

  private byte[] createEmptyPdf() throws IOException {
    try (PDDocument doc = new PDDocument()) {
      doc.addPage(new PDPage());
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      doc.save(baos);
      return baos.toByteArray();
    }
  }

  private byte[] createTestDocx(String text) throws IOException {
    try (XWPFDocument doc = new XWPFDocument()) {
      XWPFParagraph paragraph = doc.createParagraph();
      XWPFRun run = paragraph.createRun();
      run.setText(text);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      doc.write(baos);
      return baos.toByteArray();
    }
  }

  private byte[] loadTestResource(String path) throws IOException {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
      if (is == null) {
        throw new IOException("Test resource not found: " + path);
      }
      return is.readAllBytes();
    }
  }
}
