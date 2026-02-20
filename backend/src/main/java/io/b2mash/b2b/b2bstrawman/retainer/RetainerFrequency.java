package io.b2mash.b2b.b2bstrawman.retainer;

import java.time.LocalDate;

public enum RetainerFrequency {
  WEEKLY,
  FORTNIGHTLY,
  MONTHLY,
  QUARTERLY,
  SEMI_ANNUALLY,
  ANNUALLY;

  /**
   * Calculates the exclusive end date for a period starting on the given date. The end date is
   * exclusive — a monthly period starting 2026-03-01 ends at 2026-04-01. Time entries on 2026-03-31
   * count; time entries on 2026-04-01 do not.
   *
   * @param start the period start date (inclusive)
   * @return the period end date (exclusive)
   */
  public LocalDate calculateNextEnd(LocalDate start) {
    return switch (this) {
      case WEEKLY -> start.plusWeeks(1);
      case FORTNIGHTLY -> start.plusWeeks(2);
      case MONTHLY -> start.plusMonths(1);
      case QUARTERLY -> start.plusMonths(3);
      case SEMI_ANNUALLY -> start.plusMonths(6);
      case ANNUALLY -> start.plusYears(1);
    };
  }
}
