package io.b2mash.b2b.b2bstrawman.integration.email;

/** Tracks the delivery lifecycle of an email message. */
public enum EmailDeliveryStatus {

  /** Email accepted by the provider and queued for delivery. */
  SENT,

  /** Email confirmed delivered to the recipient's mailbox. */
  DELIVERED,

  /** Email bounced (hard or soft bounce). */
  BOUNCED,

  /** Email send failed due to a provider or configuration error. */
  FAILED,

  /** Email rejected because the provider rate limit was exceeded. */
  RATE_LIMITED
}
