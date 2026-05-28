package io.b2mash.b2b.b2bstrawman.integration.ai.gate;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for expiring stale AI execution gates for a single tenant. Delegates to {@link
 * AiExecutionGateService#expireStaleGatesForTenant()} which finds pending gates past their
 * expiration, marks them expired, logs audit events, and publishes gate-expired notifications.
 *
 * <p>Extracted from {@link AiExecutionGateService#expireStaleGates()}.
 */
@Component
public class AiGateExpiryHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(AiGateExpiryHandler.class);

  private final AiExecutionGateService gateService;

  public AiGateExpiryHandler(AiExecutionGateService gateService) {
    this.gateService = gateService;
  }

  @Override
  public String jobType() {
    return "ai_gate_expiry";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int expired = gateService.expireStaleGatesForTenant();
    if (expired > 0) {
      log.info("AiGateExpiryHandler: expired {} stale gates", expired);
    }
  }
}
