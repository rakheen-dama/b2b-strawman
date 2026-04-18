package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

/**
 * Source of funds used to pay a legal disbursement.
 *
 * <ul>
 *   <li>{@code OFFICE_ACCOUNT} — paid from the firm's office / business account.
 *   <li>{@code TRUST_ACCOUNT} — paid from the firm's trust account; must be linked to a {@code
 *       TrustTransaction}.
 * </ul>
 *
 * <p>Persisted as a varchar column (see ADR-238).
 */
public enum DisbursementPaymentSource {
  OFFICE_ACCOUNT,
  TRUST_ACCOUNT
}
