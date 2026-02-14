package io.b2mash.b2b.b2bstrawman.report;

import java.math.BigDecimal;

/** Spring Data projection interface for revenue aggregation grouped by currency. */
public interface RevenueCurrencyProjection {

  String getCurrency();

  BigDecimal getTotalBillableHours();

  BigDecimal getTotalNonBillableHours();

  BigDecimal getTotalHours();

  BigDecimal getBillableValue();
}
