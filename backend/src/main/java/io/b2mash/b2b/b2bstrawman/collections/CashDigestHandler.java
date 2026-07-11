package io.b2mash.b2b.b2bstrawman.collections;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for the weekly cash digest (Phase 83, ADR-328). Tenant scope is pre-bound by the
 * {@code JobWorker}; this delegates to {@link CashDigestService#processTenant()}.
 */
@Component
public class CashDigestHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(CashDigestHandler.class);

  private final CashDigestService cashDigestService;

  public CashDigestHandler(CashDigestService cashDigestService) {
    this.cashDigestService = cashDigestService;
  }

  @Override
  public String jobType() {
    return "cash_digest";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    log.debug("CashDigestHandler: executing cash digest for tenant");
    cashDigestService.processTenant();
  }
}
