package io.b2mash.b2b.b2bstrawman.projecttemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.b2mash.b2b.b2bstrawman.schedule.RecurringSchedule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PeriodCalculatorTest {

  private final PeriodCalculator calculator = new PeriodCalculator();

  // ---- calculateNextPeriod: all 6 frequencies at count=0 ----

  @Test
  void weeklyPeriodCount0() {
    LocalDate anchor = LocalDate.of(2026, 3, 15);
    var period = calculator.calculateNextPeriod(anchor, "WEEKLY", 0);
    assertThat(period.start()).isEqualTo(LocalDate.of(2026, 3, 15));
    assertThat(period.end()).isEqualTo(LocalDate.of(2026, 3, 21));
  }

  @Test
  void weeklyPeriodCount1() {
    LocalDate anchor = LocalDate.of(2026, 3, 15);
    var period = calculator.calculateNextPeriod(anchor, "WEEKLY", 1);
    assertThat(period.start()).isEqualTo(LocalDate.of(2026, 3, 22));
    assertThat(period.end()).isEqualTo(LocalDate.of(2026, 3, 28));
  }

  @Test
  void fortnightlyPeriodCount0() {
    LocalDate anchor = LocalDate.of(2026, 1, 1);
    var period = calculator.calculateNextPeriod(anchor, "FORTNIGHTLY", 0);
    assertThat(period.start()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(period.end()).isEqualTo(LocalDate.of(2026, 1, 14));
  }

  @Test
  void monthlyPeriodCount0() {
    LocalDate anchor = LocalDate.of(2026, 1, 1);
    var period = calculator.calculateNextPeriod(anchor, "MONTHLY", 0);
    assertThat(period.start()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(period.end()).isEqualTo(LocalDate.of(2026, 1, 31));
  }

  @Test
  void monthlyPeriodCount1() {
    LocalDate anchor = LocalDate.of(2026, 1, 1);
    var period = calculator.calculateNextPeriod(anchor, "MONTHLY", 1);
    assertThat(period.start()).isEqualTo(LocalDate.of(2026, 2, 1));
    assertThat(period.end()).isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  void quarterlyPeriodCount0() {
    LocalDate anchor = LocalDate.of(2026, 1, 1);
    var period = calculator.calculateNextPeriod(anchor, "QUARTERLY", 0);
    assertThat(period.start()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(period.end()).isEqualTo(LocalDate.of(2026, 3, 31));
  }

  @Test
  void quarterlyPeriodCount1() {
    LocalDate anchor = LocalDate.of(2026, 1, 1);
    var period = calculator.calculateNextPeriod(anchor, "QUARTERLY", 1);
    assertThat(period.start()).isEqualTo(LocalDate.of(2026, 4, 1));
    assertThat(period.end()).isEqualTo(LocalDate.of(2026, 6, 30));
  }

  @Test
  void semiAnnuallyPeriodCount0() {
    LocalDate anchor = LocalDate.of(2026, 1, 1);
    var period = calculator.calculateNextPeriod(anchor, "SEMI_ANNUALLY", 0);
    assertThat(period.start()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(period.end()).isEqualTo(LocalDate.of(2026, 6, 30));
  }

  @Test
  void annuallyPeriodCount0() {
    LocalDate anchor = LocalDate.of(2026, 1, 1);
    var period = calculator.calculateNextPeriod(anchor, "ANNUALLY", 0);
    assertThat(period.start()).isEqualTo(LocalDate.of(2026, 1, 1));
    assertThat(period.end()).isEqualTo(LocalDate.of(2026, 12, 31));
  }

  // ---- Edge cases ----

  @Test
  void monthEndHandling_jan31PlusOneMonth() {
    // Java's plusMonths() naturally handles month-end: Jan 31 + 1 month = Feb 28 (2026, non-leap)
    LocalDate anchor = LocalDate.of(2026, 1, 31);
    var period = calculator.calculateNextPeriod(anchor, "MONTHLY", 1);
    assertThat(period.start()).isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  void leapYear_jan31PlusOneMonth() {
    // 2024 is a leap year: Feb has 29 days
    LocalDate anchor = LocalDate.of(2024, 1, 31);
    var period = calculator.calculateNextPeriod(anchor, "MONTHLY", 1);
    assertThat(period.start()).isEqualTo(LocalDate.of(2024, 2, 29));
  }

  @Test
  void yearBoundary_decemberToJanuary() {
    LocalDate anchor = LocalDate.of(2026, 12, 1);
    var period = calculator.calculateNextPeriod(anchor, "MONTHLY", 1);
    assertThat(period.start()).isEqualTo(LocalDate.of(2027, 1, 1));
    assertThat(period.end()).isEqualTo(LocalDate.of(2027, 1, 31));
  }

  @Test
  void unknownFrequencyThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> calculator.calculateNextPeriod(LocalDate.now(), "DAILY", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("DAILY");
  }

  // ---- calculateNextExecutionDate ----

  @Test
  void calculateNextExecutionDate_zeroLeadTime() {
    LocalDate periodStart = LocalDate.of(2026, 3, 1);
    assertThat(calculator.calculateNextExecutionDate(periodStart, 0))
        .isEqualTo(LocalDate.of(2026, 3, 1));
  }

  @Test
  void calculateNextExecutionDate_fiveDayLead() {
    LocalDate periodStart = LocalDate.of(2026, 3, 1);
    assertThat(calculator.calculateNextExecutionDate(periodStart, 5))
        .isEqualTo(LocalDate.of(2026, 2, 24));
  }

  // ---- recalculateNextExecutionOnResume ----

  private RecurringSchedule makeSchedule(
      LocalDate startDate, String frequency, int executionCount, int leadTimeDays) {
    var schedule =
        new RecurringSchedule(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            frequency,
            startDate,
            null,
            leadTimeDays,
            null,
            UUID.randomUUID());
    // simulate execution count by calling recordExecution() N times
    for (int i = 0; i < executionCount; i++) {
      schedule.recordExecution(Instant.now());
    }
    return schedule;
  }

  @Test
  void resumeWithNoMissedPeriods_setsFutureDate() {
    // Start date far in the future — no periods missed
    LocalDate futureStart = LocalDate.now().plusMonths(2);
    var schedule = makeSchedule(futureStart, "MONTHLY", 0, 0);
    calculator.recalculateNextExecutionOnResume(schedule);
    // First period starts at futureStart, lead = 0, so execution date = futureStart
    assertThat(schedule.getNextExecutionDate()).isEqualTo(futureStart);
  }

  @Test
  void resumeWithMissedPeriods_skipsForward() {
    // Start date 6 months in the past, monthly, 0 executions
    // Should skip past periods to the next future period
    LocalDate pastStart = LocalDate.now().minusMonths(6);
    var schedule = makeSchedule(pastStart, "MONTHLY", 0, 0);
    calculator.recalculateNextExecutionOnResume(schedule);
    // Next execution date should be in the future (or today)
    assertThat(schedule.getNextExecutionDate()).isAfterOrEqualTo(LocalDate.now());
  }

  @Test
  void resumeWithLeadTime_appliesLeadTime() {
    // Start date far in the future with 5-day lead
    LocalDate futureStart = LocalDate.now().plusMonths(2);
    var schedule = makeSchedule(futureStart, "MONTHLY", 0, 5);
    calculator.recalculateNextExecutionOnResume(schedule);
    // Execution date = futureStart - 5 days
    assertThat(schedule.getNextExecutionDate()).isEqualTo(futureStart.minusDays(5));
  }
}
