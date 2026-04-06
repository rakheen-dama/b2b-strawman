package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ForbiddenException extends ErrorResponseException {

  public ForbiddenException(String title, String detail) {
    super(
        HttpStatus.FORBIDDEN,
        ProblemDetailFactory.create(HttpStatus.FORBIDDEN, title, detail),
        null);
  }
}
