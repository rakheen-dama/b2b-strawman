package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

/** SA VAT treatment for a disbursement. Statutory pass-through categories are zero-rated. */
public enum VatTreatment {
  STANDARD_15,
  ZERO_RATED_PASS_THROUGH,
  EXEMPT
}
