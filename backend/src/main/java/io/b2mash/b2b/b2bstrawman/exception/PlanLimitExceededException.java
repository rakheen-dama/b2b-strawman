package io.b2mash.b2b.b2bstrawman.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when a plan limit is exceeded (e.g., max members for tier). Returns HTTP 403 with upgrade
 * prompt per ADR-014.
 */
public class PlanLimitExceededException extends ErrorResponseException {

  public PlanLimitExceededException(String detail) {
    super(HttpStatus.FORBIDDEN, createProblem(detail), null);
  }

  private static ProblemDetail createProblem(String detail) {
    var problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    problem.setTitle("Plan limit exceeded");
    problem.setDetail(detail);
    problem.setProperty("upgradeUrl", "/settings/billing");
    return problem;
  }
}
