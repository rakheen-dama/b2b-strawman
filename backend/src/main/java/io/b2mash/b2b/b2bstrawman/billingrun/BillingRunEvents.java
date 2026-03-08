package io.b2mash.b2b.b2bstrawman.billingrun;

/** Domain events published after billing run generation completes. */
public final class BillingRunEvents {

  private BillingRunEvents() {}

  /** Published after every generation completes, regardless of success/failure count. */
  public record BillingRunCompletedEvent(BillingRun run) {}

  /** Published only when at least one item failed during generation. */
  public record BillingRunFailuresEvent(BillingRun run, int failureCount) {}
}
