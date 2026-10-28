package io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement;

import java.util.Locale;

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
   * Client-facing sentence-case label — {@code SHERIFF_FEES} → "Sheriff fees", {@code
   * DEEDS_OFFICE_FEES} → "Deeds office fees" (Phase 88 626E, G-C12). Used by the fee-note line
   * description and the Statement of Account category column.
   */
  public String label() {
    String words = name().replace('_', ' ').toLowerCase(Locale.ROOT);
    return Character.toUpperCase(words.charAt(0)) + words.substring(1);
  }

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
      return valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown disbursement category: " + value);
    }
  }
}
