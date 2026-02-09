package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ForbiddenException extends ErrorResponseException {

  public ForbiddenException(String title, String detail) {
    super(HttpStatus.FORBIDDEN, createProblem(title, detail), null);
  }

  private static ProblemDetail createProblem(String title, String detail) {
    var problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    problem.setTitle(title);
    problem.setDetail(detail);
    return problem;
  }
}
