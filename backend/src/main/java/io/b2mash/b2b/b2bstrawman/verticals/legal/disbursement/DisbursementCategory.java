package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

/**
 * Category of a legal disbursement. Used for default VAT treatment and statement grouping per SA
 * Legal Practice Act conventions.
 */
public enum DisbursementCategory {
  SHERIFF_FEES,
  DEEDS_OFFICE_FEES,
  COURT_FEES,
  COUNSEL_FEES,
  EXPERT_WITNESS_FEES,
  TRAVEL,
  COPYING_AND_PRINTING,
  CORRESPONDENT_FEES,
  OTHER
}
