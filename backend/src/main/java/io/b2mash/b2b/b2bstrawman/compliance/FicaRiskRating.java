package io.b2mash.b2b.b2bstrawman.compliance;

/**
 * The client's FICA risk rating as recorded in the firm's previous system (Phase 93 655A, §17.2.5).
 * A blank rating in a take-on file is {@code UNRATED}, which has no review date. Stored in {@code
 * client_fica_attestations.risk_rating} (CHECK {@code chk_fica_att_risk}).
 */
public enum FicaRiskRating {
  LOW,
  MEDIUM,
  HIGH,
  UNRATED
}
