package io.b2mash.b2b.b2bstrawman.tax.dto;

import java.math.BigDecimal;

/** Groups tax totals by rate for invoice-level tax breakdown display. */
public record TaxBreakdownEntry(
    String rateName, BigDecimal ratePercent, BigDecimal taxableAmount, BigDecimal taxAmount) {}
