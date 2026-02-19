package io.b2mash.b2b.b2bstrawman.projecttemplate;

import io.b2mash.b2b.b2bstrawman.schedule.RecurringSchedule;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class PeriodCalculator {

  public record Period(LocalDate start, LocalDate end) {}

  public Period calculateNextPeriod(LocalDate anchor, String frequency, int executionCount) {
    LocalDate periodStart =
        switch (frequency) {
          case "WEEKLY" -> anchor.plusWeeks(executionCount);
          case "FORTNIGHTLY" -> anchor.plusWeeks(executionCount * 2L);
          case "MONTHLY" -> anchor.plusMonths(executionCount);
          case "QUARTERLY" -> anchor.plusMonths(executionCount * 3L);
          case "SEMI_ANNUALLY" -> anchor.plusMonths(executionCount * 6L);
          case "ANNUALLY" -> anchor.plusYears(executionCount);
          default -> throw new IllegalArgumentException("Unknown frequency: " + frequency);
        };
    LocalDate periodEnd =
        switch (frequency) {
          case "WEEKLY" -> periodStart.plusWeeks(1).minusDays(1);
          case "FORTNIGHTLY" -> periodStart.plusWeeks(2).minusDays(1);
          case "MONTHLY" -> periodStart.plusMonths(1).minusDays(1);
          case "QUARTERLY" -> periodStart.plusMonths(3).minusDays(1);
          case "SEMI_ANNUALLY" -> periodStart.plusMonths(6).minusDays(1);
          case "ANNUALLY" -> periodStart.plusYears(1).minusDays(1);
          default -> throw new IllegalArgumentException("Unknown frequency: " + frequency);
        };
    return new Period(periodStart, periodEnd);
  }

  public LocalDate calculateNextExecutionDate(LocalDate periodStart, int leadTimeDays) {
    return periodStart.minusDays(leadTimeDays);
  }

  /**
   * Recalculates the next execution date when a paused schedule is resumed. Skips past periods so
   * that only future periods are scheduled. Mutates schedule.nextExecutionDate directly.
   *
   * <p>Safety valve: stops after 1000 periods beyond current execution count to prevent infinite
   * loops on misconfigured schedules.
   */
  public void recalculateNextExecutionOnResume(RecurringSchedule schedule) {
    LocalDate today = LocalDate.now();
    int count = schedule.getExecutionCount();
    while (true) {
      Period period = calculateNextPeriod(schedule.getStartDate(), schedule.getFrequency(), count);
      LocalDate executionDate = period.start().minusDays(schedule.getLeadTimeDays());
      if (executionDate.isAfter(today) || executionDate.isEqual(today)) {
        schedule.setNextExecutionDate(executionDate);
        break;
      }
      count++;
      // Safety valve
      if (count > schedule.getExecutionCount() + 1000) break;
    }
  }
}
