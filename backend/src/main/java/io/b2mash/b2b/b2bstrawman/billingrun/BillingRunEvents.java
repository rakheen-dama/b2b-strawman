package io.b2mash.b2b.b2bstrawman.billingrun;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain events published after billing run generation completes.
 *
 * <p>{@code runName} is the run's raw (possibly null/blank) name — the creation wizard does not
 * require one. {@code periodFrom}/{@code periodTo} carry the billing period so consumers can render
 * a fallback display name for unnamed runs (LZKC-032).
 */
public final class BillingRunEvents {

  private BillingRunEvents() {}

  /** Published after every generation completes, regardless of success/failure count. */
  public record BillingRunCompletedEvent(
      UUID billingRunId,
      String runName,
      LocalDate periodFrom,
      LocalDate periodTo,
      int totalInvoices,
      String tenantId,
      String orgId) {}

  /** Published only when at least one item failed during generation. */
  public record BillingRunFailuresEvent(
      UUID billingRunId,
      String runName,
      LocalDate periodFrom,
      LocalDate periodTo,
      int failureCount,
      String tenantId,
      String orgId) {}

  /** Published after batch send completes. */
  public record BillingRunSentEvent(
      UUID billingRunId,
      String runName,
      LocalDate periodFrom,
      LocalDate periodTo,
      int totalSent,
      String tenantId,
      String orgId) {}
}
