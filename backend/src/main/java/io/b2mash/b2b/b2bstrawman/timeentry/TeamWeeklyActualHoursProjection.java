package io.b2mash.b2b.b2bstrawman.timeentry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Spring Data projection interface for weekly actual/billable hours aggregation across all team
 * members.
 */
public interface TeamWeeklyActualHoursProjection {

  UUID getMemberId();

  LocalDate getWeekStart();

  BigDecimal getActualHours();

  BigDecimal getBillableHours();
}
