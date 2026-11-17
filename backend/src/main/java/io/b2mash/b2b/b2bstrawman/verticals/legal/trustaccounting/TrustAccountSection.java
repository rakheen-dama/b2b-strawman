package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

/**
 * The section of the Legal Practice Act a trust account reports under (Rule 54.28), derived from
 * the account type and, for an investment account, its {@link InvestmentBasis} — a value, not a
 * column (architecture §16.2.7, ADR-379). {@code SECTION_86} is treated as a section 86(2) trust
 * banking account like {@code GENERAL}: working assumption OL-1 (§16.14.2) — a different answer
 * changes {@link #of} only.
 */
public enum TrustAccountSection {
  /** Section 86(2): a trust banking account. */
  S86_2,
  /** Section 86(3): surplus trust money invested on the firm's initiative. */
  S86_3,
  /** Section 86(4): money invested on a client's instruction. */
  S86_4;

  /**
   * The one mapping (OL-1). An INVESTMENT account always carries a basis ({@code
   * chk_trust_account_investment_basis}).
   */
  public static TrustAccountSection of(TrustAccountType type, InvestmentBasis basis) {
    return switch (type) {
      case GENERAL, SECTION_86 -> S86_2;
      case INVESTMENT -> {
        if (basis == null) {
          throw new IllegalStateException("An INVESTMENT trust account has no investment basis");
        }
        yield switch (basis) {
          case FIRM_DISCRETION -> S86_3;
          case CLIENT_INSTRUCTION -> S86_4;
        };
      }
    };
  }
}
