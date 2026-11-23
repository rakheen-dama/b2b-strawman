package io.b2mash.b2b.b2bstrawman.compliance;

/**
 * PEP / sanctions screening in the previous system (CHECK {@code chk_fica_att_screening}); a
 * screening date is required unless {@code NOT_SCREENED} (CHECK {@code
 * chk_fica_att_screening_date}).
 */
public enum FicaScreeningStatus {
  SCREENED_CLEAR,
  SCREENED_MATCH,
  NOT_SCREENED
}
