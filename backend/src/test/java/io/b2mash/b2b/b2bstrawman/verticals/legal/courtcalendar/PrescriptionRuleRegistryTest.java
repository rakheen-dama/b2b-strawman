package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PrescriptionRuleRegistryTest {

  @Test
  void getPeriodYears_returnsCorrectPeriodsForKnownTypes() {
    assertThat(PrescriptionRuleRegistry.getPeriodYears("GENERAL_3Y")).isEqualTo(3);
    assertThat(PrescriptionRuleRegistry.getPeriodYears("DELICT_3Y")).isEqualTo(3);
    assertThat(PrescriptionRuleRegistry.getPeriodYears("CONTRACT_3Y")).isEqualTo(3);
    assertThat(PrescriptionRuleRegistry.getPeriodYears("DEBT_6Y")).isEqualTo(6);
    assertThat(PrescriptionRuleRegistry.getPeriodYears("MORTGAGE_30Y")).isEqualTo(30);
  }

  @Test
  void getPeriodYears_throwsForUnknownType() {
    assertThatThrownBy(() -> PrescriptionRuleRegistry.getPeriodYears("INVALID"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown prescription type");
  }

  // The stored date is the LAST DAY TO SERVE: the anniversary minus one day. Under the civilian
  // method (Kleynhans 1957 (3) SA 544 (A); Afrisun KZN v Sewpersad [2017] ZAKZPHC 50) prescription
  // completes at midnight at the end of the day before the anniversary
  // (tasks/legal/prescription-computation-2026-11-28.md).
  @Test
  void calculatePrescriptionDate_returnsLastDayToServe_theDayBeforeTheAnniversary() {
    var causeDate = LocalDate.of(2023, 6, 15);

    assertThat(PrescriptionRuleRegistry.calculatePrescriptionDate(causeDate, "GENERAL_3Y", null))
        .isEqualTo(LocalDate.of(2026, 6, 14));

    assertThat(PrescriptionRuleRegistry.calculatePrescriptionDate(causeDate, "DEBT_6Y", null))
        .isEqualTo(LocalDate.of(2029, 6, 14));

    assertThat(PrescriptionRuleRegistry.calculatePrescriptionDate(causeDate, "MORTGAGE_30Y", null))
        .isEqualTo(LocalDate.of(2053, 6, 14));

    assertThat(PrescriptionRuleRegistry.calculatePrescriptionDate(causeDate, "CUSTOM", 5))
        .isEqualTo(LocalDate.of(2028, 6, 14));
  }

  @Test
  void calculatePrescriptionDate_crossesMonthAndLeapDayBoundaries() {
    // 1 Jan: the last day is 31 Dec of the year before the anniversary.
    assertThat(
            PrescriptionRuleRegistry.calculatePrescriptionDate(
                LocalDate.of(2023, 1, 1), "GENERAL_3Y", null))
        .isEqualTo(LocalDate.of(2025, 12, 31));
    // 1 Mar into a leap year: the day before the anniversary is 29 Feb.
    assertThat(
            PrescriptionRuleRegistry.calculatePrescriptionDate(
                LocalDate.of(2025, 3, 1), "GENERAL_3Y", null))
        .isEqualTo(LocalDate.of(2028, 2, 29));
    // 1 Mar into a common year: 28 Feb.
    assertThat(
            PrescriptionRuleRegistry.calculatePrescriptionDate(
                LocalDate.of(2024, 3, 1), "GENERAL_3Y", null))
        .isEqualTo(LocalDate.of(2027, 2, 28));
  }

  /**
   * Cause of action on 29 February (FOR ATTORNEY CONFIRMATION, opinion checklist item 1). The
   * anniversary does not exist in a common year; {@code plusYears} clamps it to 28 Feb, so the last
   * day to serve is 27 Feb. One reading of the civilian method ends the period on 28 Feb instead;
   * 27 Feb is the conservative choice (it is never later than either reading). Into a leap year the
   * anniversary is 29 Feb itself, so the last day is 28 Feb.
   */
  @Test
  void calculatePrescriptionDate_causeOn29Feb_isConservativeInACommonYear() {
    var leapDay = LocalDate.of(2024, 2, 29);

    assertThat(PrescriptionRuleRegistry.calculatePrescriptionDate(leapDay, "GENERAL_3Y", null))
        .isEqualTo(LocalDate.of(2027, 2, 27));
    assertThat(PrescriptionRuleRegistry.calculatePrescriptionDate(leapDay, "CUSTOM", 4))
        .isEqualTo(LocalDate.of(2028, 2, 28));
  }

  @Test
  void calculatePrescriptionDate_customType_throwsWithoutValidCustomYears() {
    var causeDate = LocalDate.of(2023, 1, 1);

    assertThatThrownBy(
            () -> PrescriptionRuleRegistry.calculatePrescriptionDate(causeDate, "CUSTOM", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CUSTOM type requires positive customYears");

    assertThatThrownBy(
            () -> PrescriptionRuleRegistry.calculatePrescriptionDate(causeDate, "CUSTOM", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CUSTOM type requires positive customYears");

    assertThatThrownBy(
            () -> PrescriptionRuleRegistry.calculatePrescriptionDate(causeDate, "CUSTOM", -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CUSTOM type requires positive customYears");
  }
}
