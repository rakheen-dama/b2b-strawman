package io.b2mash.b2b.b2bstrawman.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Thrown when a plan limit is exceeded (e.g., max members for tier). Returns HTTP 403 with upgrade
 * prompt per ADR-014.
 */
public class PlanLimitExceededException extends ErrorResponseException {

  public PlanLimitExceededException(String detail) {
    super(
        HttpStatus.FORBIDDEN,
        ProblemDetailFactory.create(
            HttpStatus.FORBIDDEN,
            "Plan limit exceeded",
            detail,
            Map.of("upgradeUrl", "/settings/billing")),
        null);
  }
}
