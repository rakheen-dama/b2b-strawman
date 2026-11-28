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

  /**
   * The <b>last day to serve</b>: the anniversary of the cause-of-action date, {@code n} years on,
   * minus one day. Under SA's civilian method of computation the first day is counted and
   * prescription completes at midnight at the end of the day before the anniversary (Kleynhans v
   * Yorkshire Insurance 1957 (3) SA 544 (A); Afrisun KZN v Sewpersad [2017] ZAKZPHC 50; see
   * tasks/legal/prescription-computation-2026-11-28.md). The anniversary itself is the first day
   * the claim has already prescribed. Calendar years only: never extended for weekends or public
   * holidays.
   *
   * <p>29 February (for attorney confirmation): in a common year {@code plusYears} clamps the
   * anniversary to 28 February, so the last day is 27 February. That is the conservative reading:
   * the alternative (the period ends on 28 February) is never earlier, so a summons served by 27
   * February is in time under both.
   */
  public static LocalDate calculatePrescriptionDate(
      LocalDate causeOfActionDate, String prescriptionType, Integer customYears) {
    if ("CUSTOM".equals(prescriptionType)) {
      if (customYears == null || customYears <= 0) {
        throw new IllegalArgumentException("CUSTOM type requires positive customYears");
      }
      return lastDayToServe(causeOfActionDate, customYears);
    }
    return lastDayToServe(causeOfActionDate, getPeriodYears(prescriptionType));
  }

  private static LocalDate lastDayToServe(LocalDate causeOfActionDate, int years) {
    return causeOfActionDate.plusYears(years).minusDays(1);
  }
}
