package io.b2mash.b2b.b2bstrawman.template;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Converts DOCX bytes to PDF using a cascade strategy: LibreOffice headless (primary) with docx4j
 * as a fallback. Returns {@code Optional.empty()} when no converter is available, enabling graceful
 * degradation in the calling service.
 */
@Service
public class PdfConversionService {

  private static final Logger log = LoggerFactory.getLogger(PdfConversionService.class);
  private static final int LIBREOFFICE_TIMEOUT_SECONDS = 30;

  private boolean libreOfficeAvailable;

  @PostConstruct
  void checkLibreOfficeAvailability() {
    try {
      var process = new ProcessBuilder("which", "soffice").start();
      boolean finished = process.waitFor(5, TimeUnit.SECONDS);
      libreOfficeAvailable = finished && process.exitValue() == 0;
    } catch (Exception e) {
      libreOfficeAvailable = false;
    }

    if (libreOfficeAvailable) {
      log.info("LibreOffice detected — PDF conversion via soffice available");
    } else {
      log.warn("LibreOffice not found — PDF conversion will fall back to docx4j");
    }
  }

  /**
   * Converts DOCX bytes to PDF. Tries LibreOffice first, then docx4j, then returns empty if neither
   * is available.
   */
  public Optional<byte[]> convertToPdf(byte[] docxBytes) {
    if (libreOfficeAvailable) {
      var result = convertViaLibreOffice(docxBytes);
      if (result.isPresent()) {
        return result;
      }
      log.warn("LibreOffice conversion failed — falling back to docx4j");
    }

    var docx4jResult = convertViaDocx4j(docxBytes);
    if (docx4jResult.isPresent()) {
      return docx4jResult;
    }

    log.warn("All PDF conversion methods unavailable");
    return Optional.empty();
  }

  Optional<byte[]> convertViaLibreOffice(byte[] docxBytes) {
    Path tmpDir = null;
    try {
      tmpDir = Files.createTempDirectory("pdf-convert-");
      Path tmpFile = tmpDir.resolve("input.docx");
      Files.write(tmpFile, docxBytes);

      var process =
          new ProcessBuilder(
                  "soffice",
                  "--headless",
                  "--convert-to",
                  "pdf",
                  "--outdir",
                  tmpDir.toString(),
                  tmpFile.toString())
              .redirectErrorStream(true)
              .start();

      boolean finished = process.waitFor(LIBREOFFICE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (!finished) {
        process.destroyForcibly();
        log.warn("LibreOffice conversion timed out after {}s", LIBREOFFICE_TIMEOUT_SECONDS);
        return Optional.empty();
      }

      if (process.exitValue() != 0) {
        log.warn("LibreOffice conversion failed with exit code {}", process.exitValue());
        return Optional.empty();
      }

      Path pdfFile = tmpDir.resolve("input.pdf");
      if (!Files.exists(pdfFile)) {
        log.warn("LibreOffice conversion produced no output PDF");
        return Optional.empty();
      }

      byte[] pdfBytes = Files.readAllBytes(pdfFile);
      log.info("LibreOffice conversion succeeded: {} bytes", pdfBytes.length);
      return Optional.of(pdfBytes);
    } catch (IOException | InterruptedException e) {
      log.warn("LibreOffice conversion failed: {}", e.getMessage());
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return Optional.empty();
    } finally {
      cleanupTempDir(tmpDir);
    }
  }

  Optional<byte[]> convertViaDocx4j(byte[] docxBytes) {
    try {
      // Check if docx4j is on the classpath (optional dependency)
      Class.forName("org.docx4j.Docx4J");
    } catch (ClassNotFoundException e) {
      log.debug("docx4j not available on classpath — skipping docx4j conversion");
      return Optional.empty();
    }

    try {
      var inputStream = new java.io.ByteArrayInputStream(docxBytes);
      var wordMLPackage =
          org.docx4j.openpackaging.packages.WordprocessingMLPackage.load(inputStream);
      var outputStream = new java.io.ByteArrayOutputStream();
      org.docx4j.Docx4J.toPDF(wordMLPackage, outputStream);
      byte[] pdfBytes = outputStream.toByteArray();
      log.info("docx4j conversion succeeded: {} bytes", pdfBytes.length);
      return Optional.of(pdfBytes);
    } catch (Exception | NoClassDefFoundError | NoSuchMethodError | ExceptionInInitializerError e) {
      log.warn("docx4j conversion failed: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private void cleanupTempDir(Path tmpDir) {
    if (tmpDir == null) {
      return;
    }
    try {
      try (var files = Files.list(tmpDir)) {
        files.forEach(
            f -> {
              try {
                Files.deleteIfExists(f);
              } catch (IOException e) {
                log.debug("Failed to clean up temp file: {}", f);
              }
            });
      }
      Files.deleteIfExists(tmpDir);
    } catch (IOException e) {
      log.debug("Failed to clean up temp directory: {}", tmpDir);
    }
  }

  // Visible for testing
  void setLibreOfficeAvailable(boolean available) {
    this.libreOfficeAvailable = available;
  }

  boolean isLibreOfficeAvailable() {
    return libreOfficeAvailable;
  }
}
