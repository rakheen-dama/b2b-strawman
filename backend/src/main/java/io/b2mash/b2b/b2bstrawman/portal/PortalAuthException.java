package io.b2mash.b2b.b2bstrawman.portal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Thrown when portal authentication fails (invalid/expired token, bad credentials). */
public class PortalAuthException extends ErrorResponseException {

  public PortalAuthException(String detail) {
    super(HttpStatus.UNAUTHORIZED, createProblem(detail), null);
  }

  private static ProblemDetail createProblem(String detail) {
    var problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
    problem.setTitle("Portal authentication failed");
    problem.setDetail(detail);
    return problem;
  }
}
