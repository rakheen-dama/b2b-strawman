package io.b2mash.b2b.b2bstrawman.tax;

import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLine;
import io.b2mash.b2b.b2bstrawman.tax.dto.TaxBreakdownEntry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  /**
   * Returns true if any line in the list has a non-null taxRateId, indicating per-line tax has been
   * applied.
   */
  public boolean hasPerLineTax(List<InvoiceLine> lines) {
    return lines.stream().anyMatch(line -> line.getTaxRateId() != null);
  }

  /**
   * Builds a tax breakdown grouped by rate name + rate percent. Excludes exempt lines. Returns an
   * empty list if no per-line tax is present.
   *
   * @param lines the invoice lines to group
   * @return list of TaxBreakdownEntry, one per distinct rate
   */
  public List<TaxBreakdownEntry> buildTaxBreakdown(List<InvoiceLine> lines) {
    if (!hasPerLineTax(lines)) {
      return List.of();
    }

    record TaxGroupKey(String name, BigDecimal percent) {}

    // Group by rateName + ratePercent, excluding exempt lines
    Map<TaxGroupKey, BigDecimal[]> groups = new LinkedHashMap<>();

    for (InvoiceLine line : lines) {
      if (line.getTaxRateId() == null || line.isTaxExempt()) {
        continue;
      }
      TaxGroupKey key = new TaxGroupKey(line.getTaxRateName(), line.getTaxRatePercent());
      groups.computeIfAbsent(key, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
      BigDecimal[] totals = groups.get(key);
      totals[0] = totals[0].add(line.getAmount());
      totals[1] =
          totals[1].add(line.getTaxAmount() != null ? line.getTaxAmount() : BigDecimal.ZERO);
    }

    List<TaxBreakdownEntry> result = new ArrayList<>();
    for (var entry : groups.entrySet()) {
      result.add(
          new TaxBreakdownEntry(
              entry.getKey().name(),
              entry.getKey().percent(),
              entry.getValue()[0],
              entry.getValue()[1]));
    }
    return result;
  }
}
