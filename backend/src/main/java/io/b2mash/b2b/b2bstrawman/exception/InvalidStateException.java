package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class InvalidStateException extends ErrorResponseException {

  public InvalidStateException(String title, String detail) {
    super(
        HttpStatus.BAD_REQUEST,
        ProblemDetailFactory.create(HttpStatus.BAD_REQUEST, title, detail),
        null);
  }
}
