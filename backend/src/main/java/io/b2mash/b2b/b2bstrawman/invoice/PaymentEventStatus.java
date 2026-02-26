package io.b2mash.b2b.b2bstrawman.invoice;

/** Status lifecycle for payment events tracked against invoices. */
public enum PaymentEventStatus {

  /** Payment event created but not yet submitted to the provider. */
  CREATED,

  /** Payment submitted and awaiting confirmation from the provider. */
  PENDING,

  /** Payment successfully completed and confirmed by the provider. */
  COMPLETED,

  /** Payment failed due to an error (declined, insufficient funds, etc.). */
  FAILED,

  /** Payment session expired before completion. */
  EXPIRED,

  /** Payment was cancelled by the user or system before completion. */
  CANCELLED
}
