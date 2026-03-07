package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class TooManyRequestsException extends ErrorResponseException {

  public TooManyRequestsException(String title, String detail) {
    super(HttpStatus.TOO_MANY_REQUESTS, createProblem(title, detail), null);
  }

  private static ProblemDetail createProblem(String title, String detail) {
    var problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
    problem.setTitle(title);
    problem.setDetail(detail);
    return problem;
  }
}
