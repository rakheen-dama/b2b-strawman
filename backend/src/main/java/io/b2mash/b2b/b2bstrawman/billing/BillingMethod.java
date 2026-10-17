package io.b2mash.b2b.b2bstrawman.billing;

/**
 * Billing arrangement for a tenant subscription (ADR-223, ADR-335). Manual firm-level billing is
 * the only model — every method is admin-managed through {@code AdminBillingController} / {@code
 * PlatformBillingController}. Determines which lifecycle rules apply.
 */
public enum BillingMethod {
  DEBIT_ORDER,
  PILOT,
  COMPLIMENTARY,
  MANUAL;

  /**
   * Returns true if this billing method allows trial auto-expiry. Only MANUAL (the default for new
   * subscriptions) expires automatically when the trial ends; DEBIT_ORDER, PILOT and COMPLIMENTARY
   * trials are extended or activated by a platform admin.
   */
  public boolean isTrialAutoExpiring() {
    return this == MANUAL;
  }

  /**
   * Returns true if this billing method is eligible for cleanup (e.g., removing stale pilot or
   * complimentary subscriptions that are no longer active).
   */
  public boolean isCleanupEligible() {
    return this == PILOT || this == COMPLIMENTARY;
  }
}
