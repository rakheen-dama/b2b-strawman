package io.b2mash.b2b.b2bstrawman.integration.ai.skill;

import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class DocumentTextExtractorService {

  private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractorService.class);
  private static final int MAX_TEXT_LENGTH = 102_400; // 100KB

  private final StorageService storageService;
  private final ObjectMapper objectMapper;

  public DocumentTextExtractorService(StorageService storageService, ObjectMapper objectMapper) {
    this.storageService = storageService;
    this.objectMapper = objectMapper;
  }

  public ExtractedText extractText(Document document) {
    byte[] bytes = storageService.download(document.getS3Key());
    String contentType = document.getContentType();

    String text;
    if ("application/pdf".equals(contentType)) {
      text = extractFromPdf(bytes, document.getFileName());
    } else if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        .equals(contentType)) {
      text = extractFromDocx(bytes, document.getFileName());
    } else if ("application/json".equals(contentType)) {
      text = extractFromTiptap(bytes, document.getFileName());
    } else {
      throw new InvalidStateException(
          "UNSUPPORTED_DOCUMENT", "Cannot extract text from document type: " + contentType);
    }

    if (text.length() > MAX_TEXT_LENGTH) {
      String truncated = text.substring(0, MAX_TEXT_LENGTH);
      String warning =
          String.format(
              "Document text truncated from %d to %d characters (100KB limit).",
              text.length(), MAX_TEXT_LENGTH);
      return new ExtractedText(truncated, MAX_TEXT_LENGTH, true, warning);
    }

    return new ExtractedText(text, text.length(), false, null);
  }

  // NOTE: This PDF extraction logic is structurally similar to FicaDocumentReader.processPdf().
  // Consolidation opportunity — a shared PdfTextExtractor utility could serve both call-sites.
  private String extractFromPdf(byte[] bytes, String fileName) {
    try (PDDocument pdf = Loader.loadPDF(bytes)) {
      PDFTextStripper stripper = new PDFTextStripper();
      return stripper.getText(pdf);
    } catch (IOException e) {
      log.warn("Failed to extract text from PDF {}: {}", fileName, e.getMessage());
      throw new InvalidStateException(
          "UNSUPPORTED_DOCUMENT", "Failed to extract text from PDF: " + e.getMessage());
    }
  }

  private String extractFromDocx(byte[] bytes, String fileName) {
    try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(bytes))) {
      var sb = new StringBuilder();
      for (XWPFParagraph paragraph : docx.getParagraphs()) {
        String text = paragraph.getText();
        if (text != null && !text.isEmpty()) {
          sb.append(text).append("\n");
        }
      }
      return sb.toString();
    } catch (IOException e) {
      log.warn("Failed to extract text from DOCX {}: {}", fileName, e.getMessage());
      throw new InvalidStateException(
          "UNSUPPORTED_DOCUMENT", "Failed to extract text from DOCX: " + e.getMessage());
    }
  }

  private static final int MAX_RECURSION_DEPTH = 100;

  private String extractFromTiptap(byte[] bytes, String fileName) {
    try {
      JsonNode root = objectMapper.readTree(bytes);

      // Validate that this is a Tiptap document (root must have "type": "doc")
      if (!root.isObject() || !"doc".equals(root.path("type").asText())) {
        throw new InvalidStateException(
            "UNSUPPORTED_DOCUMENT", "JSON document is not in Tiptap format");
      }

      var sb = new StringBuilder();
      extractTextNodes(root, sb, 0);
      return sb.toString();
    } catch (InvalidStateException e) {
      throw e;
    } catch (Exception e) {
      log.warn("Failed to extract text from Tiptap JSON {}: {}", fileName, e.getMessage());
      throw new InvalidStateException(
          "UNSUPPORTED_DOCUMENT", "Failed to extract text from Tiptap JSON: " + e.getMessage());
    }
  }

  private void extractTextNodes(JsonNode node, StringBuilder sb, int depth) {
    if (depth > MAX_RECURSION_DEPTH) {
      throw new InvalidStateException(
          "UNSUPPORTED_DOCUMENT",
          "Tiptap JSON exceeds maximum nesting depth of " + MAX_RECURSION_DEPTH);
    }
    if (node.isObject()) {
      String type = node.path("type").asText();

      // Leaf text node
      if ("text".equals(type) && node.has("text")) {
        sb.append(node.get("text").asText());
        return;
      }

      // Block-level nodes: process content, then add newline
      if ("paragraph".equals(type) || "heading".equals(type) || "listItem".equals(type)) {
        JsonNode content = node.get("content");
        if (content != null && content.isArray()) {
          for (JsonNode child : content) {
            extractTextNodes(child, sb, depth + 1);
          }
        }
        sb.append("\n");
        return;
      }

      // Container nodes (doc, bulletList, orderedList, etc.): recurse into content
      JsonNode content = node.get("content");
      if (content != null && content.isArray()) {
        for (JsonNode child : content) {
          extractTextNodes(child, sb, depth + 1);
        }
      }
    } else if (node.isArray()) {
      for (JsonNode child : node) {
        extractTextNodes(child, sb, depth + 1);
      }
    }
  }
}
