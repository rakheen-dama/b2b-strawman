package io.b2mash.b2b.b2bstrawman.billing;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for processing trial subscription expiry. Delegates to {@link
 * SubscriptionExpiryJob#processTrialExpiry()} which queries the global {@code public.subscriptions}
 * table for TRIALING subscriptions past their trial end date and transitions them to EXPIRED with a
 * grace period.
 *
 * <p>Note: Subscriptions live in the public schema, not per-tenant schemas. The handler delegates
 * to the same method the {@code @Scheduled} annotation calls. The method is idempotent because
 * {@code transitionTo()} changes status, so subsequent invocations find no matching rows.
 *
 * <p>Extracted from {@link SubscriptionExpiryJob#processTrialExpiry()}.
 */
@Component
public class TrialExpiryHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(TrialExpiryHandler.class);

  private final SubscriptionExpiryJob subscriptionExpiryJob;

  public TrialExpiryHandler(SubscriptionExpiryJob subscriptionExpiryJob) {
    this.subscriptionExpiryJob = subscriptionExpiryJob;
  }

  @Override
  public String jobType() {
    return "subscription_trial_expiry";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    log.debug("TrialExpiryHandler: executing trial expiry check");
    subscriptionExpiryJob.processTrialExpiry();
  }
}
