package io.b2mash.b2b.b2bstrawman.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for expiring overdue acceptance requests for a single tenant. Delegates to {@link
 * AcceptanceService#processExpiredForTenant()} which finds and expires overdue acceptance requests.
 *
 * <p>Extracted from {@link AcceptanceExpiryProcessor#processExpired()}.
 */
@Component
public class AcceptanceExpiryHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(AcceptanceExpiryHandler.class);

  private final AcceptanceService acceptanceService;

  public AcceptanceExpiryHandler(AcceptanceService acceptanceService) {
    this.acceptanceService = acceptanceService;
  }

  @Override
  public String jobType() {
    return "acceptance_expiry";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int expired = acceptanceService.processExpiredForTenant();
    if (expired > 0) {
      log.info("AcceptanceExpiryHandler: expired {} acceptance requests", expired);
    }
  }
}
