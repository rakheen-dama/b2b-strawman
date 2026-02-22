package io.b2mash.b2b.b2bstrawman.integration;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when an integration domain is not enabled for the current tenant. Returns HTTP 403 with
 * ProblemDetail per ADR-091.
 */
public class IntegrationDisabledException extends ErrorResponseException {

  public IntegrationDisabledException(IntegrationDomain domain) {
    super(HttpStatus.FORBIDDEN, createProblem(domain), null);
  }

  private static ProblemDetail createProblem(IntegrationDomain domain) {
    var problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
    problem.setTitle("Integration Disabled");
    problem.setDetail(
        "The " + domain.name() + " integration domain is not enabled for this organization.");
    return problem;
  }
}
