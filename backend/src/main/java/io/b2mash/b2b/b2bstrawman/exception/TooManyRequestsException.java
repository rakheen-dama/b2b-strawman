package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class TooManyRequestsException extends ErrorResponseException {

  public TooManyRequestsException(String title, String detail) {
    super(
        HttpStatus.TOO_MANY_REQUESTS,
        ProblemDetailFactory.create(HttpStatus.TOO_MANY_REQUESTS, title, detail),
        null);
  }
}
