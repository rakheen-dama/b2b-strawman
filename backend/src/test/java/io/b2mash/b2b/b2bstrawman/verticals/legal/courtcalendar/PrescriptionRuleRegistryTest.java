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

  @Test
  void calculatePrescriptionDate_addsCorrectYears() {
    var causeDate = LocalDate.of(2023, 6, 15);

    assertThat(PrescriptionRuleRegistry.calculatePrescriptionDate(causeDate, "GENERAL_3Y", null))
        .isEqualTo(LocalDate.of(2026, 6, 15));

    assertThat(PrescriptionRuleRegistry.calculatePrescriptionDate(causeDate, "DEBT_6Y", null))
        .isEqualTo(LocalDate.of(2029, 6, 15));

    assertThat(PrescriptionRuleRegistry.calculatePrescriptionDate(causeDate, "MORTGAGE_30Y", null))
        .isEqualTo(LocalDate.of(2053, 6, 15));

    assertThat(PrescriptionRuleRegistry.calculatePrescriptionDate(causeDate, "CUSTOM", 5))
        .isEqualTo(LocalDate.of(2028, 6, 15));
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
