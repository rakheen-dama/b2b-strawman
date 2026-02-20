package io.b2mash.b2b.b2bstrawman.retainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RetainerFrequencyTest {

  @Test
  void weekly_addsOneWeek() {
    LocalDate start = LocalDate.of(2026, 3, 1);
    assertThat(RetainerFrequency.WEEKLY.calculateNextEnd(start))
        .isEqualTo(LocalDate.of(2026, 3, 8));
  }

  @Test
  void fortnightly_addsTwoWeeks() {
    LocalDate start = LocalDate.of(2026, 3, 1);
    assertThat(RetainerFrequency.FORTNIGHTLY.calculateNextEnd(start))
        .isEqualTo(LocalDate.of(2026, 3, 15));
  }

  @Test
  void monthly_addsOneMonth() {
    LocalDate start = LocalDate.of(2026, 3, 1);
    assertThat(RetainerFrequency.MONTHLY.calculateNextEnd(start))
        .isEqualTo(LocalDate.of(2026, 4, 1));
  }

  @Test
  void quarterly_addsThreeMonths() {
    LocalDate start = LocalDate.of(2026, 1, 1);
    assertThat(RetainerFrequency.QUARTERLY.calculateNextEnd(start))
        .isEqualTo(LocalDate.of(2026, 4, 1));
  }

  @Test
  void semiAnnually_addsSixMonths() {
    LocalDate start = LocalDate.of(2026, 1, 1);
    assertThat(RetainerFrequency.SEMI_ANNUALLY.calculateNextEnd(start))
        .isEqualTo(LocalDate.of(2026, 7, 1));
  }

  @Test
  void annually_addsOneYear() {
    LocalDate start = LocalDate.of(2026, 1, 1);
    assertThat(RetainerFrequency.ANNUALLY.calculateNextEnd(start))
        .isEqualTo(LocalDate.of(2027, 1, 1));
  }

  @Test
  void monthly_edgeCase_jan31_becomes_feb28() {
    // Java LocalDate.plusMonths clamps to last valid day of month
    LocalDate start = LocalDate.of(2026, 1, 31);
    assertThat(RetainerFrequency.MONTHLY.calculateNextEnd(start))
        .isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  void annually_leapYear_feb29_stays_feb28() {
    // 2024 is a leap year; 2025 is not — plusYears clamps
    LocalDate start = LocalDate.of(2024, 2, 29);
    assertThat(RetainerFrequency.ANNUALLY.calculateNextEnd(start))
        .isEqualTo(LocalDate.of(2025, 2, 28));
  }
}
