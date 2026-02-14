package io.b2mash.b2b.b2bstrawman.report;

import java.math.BigDecimal;

/** Spring Data projection interface for cost aggregation grouped by currency. */
public interface CostCurrencyProjection {

  String getCurrency();

  BigDecimal getCostValue();
}
