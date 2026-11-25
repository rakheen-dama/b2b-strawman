package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.takeon.reader.TakeOnFileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * Backend file validation of a take-on upload (§17.6.2), independent of the frontend's: extension
 * {@code .csv} / {@code .xlsx} only, an accepted MIME type, {@code 0 < size <= 3 MB} (FR-3), and
 * the content sniffed — an {@code .xlsx} must start with the ZIP signature, a {@code .csv} must not
 * and must hold no NUL byte. {@code .xls}, {@code .xlsm}, {@code .xlsb} and {@code .ods} are
 * refused. Every refusal is a 422 before anything is stored.
 */
public final class TakeOnFileSniffer {

  /** The file's bytes (read once) and its format. */
  public record Sniffed(TakeOnSource.FileFormat format, byte[] bytes, String contentType) {}

  private static final Set<String> ACCEPTED_MIME =
      Set.of(
          "text/csv",
          "application/vnd.ms-excel",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "application/octet-stream");

  private static final byte[] ZIP_SIGNATURE = {0x50, 0x4B, 0x03, 0x04};

  private TakeOnFileSniffer() {}

  public static Sniffed sniff(MultipartFile file) {
    TakeOnSource.FileFormat format = formatOf(file.getOriginalFilename());
    String contentType = normaliseMime(file.getContentType());
    if (!contentType.isEmpty() && !ACCEPTED_MIME.contains(contentType)) {
      throw TakeOnProblems.fileUnsupported();
    }
    if (file.getSize() > TakeOnFileReader.MAX_FILE_BYTES) {
      throw TakeOnProblems.fileTooLarge();
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read the uploaded take-on file", e);
    }
    if (bytes.length == 0) {
      throw TakeOnProblems.fileEmpty();
    }
    if (bytes.length > TakeOnFileReader.MAX_FILE_BYTES) {
      throw TakeOnProblems.fileTooLarge();
    }
    boolean zip = startsWithZipSignature(bytes);
    if (format == TakeOnSource.FileFormat.XLSX && !zip) {
      throw TakeOnProblems.fileUnsupported();
    }
    if (format == TakeOnSource.FileFormat.CSV && (zip || containsNul(bytes))) {
      throw TakeOnProblems.fileUnsupported();
    }
    String stored =
        contentType.isEmpty() || contentType.equals("application/octet-stream")
            ? defaultMime(format)
            : contentType;
    return new Sniffed(format, bytes, stored);
  }

  /** {@code .csv} → CSV, {@code .xlsx} → XLSX (case-insensitive); anything else is refused. */
  static TakeOnSource.FileFormat formatOf(String fileName) {
    String name = fileName == null ? "" : fileName.strip().toLowerCase(Locale.ROOT);
    if (name.endsWith(".csv")) {
      return TakeOnSource.FileFormat.CSV;
    }
    if (name.endsWith(".xlsx")) {
      return TakeOnSource.FileFormat.XLSX;
    }
    throw TakeOnProblems.fileUnsupported();
  }

  private static String normaliseMime(String contentType) {
    if (contentType == null) {
      return "";
    }
    int semicolon = contentType.indexOf(';');
    String base = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
    return base.strip().toLowerCase(Locale.ROOT);
  }

  private static String defaultMime(TakeOnSource.FileFormat format) {
    return format == TakeOnSource.FileFormat.XLSX
        ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        : "text/csv";
  }

  private static boolean startsWithZipSignature(byte[] bytes) {
    if (bytes.length < ZIP_SIGNATURE.length) {
      return false;
    }
    for (int i = 0; i < ZIP_SIGNATURE.length; i++) {
      if (bytes[i] != ZIP_SIGNATURE[i]) {
        return false;
      }
    }
    return true;
  }

  private static boolean containsNul(byte[] bytes) {
    for (byte b : bytes) {
      if (b == 0) {
        return true;
      }
    }
    return false;
  }
}
