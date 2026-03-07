package io.b2mash.b2b.b2bstrawman.timeentry;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Spring Data projection interface for weekly actual/billable hours aggregation per member. */
public interface WeeklyActualHoursProjection {

  LocalDate getWeekStart();

  BigDecimal getActualHours();

  BigDecimal getBillableHours();
}
