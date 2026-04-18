package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

/**
 * Category of a legal disbursement — out-of-pocket costs incurred on behalf of clients.
 *
 * <p>Persisted as a varchar column (see ADR-238). Use {@link #fromString(String)} for
 * case-insensitive parsing.
 */
public enum DisbursementCategory {
  SHERIFF_FEES,
  COUNSEL_FEES,
  SEARCH_FEES,
  DEEDS_OFFICE_FEES,
  COURT_FEES,
  ADVOCATE_FEES,
  EXPERT_WITNESS,
  TRAVEL,
  OTHER;

  /**
   * Case-insensitive lookup of a DisbursementCategory from a string value.
   *
   * @param value the string to convert
   * @return the matching DisbursementCategory
   * @throws IllegalArgumentException if the value does not match any category
   */
  public static DisbursementCategory fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Disbursement category must not be null");
    }
    try {
      return valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown disbursement category: " + value);
    }
  }
}
