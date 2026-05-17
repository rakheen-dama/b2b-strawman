package io.b2mash.b2b.b2bstrawman.integration.ai.skill.fica;

import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.integration.ai.cost.AiPricingProperties;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads customer documents from S3 and extracts text via PDFBox. Scanned PDFs and image files are
 * noted as requiring manual review since text extraction is not possible for those formats.
 */
@Service
public class FicaDocumentReader {

  private static final Logger log = LoggerFactory.getLogger(FicaDocumentReader.class);
  private static final int SCANNED_PDF_TEXT_THRESHOLD = 100;
  private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/jpg");

  private final StorageService storageService;
  private final AiPricingProperties pricingProperties;

  public FicaDocumentReader(StorageService storageService, AiPricingProperties pricingProperties) {
    this.storageService = storageService;
    this.pricingProperties = pricingProperties;
  }

  public DocumentReadResult readDocuments(List<Document> documents) {
    var textContent = new StringBuilder();
    long totalBytesFetched = 0;

    for (int i = 0; i < documents.size(); i++) {
      Document doc = documents.get(i);

      // Skip documents exceeding single-document size limit
      if (doc.getSize() > pricingProperties.maxDocumentSizeBytes()) {
        textContent
            .append("<document file=\"")
            .append(doc.getFileName())
            .append("\" type=\"")
            .append(doc.getContentType())
            .append("\">\n");
        textContent.append("Skipped: document exceeds maximum size limit.\n");
        textContent.append("</document>\n");
        continue;
      }

      // Stop if total payload exceeds limit
      if (totalBytesFetched + doc.getSize() > pricingProperties.maxTotalDocumentSizeBytes()) {
        int remaining = documents.size() - i;
        textContent
            .append("Remaining ")
            .append(remaining)
            .append(" documents were too large to include.\n");
        break;
      }

      // Per-document error handling: one failed download should not kill the entire skill
      byte[] bytes;
      try {
        bytes = storageService.download(doc.getS3Key());
      } catch (Exception e) {
        log.warn(
            "Failed to download document {} (key={}): {}",
            doc.getFileName(),
            doc.getS3Key(),
            e.getMessage());
        textContent
            .append("<document file=\"")
            .append(doc.getFileName())
            .append("\" type=\"")
            .append(doc.getContentType())
            .append("\">\n");
        textContent.append("Error: failed to download document from storage.\n");
        textContent.append("</document>\n");
        continue;
      }

      // Post-download size enforcement (metadata can be stale)
      if (bytes.length > pricingProperties.maxDocumentSizeBytes()) {
        log.warn(
            "Document {} actual size {} exceeds limit (metadata said {})",
            doc.getFileName(),
            bytes.length,
            doc.getSize());
        textContent
            .append("<document file=\"")
            .append(doc.getFileName())
            .append("\" type=\"")
            .append(doc.getContentType())
            .append("\">\n");
        textContent.append("Skipped: document exceeds maximum size limit.\n");
        textContent.append("</document>\n");
        continue;
      }

      if (totalBytesFetched + bytes.length > pricingProperties.maxTotalDocumentSizeBytes()) {
        int remaining = documents.size() - i;
        textContent
            .append("Remaining ")
            .append(remaining)
            .append(" documents were too large to include.\n");
        break;
      }

      totalBytesFetched += bytes.length;

      String contentType = doc.getContentType();
      textContent
          .append("<document file=\"")
          .append(doc.getFileName())
          .append("\" type=\"")
          .append(contentType)
          .append("\">\n");

      if ("application/pdf".equals(contentType)) {
        processPdf(bytes, doc.getFileName(), textContent);
      } else if (IMAGE_TYPES.contains(contentType)) {
        processImage(doc.getFileName(), textContent);
      } else {
        textContent.append("Skipped: unsupported content type.\n");
      }

      textContent.append("</document>\n");
    }

    return new DocumentReadResult(textContent.toString());
  }

  private void processPdf(byte[] bytes, String fileName, StringBuilder textContent) {
    try (PDDocument pdf = Loader.loadPDF(bytes)) {
      PDFTextStripper stripper = new PDFTextStripper();
      String text = stripper.getText(pdf);

      if (text.length() >= SCANNED_PDF_TEXT_THRESHOLD) {
        textContent.append(text);
      } else {
        // Scanned PDF — text extraction yielded insufficient content
        textContent
            .append("Document ")
            .append(fileName)
            .append(
                " appears to be a scanned document — text extraction yielded insufficient content. Manual review recommended.\n");
      }
    } catch (IOException e) {
      log.warn("Failed to process PDF {}: {}", fileName, e.getMessage());
      textContent.append("Error: failed to process PDF document.\n");
    }
  }

  private void processImage(String fileName, StringBuilder textContent) {
    textContent
        .append("[Image document: ")
        .append(fileName)
        .append(" — content not extractable via text; manual review recommended]\n");
  }

  /** Result of reading all documents: accumulated text content. */
  public record DocumentReadResult(String textContent) {}
}
