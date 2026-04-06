package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.exception.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** Thrown when PDF generation fails due to rendering or I/O errors. */
public class PdfGenerationException extends ErrorResponseException {

  public PdfGenerationException(String detail, Throwable cause) {
    super(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ProblemDetailFactory.create(
            HttpStatus.INTERNAL_SERVER_ERROR, "PDF Generation Failed", detail),
        cause);
  }
}
