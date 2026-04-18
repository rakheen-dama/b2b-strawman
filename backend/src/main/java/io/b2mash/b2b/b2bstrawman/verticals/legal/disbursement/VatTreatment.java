package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

/**
 * VAT treatment for a legal disbursement.
 *
 * <ul>
 *   <li>{@code STANDARD_15} — standard 15% VAT applies.
 *   <li>{@code ZERO_RATED_PASS_THROUGH} — pass-through disbursement with zero VAT.
 *   <li>{@code EXEMPT} — VAT-exempt.
 * </ul>
 *
 * <p>Persisted as a varchar column (see ADR-238).
 */
public enum VatTreatment {
  STANDARD_15,
  ZERO_RATED_PASS_THROUGH,
  EXEMPT
}
