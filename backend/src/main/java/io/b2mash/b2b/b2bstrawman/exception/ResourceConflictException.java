package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ResourceConflictException extends ErrorResponseException {

  public ResourceConflictException(String title, String detail) {
    super(
        HttpStatus.CONFLICT, ProblemDetailFactory.create(HttpStatus.CONFLICT, title, detail), null);
  }
}
