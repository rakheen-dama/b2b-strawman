package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class MissingOrganizationContextException extends ErrorResponseException {

  public MissingOrganizationContextException() {
    super(HttpStatus.UNAUTHORIZED, createProblem(), null);
  }

  private static ProblemDetail createProblem() {
    var problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
    problem.setTitle("Missing organization context");
    problem.setDetail("JWT token does not contain organization claim");
    return problem;
  }
}
