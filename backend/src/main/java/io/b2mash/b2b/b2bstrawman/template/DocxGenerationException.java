package io.b2mash.b2b.b2bstrawman.template;

import io.b2mash.b2b.b2bstrawman.exception.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class DocxGenerationException extends ErrorResponseException {

  public DocxGenerationException(String detail, Throwable cause) {
    super(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ProblemDetailFactory.create(
            HttpStatus.INTERNAL_SERVER_ERROR, "DOCX Generation Failed", detail),
        cause);
  }
}
