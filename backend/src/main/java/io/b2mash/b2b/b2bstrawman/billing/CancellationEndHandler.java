package io.b2mash.b2b.b2bstrawman.billing;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for processing pending cancellation end. Delegates to {@link
 * SubscriptionExpiryJob#processPendingCancellationEnd()} which queries the global {@code
 * public.subscriptions} table for PENDING_CANCELLATION subscriptions past their current period end
 * and transitions them to GRACE_PERIOD.
 *
 * <p>Note: Subscriptions live in the public schema, not per-tenant schemas. The handler delegates
 * to the same method the {@code @Scheduled} annotation calls. The method is idempotent because
 * {@code transitionTo()} changes status, so subsequent invocations find no matching rows.
 *
 * <p>Extracted from {@link SubscriptionExpiryJob#processPendingCancellationEnd()}.
 */
@Component
public class CancellationEndHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(CancellationEndHandler.class);

  private final SubscriptionExpiryJob subscriptionExpiryJob;

  public CancellationEndHandler(SubscriptionExpiryJob subscriptionExpiryJob) {
    this.subscriptionExpiryJob = subscriptionExpiryJob;
  }

  @Override
  public String jobType() {
    return "subscription_cancellation_end";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    log.debug("CancellationEndHandler: executing pending cancellation end check");
    subscriptionExpiryJob.processPendingCancellationEnd();
  }
}
