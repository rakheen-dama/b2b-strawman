package io.b2mash.b2b.b2bstrawman.report;

import java.math.BigDecimal;

/** Projection interface for expense profitability aggregation grouped by currency. */
public interface ExpenseProfitabilityProjection {

  String getCurrency();

  BigDecimal getTotalExpenseCost();

  BigDecimal getTotalExpenseRevenue();
}
