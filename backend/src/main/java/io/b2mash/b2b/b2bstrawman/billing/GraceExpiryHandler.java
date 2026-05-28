package io.b2mash.b2b.b2bstrawman.billing;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Job handler for processing grace period expiry. Delegates to {@link
 * SubscriptionExpiryJob#doProcessGraceExpiry()} which queries the global {@code
 * public.subscriptions} table for GRACE_PERIOD/EXPIRED/SUSPENDED subscriptions past their grace end
 * date and transitions them to LOCKED.
 *
 * <p>Note: Subscriptions live in the public schema, not per-tenant schemas. The handler delegates
 * to the extracted processing method (not the {@code @Scheduled} method) to avoid re-enqueuing jobs
 * via {@code fanOutToAllTenants}. The method is idempotent because {@code transitionTo()} changes
 * status, so subsequent invocations find no matching rows.
 */
@Component
public class GraceExpiryHandler implements JobHandler {

  private static final Logger log = LoggerFactory.getLogger(GraceExpiryHandler.class);

  private final SubscriptionExpiryJob subscriptionExpiryJob;

  public GraceExpiryHandler(SubscriptionExpiryJob subscriptionExpiryJob) {
    this.subscriptionExpiryJob = subscriptionExpiryJob;
  }

  @Override
  public String jobType() {
    return "subscription_grace_expiry";
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    log.debug("GraceExpiryHandler: executing grace period expiry check");
    subscriptionExpiryJob.doProcessGraceExpiry();
  }
}
