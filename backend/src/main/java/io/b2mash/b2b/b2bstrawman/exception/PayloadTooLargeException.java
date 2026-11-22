package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/** 413: a request body larger than the endpoint accepts (refused before it is read further). */
public class PayloadTooLargeException extends ErrorResponseException {

  public PayloadTooLargeException(String title, String detail) {
    super(
        HttpStatus.CONTENT_TOO_LARGE,
        ProblemDetailFactory.create(HttpStatus.CONTENT_TOO_LARGE, title, detail),
        null);
  }
}
