package io.b2mash.b2b.b2bstrawman.assistant.tool.read;

import io.b2mash.b2b.b2bstrawman.assistant.tool.AssistantTool;
import io.b2mash.b2b.b2bstrawman.assistant.tool.TenantToolContext;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extracts embedded text from a PDF document using PDFBox. Returns text plus structural flags
 * indicating whether a text layer is present. Documents exceeding 32MB or 100 pages are rejected.
 */
@Component
public class ExtractTextFromDocumentTool implements AssistantTool {

  private static final Logger log = LoggerFactory.getLogger(ExtractTextFromDocumentTool.class);

  /** 32 MB maximum document size. */
  static final int MAX_DOCUMENT_SIZE = 32 * 1024 * 1024;

  /** Hard cap on page count — documents exceeding this are rejected. */
  static final int INTAKE_VISION_HARD_CAP = 100;

  /** Default maximum pages for vision processing. */
  static final int INTAKE_VISION_MAX_PAGES = 50;

  private final DocumentRepository documentRepository;
  private final StorageService storageService;

  public ExtractTextFromDocumentTool(
      DocumentRepository documentRepository, StorageService storageService) {
    this.documentRepository = documentRepository;
    this.storageService = storageService;
  }

  @Override
  public String name() {
    return "ExtractTextFromDocument";
  }

  @Override
  public String description() {
    return "Extract embedded text from a PDF document. Returns text plus structural flags."
        + " Documents exceeding 32MB or 100 pages are rejected with is_error.";
  }

  @Override
  public Map<String, Object> inputSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of("documentId", Map.of("type", "string", "format", "uuid")),
        "required",
        List.of("documentId"));
  }

  @Override
  public boolean requiresConfirmation() {
    return false;
  }

  @Override
  public Set<String> requiredCapabilities() {
    return Set.of("CUSTOMER_VIEW");
  }

  @Override
  public Object execute(Map<String, Object> input, TenantToolContext context) {
    var documentIdStr = (String) input.get("documentId");
    UUID documentId;
    try {
      documentId = UUID.fromString(documentIdStr);
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid documentId format: " + documentIdStr);
    }

    var document = documentRepository.findById(documentId).orElse(null);
    if (document == null) {
      return Map.of("error", "Document not found: " + documentId);
    }

    byte[] bytes;
    try {
      bytes = storageService.download(document.getS3Key());
    } catch (Exception e) {
      log.warn("Failed to download document {}: {}", documentId, e.getMessage());
      return Map.of("error", "Failed to download document");
    }

    // Pre-check: document size
    if (bytes.length > MAX_DOCUMENT_SIZE) {
      return Map.of("is_error", true, "errorMessage", "DOCUMENT_TOO_LARGE");
    }

    try (PDDocument pdf = Loader.loadPDF(bytes)) {
      int pageCount = pdf.getNumberOfPages();

      // Pre-check: page count against hard cap
      if (pageCount > INTAKE_VISION_HARD_CAP) {
        return Map.of("is_error", true, "errorMessage", "DOCUMENT_TOO_LARGE");
      }

      var stripper = new PDFTextStripper();
      String text = stripper.getText(pdf);

      boolean hasTextLayer = pageCount > 0 && text != null && !text.isBlank();
      int characterCount = text != null ? text.length() : 0;

      var result = new LinkedHashMap<String, Object>();
      result.put("documentId", documentId.toString());
      result.put("text", text != null ? text : "");
      result.put("characterCount", characterCount);
      result.put("hasTextLayer", hasTextLayer);
      result.put("pageCount", pageCount);
      return result;
    } catch (Exception e) {
      log.warn("Failed to extract text from document {}: {}", documentId, e.getMessage());
      return Map.of("error", "Failed to extract text from document: " + e.getMessage());
    }
  }
}
