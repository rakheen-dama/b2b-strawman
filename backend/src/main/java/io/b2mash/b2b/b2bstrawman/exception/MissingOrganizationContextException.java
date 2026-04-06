package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

public class MissingOrganizationContextException extends ErrorResponseException {

  public MissingOrganizationContextException() {
    super(
        HttpStatus.UNAUTHORIZED,
        ProblemDetailFactory.create(
            HttpStatus.UNAUTHORIZED,
            "Missing organization context",
            "JWT token does not contain organization claim"),
        null);
  }
}
