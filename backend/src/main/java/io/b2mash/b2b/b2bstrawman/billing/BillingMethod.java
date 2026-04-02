package io.b2mash.b2b.b2bstrawman.billing;

/**
 * Payment method dimension for subscriptions. Determines which lifecycle rules apply and whether
 * admin intervention is required for state transitions.
 */
public enum BillingMethod {
  PAYFAST,
  DEBIT_ORDER,
  PILOT,
  COMPLIMENTARY,
  MANUAL;

  /**
   * Returns true if this billing method requires admin management (not self-service via PayFast).
   */
  public boolean isAdminManaged() {
    return this != PAYFAST;
  }

  /**
   * Returns true if this billing method allows trial auto-expiry. Only PAYFAST (self-service) and
   * MANUAL (default) subscriptions expire automatically when the trial ends.
   */
  public boolean isTrialAutoExpiring() {
    return this == PAYFAST || this == MANUAL;
  }

  /**
   * Returns true if this billing method is eligible for cleanup (e.g., removing stale pilot or
   * complimentary subscriptions that are no longer active).
   */
  public boolean isCleanupEligible() {
    return this == PILOT || this == COMPLIMENTARY;
  }
}
