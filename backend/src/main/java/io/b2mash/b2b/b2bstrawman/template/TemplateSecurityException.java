package io.b2mash.b2b.b2bstrawman.template;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Thrown when a document template contains dangerous patterns that could lead to SSTI. */
public class TemplateSecurityException extends ErrorResponseException {

  public TemplateSecurityException(String detail) {
    super(HttpStatus.BAD_REQUEST, createProblem(detail), null);
  }

  private static ProblemDetail createProblem(String detail) {
    var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    problem.setTitle("Template Security Violation");
    problem.setDetail(detail);
    return problem;
  }
}
