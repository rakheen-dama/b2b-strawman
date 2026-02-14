package io.b2mash.b2b.b2bstrawman.timeentry;

import java.math.BigDecimal;

/** Spring Data projection interface for budget amount consumption aggregation. */
public interface BudgetAmountProjection {

  BigDecimal getAmountConsumed();
}
