package io.b2mash.b2b.b2bstrawman.billingrun;

import java.util.UUID;

/** Domain events published after billing run generation completes. */
public final class BillingRunEvents {

  private BillingRunEvents() {}

  /** Published after every generation completes, regardless of success/failure count. */
  public record BillingRunCompletedEvent(
      UUID billingRunId, String runName, int totalInvoices, String tenantId, String orgId) {}

  /** Published only when at least one item failed during generation. */
  public record BillingRunFailuresEvent(
      UUID billingRunId, String runName, int failureCount, String tenantId, String orgId) {}

  /** Published after batch send completes. */
  public record BillingRunSentEvent(
      UUID billingRunId, String runName, int totalSent, String tenantId, String orgId) {}
}
