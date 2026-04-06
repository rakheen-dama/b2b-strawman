package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import org.springframework.web.multipart.MultipartFile;

public final class FileValidationHelper {

  private static final String DOCX_CONTENT_TYPE =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  private static final long MAX_DOCX_SIZE = 10 * 1024 * 1024; // 10MB

  private FileValidationHelper() {}

  public static void validateDocxFile(MultipartFile file) {
    String contentType = file.getContentType();
    if (!DOCX_CONTENT_TYPE.equals(contentType)) {
      throw new InvalidStateException(
          "Invalid file type",
          "Expected MIME type '" + DOCX_CONTENT_TYPE + "' but got '" + contentType + "'");
    }

    if (file.getSize() > MAX_DOCX_SIZE) {
      throw new InvalidStateException(
          "File too large",
          "Maximum file size is 10MB, but the uploaded file is "
              + (file.getSize() / (1024 * 1024))
              + "MB");
    }
  }
}
