package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

/**
 * Billing lifecycle of a legal disbursement: {@code UNBILLED} → {@code BILLED} or {@code
 * WRITTEN_OFF}.
 *
 * <p>Persisted as a varchar column (see ADR-238).
 */
public enum DisbursementBillingStatus {
  UNBILLED,
  BILLED,
  WRITTEN_OFF
}
