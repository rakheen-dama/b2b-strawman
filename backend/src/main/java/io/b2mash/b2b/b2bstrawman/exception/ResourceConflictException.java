package io.b2mash.b2b.b2bstrawman.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class ResourceConflictException extends ErrorResponseException {

  public ResourceConflictException(String title, String detail) {
    super(
        HttpStatus.CONFLICT, ProblemDetailFactory.create(HttpStatus.CONFLICT, title, detail), null);
  }

  /**
   * 409 with extra ProblemDetail properties (e.g. a machine-readable {@code code} the client can
   * branch on).
   */
  public ResourceConflictException(String title, String detail, Map<String, Object> properties) {
    super(
        HttpStatus.CONFLICT,
        ProblemDetailFactory.create(HttpStatus.CONFLICT, title, detail, properties),
        null);
  }
}
