package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

/**
 * Approval lifecycle of a legal disbursement: {@code DRAFT} → {@code PENDING_APPROVAL} → {@code
 * APPROVED} or {@code REJECTED}.
 *
 * <p>Persisted as a varchar column (see ADR-238).
 */
public enum DisbursementApprovalStatus {
  DRAFT,
  PENDING_APPROVAL,
  APPROVED,
  REJECTED
}
