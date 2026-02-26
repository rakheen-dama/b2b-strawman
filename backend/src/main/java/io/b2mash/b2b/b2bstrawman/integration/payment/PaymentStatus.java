package io.b2mash.b2b.b2bstrawman.integration.payment;

/**
 * Status of a PSP payment session or payment query result. Used by {@link
 * PaymentGateway#queryPaymentStatus(String)} and {@link WebhookResult}.
 */
public enum PaymentStatus {
  /** Payment not yet completed. */
  PENDING,
  /** Payment successful. */
  COMPLETED,
  /** Payment failed (card declined, insufficient funds, etc.). */
  FAILED,
  /** Checkout session expired (e.g., Stripe 24-hour expiry). */
  EXPIRED,
  /** Session cancelled (e.g., manual payment recorded while session was active). */
  CANCELLED
}
