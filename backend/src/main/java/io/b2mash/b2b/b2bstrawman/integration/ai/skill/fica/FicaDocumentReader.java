package io.b2mash.b2b.b2bstrawman.integration.ai.skill.fica;

import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.integration.ai.AiImageInput;
import io.b2mash.b2b.b2bstrawman.integration.ai.cost.AiPricingProperties;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads customer documents from S3, extracts text via PDFBox, and falls back to vision (image
 * rendering) for scanned PDFs. Images are passed as base64-encoded data for the AI prompt.
 */
@Service
public class FicaDocumentReader {

  private static final Logger log = LoggerFactory.getLogger(FicaDocumentReader.class);
  private static final int SCANNED_PDF_TEXT_THRESHOLD = 100;
  private static final float RENDER_DPI = 150f;
  private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/jpg");

  private final StorageService storageService;
  private final AiPricingProperties pricingProperties;

  public FicaDocumentReader(StorageService storageService, AiPricingProperties pricingProperties) {
    this.storageService = storageService;
    this.pricingProperties = pricingProperties;
  }

  public DocumentReadResult readDocuments(List<Document> documents) {
    var textContent = new StringBuilder();
    var images = new ArrayList<AiImageInput>();
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

      byte[] bytes = storageService.download(doc.getS3Key());
      totalBytesFetched += bytes.length;

      String contentType = doc.getContentType();
      textContent
          .append("<document file=\"")
          .append(doc.getFileName())
          .append("\" type=\"")
          .append(contentType)
          .append("\">\n");

      if ("application/pdf".equals(contentType)) {
        processPdf(bytes, doc.getFileName(), textContent, images);
      } else if (IMAGE_TYPES.contains(contentType)) {
        processImage(bytes, contentType, textContent, images);
      } else {
        textContent.append("Skipped: unsupported content type.\n");
      }

      textContent.append("</document>\n");
    }

    return new DocumentReadResult(textContent.toString(), images);
  }

  private void processPdf(
      byte[] bytes, String fileName, StringBuilder textContent, List<AiImageInput> images) {
    try (PDDocument pdf = Loader.loadPDF(bytes)) {
      PDFTextStripper stripper = new PDFTextStripper();
      String text = stripper.getText(pdf);

      if (text.length() >= SCANNED_PDF_TEXT_THRESHOLD) {
        textContent.append(text);
      } else {
        // Scanned PDF — fall back to vision by rendering pages as images
        textContent.append("Vision input — see attached image.\n");
        PDFRenderer renderer = new PDFRenderer(pdf);
        for (int page = 0; page < pdf.getNumberOfPages(); page++) {
          BufferedImage image = renderer.renderImageWithDPI(page, RENDER_DPI, ImageType.RGB);
          String base64 = encodeImage(image);
          if (base64 != null) {
            images.add(new AiImageInput("image/png", base64));
          }
        }
      }
    } catch (IOException e) {
      log.warn("Failed to process PDF {}: {}", fileName, e.getMessage());
      textContent.append("Error: failed to process PDF document.\n");
    }
  }

  private void processImage(
      byte[] bytes, String contentType, StringBuilder textContent, List<AiImageInput> images) {
    textContent.append("Vision input — see attached image.\n");
    String base64 = Base64.getEncoder().encodeToString(bytes);
    images.add(new AiImageInput(contentType, base64));
  }

  private String encodeImage(BufferedImage image) {
    try (var baos = new ByteArrayOutputStream()) {
      ImageIO.write(image, "PNG", baos);
      return Base64.getEncoder().encodeToString(baos.toByteArray());
    } catch (IOException e) {
      log.warn("Failed to encode PDF page as image: {}", e.getMessage());
      return null;
    }
  }

  /** Result of reading all documents: accumulated text and extracted images. */
  public record DocumentReadResult(String textContent, List<AiImageInput> images) {}
}
