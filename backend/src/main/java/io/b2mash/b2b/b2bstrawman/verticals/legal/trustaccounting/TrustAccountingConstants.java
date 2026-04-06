package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import java.math.BigDecimal;

/**
 * Statutory constants for trust accounting calculations.
 *
 * <p>See ADR-235 for the rationale behind hardcoding statutory prescriptions rather than making
 * them configurable.
 */
public final class TrustAccountingConstants {

  private TrustAccountingConstants() {}

  /**
   * The statutory LPFF (Legal Practitioners Fidelity Fund) share percentage for client-instructed
   * investments under Section 86(5) of the Legal Practice Act. The fund is entitled to 5% of all
   * interest earned on Section 86(4) investments.
   *
   * <p>This value has not changed since the Legal Practice Act was enacted in 2014.
   */
  public static final BigDecimal STATUTORY_LPFF_SHARE_PERCENT = new BigDecimal("0.05");
}
