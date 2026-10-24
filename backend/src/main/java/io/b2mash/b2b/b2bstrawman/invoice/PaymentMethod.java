package io.b2mash.b2b.b2bstrawman.invoice;

/**
 * How a fee-note payment arrived (Phase 88 U15, ADR-347 A5). Stored as {@code
 * payment_events.payment_method} ({@code chk_payment_events_method}). One method per source: the
 * manual form (EFT/CASH/OTHER, or CARD for a card machine), the trust fee transfer ({@link
 * #TRUST_TRANSFER}), the pay-now webhook ({@link #CARD}) and Xero sync ({@link #XERO}).
 */
public enum PaymentMethod {
  EFT,
  CARD,
  CASH,
  TRUST_TRANSFER,
  XERO,
  OTHER;

  /**
   * Manual-entry methods (ADR-347 A4): an amount above the balance due is refused with a 409, never
   * capped — the money has not necessarily moved and a credit note is out of scope.
   */
  public boolean isManual() {
    return this == EFT || this == CASH || this == OTHER;
  }
}
