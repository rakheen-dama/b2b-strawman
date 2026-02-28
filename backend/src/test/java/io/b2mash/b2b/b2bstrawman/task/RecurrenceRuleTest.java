package io.b2mash.b2b.b2bstrawman.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RecurrenceRuleTest {

  @Test
  void parse_monthly_interval1() {
    var rule = RecurrenceRule.parse("FREQ=MONTHLY;INTERVAL=1");
    assertThat(rule.frequency()).isEqualTo("MONTHLY");
    assertThat(rule.interval()).isEqualTo(1);
  }

  @Test
  void parse_weekly_interval2() {
    var rule = RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=2");
    assertThat(rule.frequency()).isEqualTo("WEEKLY");
    assertThat(rule.interval()).isEqualTo(2);
  }

  @Test
  void parse_daily_noInterval_defaultsTo1() {
    var rule = RecurrenceRule.parse("FREQ=DAILY");
    assertThat(rule.frequency()).isEqualTo("DAILY");
    assertThat(rule.interval()).isEqualTo(1);
  }

  @Test
  void parse_yearly_interval1() {
    var rule = RecurrenceRule.parse("FREQ=YEARLY;INTERVAL=1");
    assertThat(rule.frequency()).isEqualTo("YEARLY");
    assertThat(rule.interval()).isEqualTo(1);
  }

  @Test
  void parse_invalidFrequency_throws() {
    assertThatThrownBy(() -> RecurrenceRule.parse("FREQ=HOURLY"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported frequency");
  }

  @Test
  void parse_freqOnly_defaultsIntervalTo1() {
    var rule = RecurrenceRule.parse("FREQ=MONTHLY");
    assertThat(rule.interval()).isEqualTo(1);
  }

  @Test
  void nextDueDate_daily_addsDays() {
    var rule = RecurrenceRule.parse("FREQ=DAILY;INTERVAL=3");
    var next = rule.calculateNextDueDate(LocalDate.of(2026, 1, 10));
    assertThat(next).isEqualTo(LocalDate.of(2026, 1, 13));
  }

  @Test
  void nextDueDate_weekly_addsWeeks() {
    var rule = RecurrenceRule.parse("FREQ=WEEKLY;INTERVAL=2");
    var next = rule.calculateNextDueDate(LocalDate.of(2026, 3, 1));
    assertThat(next).isEqualTo(LocalDate.of(2026, 3, 15));
  }

  @Test
  void nextDueDate_monthly_sameDay() {
    var rule = RecurrenceRule.parse("FREQ=MONTHLY;INTERVAL=1");
    var next = rule.calculateNextDueDate(LocalDate.of(2026, 1, 15));
    assertThat(next).isEqualTo(LocalDate.of(2026, 2, 15));
  }

  @Test
  void nextDueDate_monthly_clampsDayOfMonth_jan31_to_feb28() {
    var rule = RecurrenceRule.parse("FREQ=MONTHLY;INTERVAL=1");
    var next = rule.calculateNextDueDate(LocalDate.of(2026, 1, 31));
    assertThat(next).isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  void nextDueDate_yearly_handlesFeb29() {
    var rule = RecurrenceRule.parse("FREQ=YEARLY;INTERVAL=1");
    // 2024 is a leap year, 2025 is not
    var next = rule.calculateNextDueDate(LocalDate.of(2024, 2, 29));
    assertThat(next).isEqualTo(LocalDate.of(2025, 2, 28));
  }

  @Test
  void nextDueDate_nullBaseDate_usesToday() {
    var rule = RecurrenceRule.parse("FREQ=DAILY;INTERVAL=1");
    var next = rule.calculateNextDueDate(null);
    assertThat(next).isEqualTo(LocalDate.now().plusDays(1));
  }
}
