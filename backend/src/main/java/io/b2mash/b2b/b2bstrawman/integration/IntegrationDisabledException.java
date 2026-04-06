package io.b2mash.b2b.b2bstrawman.integration;

import io.b2mash.b2b.b2bstrawman.exception.ProblemDetailFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when an integration domain is not enabled for the current tenant. Returns HTTP 403 with
 * ProblemDetail per ADR-091.
 */
public class IntegrationDisabledException extends ErrorResponseException {

  public IntegrationDisabledException(IntegrationDomain domain) {
    super(
        HttpStatus.FORBIDDEN,
        ProblemDetailFactory.create(
            HttpStatus.FORBIDDEN,
            "Integration Disabled",
            "The " + domain.name() + " integration domain is not enabled for this organization."),
        null);
  }
}
