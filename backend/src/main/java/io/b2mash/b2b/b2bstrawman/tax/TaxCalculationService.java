package io.b2mash.b2b.b2bstrawman.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/** Stateless service for calculating tax amounts on invoice lines. */
@Service
public class TaxCalculationService {

  /**
   * Calculates the tax amount for a single invoice line.
   *
   * @param amount the line amount (quantity * unitPrice)
   * @param ratePercent the tax rate percentage (e.g., 15.00 for 15%)
   * @param taxInclusive whether the amount already includes tax
   * @param taxExempt whether the line is tax-exempt (short-circuits to ZERO)
   * @return the calculated tax amount, scale 2, HALF_UP rounding
   */
  public BigDecimal calculateLineTax(
      BigDecimal amount, BigDecimal ratePercent, boolean taxInclusive, boolean taxExempt) {
    if (taxExempt) {
      return BigDecimal.ZERO;
    }
    // Zero-rated (rate = 0%) falls through: returns BigDecimal.ZERO naturally
    // but is NOT short-circuited -- zero-rated lines appear in the tax breakdown
    if (taxInclusive) {
      // Extract tax from inclusive amount
      BigDecimal divisor =
          BigDecimal.ONE.add(ratePercent.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
      BigDecimal exTaxAmount = amount.divide(divisor, 2, RoundingMode.HALF_UP);
      return amount.subtract(exTaxAmount);
    } else {
      // Calculate tax on top of ex-tax amount
      return amount.multiply(ratePercent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
  }
}
