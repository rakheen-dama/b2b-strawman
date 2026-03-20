package io.b2mash.b2b.b2bstrawman.deadline;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Static registry of regulatory deadline types by jurisdiction. Not a Spring bean — no injection,
 * no state. Deadline types are regulatory constants defined in code, not tenant-configurable data.
 * Adding new jurisdictions means adding a new static block.
 *
 * <p>See ADR-197 (calculated vs. stored deadlines).
 */
public final class DeadlineTypeRegistry {

  private DeadlineTypeRegistry() {}

  /**
   * Immutable definition of a regulatory deadline type.
   *
   * @param slug unique identifier (e.g. "sars_provisional_1")
   * @param name human-readable name
   * @param jurisdiction jurisdiction code (e.g. "ZA")
   * @param category grouping category (e.g. "tax", "vat", "corporate", "payroll")
   * @param calculationRule (fye, periodKey) -> dueDate
   * @param applicabilityRule customFields -> applicable?
   */
  public record DeadlineType(
      String slug,
      String name,
      String jurisdiction,
      String category,
      BiFunction<LocalDate, String, LocalDate> calculationRule,
      Predicate<Map<String, Object>> applicabilityRule) {}

  private static final List<DeadlineType> ALL_TYPES =
      List.of(
          // --- ZA: Tax deadlines ---
          new DeadlineType(
              "sars_provisional_1",
              "Provisional Tax \u2014 1st Payment",
              "ZA",
              "tax",
              (fye, periodKey) -> YearMonth.from(fye).plusMonths(6).atEndOfMonth(),
              customFields -> true),
          new DeadlineType(
              "sars_provisional_2",
              "Provisional Tax \u2014 2nd Payment",
              "ZA",
              "tax",
              (fye, periodKey) -> YearMonth.from(fye).atEndOfMonth(),
              customFields -> true),
          new DeadlineType(
              "sars_provisional_3",
              "Provisional Tax \u2014 Top-Up (Voluntary)",
              "ZA",
              "tax",
              (fye, periodKey) -> YearMonth.from(fye).plusMonths(7).atEndOfMonth(),
              customFields -> true),
          new DeadlineType(
              "sars_annual_return",
              "Income Tax Return (ITR14/IT12)",
              "ZA",
              "tax",
              (fye, periodKey) -> fye.plusMonths(12),
              customFields -> true),
          // --- ZA: VAT ---
          new DeadlineType(
              "sars_vat_return",
              "VAT Return (bi-monthly)",
              "ZA",
              "vat",
              (fye, periodKey) ->
                  LocalDate.parse(periodKey + "-01").plusMonths(1).withDayOfMonth(25),
              customFields -> {
                Object vatNumber = customFields.get("vat_number");
                return vatNumber != null && !vatNumber.toString().isBlank();
              }),
          // --- ZA: Corporate ---
          new DeadlineType(
              "cipc_annual_return",
              "CIPC Annual Return",
              "ZA",
              "corporate",
              (fye, periodKey) -> fye.plusMonths(12),
              customFields -> {
                Object cipc = customFields.get("cipc_registration_number");
                return cipc != null && !cipc.toString().isBlank();
              }),
          new DeadlineType(
              "afs_submission",
              "Annual Financial Statements",
              "ZA",
              "corporate",
              (fye, periodKey) -> fye.plusMonths(6),
              customFields -> true),
          // --- ZA: Payroll ---
          new DeadlineType(
              "sars_paye_monthly",
              "PAYE/UIF/SDL Monthly Return",
              "ZA",
              "payroll",
              (fye, periodKey) ->
                  LocalDate.parse(periodKey + "-01").plusMonths(1).withDayOfMonth(7),
              customFields -> true));

  /**
   * Returns all deadline types for the given jurisdiction. Returns empty list for unknown
   * jurisdictions.
   */
  public static List<DeadlineType> getDeadlineTypes(String jurisdiction) {
    if (jurisdiction == null) {
      return List.of();
    }
    return ALL_TYPES.stream().filter(t -> t.jurisdiction().equals(jurisdiction)).toList();
  }

  /**
   * Returns the deadline type for the given slug, searching across all jurisdictions. Returns empty
   * if not found.
   */
  public static Optional<DeadlineType> getDeadlineType(String slug) {
    if (slug == null) {
      return Optional.empty();
    }
    return ALL_TYPES.stream().filter(t -> t.slug().equals(slug)).findFirst();
  }

  /** Returns the distinct category values for the given jurisdiction. */
  public static List<String> getCategories(String jurisdiction) {
    return getDeadlineTypes(jurisdiction).stream().map(DeadlineType::category).distinct().toList();
  }
}
