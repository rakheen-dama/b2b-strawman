package io.b2mash.b2b.b2bstrawman.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * 422: the request is well formed but its content cannot be accepted, with extra ProblemDetail
 * properties (e.g. a machine-readable {@code code} the client can branch on).
 */
public class UnprocessableEntityException extends ErrorResponseException {

  public UnprocessableEntityException(String title, String detail, Map<String, Object> properties) {
    super(
        HttpStatus.UNPROCESSABLE_ENTITY,
        ProblemDetailFactory.create(HttpStatus.UNPROCESSABLE_ENTITY, title, detail, properties),
        null);
  }
}
