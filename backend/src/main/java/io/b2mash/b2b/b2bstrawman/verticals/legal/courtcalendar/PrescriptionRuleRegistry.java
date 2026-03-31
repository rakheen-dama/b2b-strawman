package io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar;

import java.time.LocalDate;

/**
 * Static registry of South African prescription periods. Not a Spring bean -- no injection, no
 * state.
 */
public final class PrescriptionRuleRegistry {

  private PrescriptionRuleRegistry() {}

  public static int getPeriodYears(String prescriptionType) {
    return switch (prescriptionType) {
      case "GENERAL_3Y", "DELICT_3Y", "CONTRACT_3Y" -> 3;
      case "DEBT_6Y" -> 6;
      case "MORTGAGE_30Y" -> 30;
      default ->
          throw new IllegalArgumentException("Unknown prescription type: " + prescriptionType);
    };
  }

  public static LocalDate calculatePrescriptionDate(
      LocalDate causeOfActionDate, String prescriptionType, Integer customYears) {
    if ("CUSTOM".equals(prescriptionType)) {
      if (customYears == null || customYears <= 0) {
        throw new IllegalArgumentException("CUSTOM type requires positive customYears");
      }
      return causeOfActionDate.plusYears(customYears);
    }
    return causeOfActionDate.plusYears(getPeriodYears(prescriptionType));
  }
}
