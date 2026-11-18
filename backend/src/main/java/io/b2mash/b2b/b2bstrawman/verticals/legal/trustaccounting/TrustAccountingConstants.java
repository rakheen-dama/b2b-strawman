package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting;

import java.math.BigDecimal;

/**
 * Statutory constants for trust accounting calculations.
 *
 * <p>See ADR-379 (section 86(4) interest entries, Phase 92 648B; it supersedes ADR-235, whose
 * reasoning for hardcoding statutory prescriptions rather than making them configurable survives):
 * the Fund's share is a code constant, not a setting and not a database CHECK. The rounding rule —
 * half-up to the cent on the gross, a typed share within {@link #STATUTORY_LPFF_SHARE_TOLERANCE}
 * accepted so the bank's own figure can be mirrored — is a working assumption (OL-5); a different
 * answer changes these constants only.
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

  /**
   * How far a typed Fund share may differ from {@code round(gross × 5%, 2, HALF_UP)} on a section
   * 86(4) interest entry (OL-5 working assumption, ADR-379): R0,01, so a bank's own rounding can be
   * mirrored; anything further is refused.
   */
  public static final BigDecimal STATUTORY_LPFF_SHARE_TOLERANCE = new BigDecimal("0.01");
}
