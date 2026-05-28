package io.b2mash.b2b.b2bstrawman.assistant.invocation;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for sweeping expired AI specialist invocations for a single tenant. Delegates to
 * {@link AiInvocationExpirySweeper#sweepForTenant()} which expires stale PENDING_APPROVAL rows and
 * nulls retention-aged outputs for POPIA compliance.
 *
 * <p>Extracted from {@link AiInvocationExpirySweeper#sweep()}.
 */
@Component
public class AiInvocationExpiryHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(AiInvocationExpiryHandler.class);

  private final AiInvocationExpirySweeper expirySweeper;

  public AiInvocationExpiryHandler(AiInvocationExpirySweeper expirySweeper) {
    this.expirySweeper = expirySweeper;
  }

  @Override
  public String jobType() {
    return "ai_invocation_expiry";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    int[] result = expirySweeper.sweepForTenant();
    if (result != null && (result[0] > 0 || result[1] > 0)) {
      log.info("AiInvocationExpiryHandler: expired {}, retention-nulled {}", result[0], result[1]);
    }
  }
}
