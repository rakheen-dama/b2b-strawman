package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

/**
 * Distinguishes the legal basis for a trust investment placement.
 *
 * <ul>
 *   <li>{@link #FIRM_DISCRETION} — Section 86(3): firm places funds at its own discretion
 *   <li>{@link #CLIENT_INSTRUCTION} — Section 86(4): client instructs the specific investment
 * </ul>
 */
public enum InvestmentBasis {
  FIRM_DISCRETION,
  CLIENT_INSTRUCTION
}
