package io.b2mash.b2b.b2bstrawman.deadline;

import io.b2mash.b2b.b2bstrawman.customer.Customer;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.deadline.DeadlineTypeRegistry.DeadlineType;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calculates regulatory deadlines on-the-fly from customer financial year-end dates and the {@link
 * DeadlineTypeRegistry}. Overlays persisted {@link FilingStatus} records and cross-references
 * linked projects.
 *
 * <p>See ADR-197 (calculated vs. stored deadlines), ADR-199 (filing status overlay).
 */
@Service
public class DeadlineCalculationService {

  /**
   * A single calculated deadline for a customer.
   *
   * @param customerId the customer this deadline belongs to
   * @param customerName display name
   * @param deadlineTypeSlug registry slug (e.g. "sars_provisional_1")
   * @param deadlineTypeName human-readable name
   * @param category grouping category (tax, vat, corporate, payroll)
   * @param dueDate computed due date
   * @param status "pending", "filed", "overdue", or "not_applicable"
   * @param linkedProjectId nullable project ID if a matching project was found
   * @param filingStatusId nullable filing status ID if a persisted record exists
   */
  public record CalculatedDeadline(
      UUID customerId,
      String customerName,
      String deadlineTypeSlug,
      String deadlineTypeName,
      String category,
      LocalDate dueDate,
      String status,
      UUID linkedProjectId,
      UUID filingStatusId) {}

  /**
   * Optional filters for deadline calculation.
   *
   * @param category filter by category (nullable)
   * @param status filter by computed status (nullable)
   * @param customerId restrict to a single customer (nullable)
   */
  public record DeadlineFilters(String category, String status, UUID customerId) {}

  /**
   * Aggregated summary row grouped by month and category.
   *
   * @param month year-month string (e.g. "2026-01")
   * @param category deadline category
   * @param total total deadlines in the group
   * @param filed number with "filed" status
   * @param pending number with "pending" status
   * @param overdue number with "overdue" status
   */
  public record DeadlineSummary(
      String month, String category, int total, int filed, int pending, int overdue) {}

  private static final Set<String> MONTHLY_SLUGS = Set.of("sars_vat_return", "sars_paye_monthly");
  private static final Set<Integer> VAT_BIMONTHLY_MONTHS = Set.of(1, 3, 5, 7, 9, 11);

  private final CustomerRepository customerRepository;
  private final FilingStatusRepository filingStatusRepository;
  private final ProjectRepository projectRepository;

  public DeadlineCalculationService(
      CustomerRepository customerRepository,
      FilingStatusRepository filingStatusRepository,
      ProjectRepository projectRepository) {
    this.customerRepository = customerRepository;
    this.filingStatusRepository = filingStatusRepository;
    this.projectRepository = projectRepository;
  }

  /**
   * Calculates all deadlines within the given date range, applying optional filters. Deadlines are
   * computed on-the-fly from customer FYE + deadline type rules, then overlaid with persisted
   * filing statuses and cross-referenced with projects.
   */
  @Transactional(readOnly = true)
  public List<CalculatedDeadline> calculateDeadlines(
      LocalDate from, LocalDate to, DeadlineFilters filters) {
    // 1. Load customers
    List<Customer> customers = loadCustomers(filters);

    // 2. Get all ZA deadline types (filtered by category if specified)
    List<DeadlineType> deadlineTypes = DeadlineTypeRegistry.getDeadlineTypes("ZA");
    if (filters != null && filters.category() != null) {
      deadlineTypes =
          deadlineTypes.stream().filter(dt -> dt.category().equals(filters.category())).toList();
    }

    // 3. Generate raw deadlines (before status overlay)
    List<RawDeadline> rawDeadlines = new ArrayList<>();
    for (Customer customer : customers) {
      Map<String, Object> customFields = customer.getCustomFields();
      Object fyeValue = customFields.get("financial_year_end");
      if (fyeValue == null) {
        continue;
      }
      LocalDate fye = LocalDate.parse(fyeValue.toString());

      for (DeadlineType dt : deadlineTypes) {
        if (!dt.applicabilityRule().test(customFields)) {
          continue;
        }
        generatePeriodsForType(dt, fye, from, to)
            .forEach(
                raw ->
                    rawDeadlines.add(
                        new RawDeadline(
                            customer.getId(),
                            customer.getName(),
                            dt.slug(),
                            dt.name(),
                            dt.category(),
                            raw.dueDate(),
                            raw.periodKey())));
      }
    }

    if (rawDeadlines.isEmpty()) {
      return List.of();
    }

    // 4. Batch-load filing statuses and build lookup map
    Set<UUID> customerIds =
        rawDeadlines.stream().map(RawDeadline::customerId).collect(Collectors.toSet());
    Set<String> slugs =
        rawDeadlines.stream().map(RawDeadline::deadlineTypeSlug).collect(Collectors.toSet());
    Set<String> periodKeys =
        rawDeadlines.stream().map(RawDeadline::periodKey).collect(Collectors.toSet());

    List<FilingStatus> filingStatuses =
        filingStatusRepository.findByCustomerIdInAndDeadlineTypeSlugInAndPeriodKeyIn(
            customerIds, slugs, periodKeys);

    Map<String, FilingStatus> filingStatusMap = new HashMap<>();
    for (FilingStatus fs : filingStatuses) {
      String key =
          fs.getCustomerId().toString() + "|" + fs.getDeadlineTypeSlug() + "|" + fs.getPeriodKey();
      filingStatusMap.put(key, fs);
    }

    // 5. Load projects for cross-referencing (grouped by customer)
    Map<UUID, List<Project>> projectsByCustomer = new HashMap<>();
    for (UUID cid : customerIds) {
      projectsByCustomer.put(cid, projectRepository.findByCustomerId(cid));
    }

    // 6. Build final deadlines with status overlay and project cross-reference
    LocalDate today = LocalDate.now();
    List<CalculatedDeadline> result = new ArrayList<>();

    for (RawDeadline raw : rawDeadlines) {
      String compositeKey =
          raw.customerId().toString() + "|" + raw.deadlineTypeSlug() + "|" + raw.periodKey();
      FilingStatus filingStatus = filingStatusMap.get(compositeKey);

      String status;
      UUID filingStatusId = null;

      if (filingStatus != null) {
        status = filingStatus.getStatus();
        filingStatusId = filingStatus.getId();
      } else if ("sars_provisional_3".equals(raw.deadlineTypeSlug())) {
        status = "pending"; // voluntary -- never overdue
      } else if (raw.dueDate().isBefore(today)) {
        status = "overdue";
      } else {
        status = "pending";
      }

      // Project cross-reference
      UUID linkedProjectId = findLinkedProject(raw, projectsByCustomer);

      result.add(
          new CalculatedDeadline(
              raw.customerId(),
              raw.customerName(),
              raw.deadlineTypeSlug(),
              raw.deadlineTypeName(),
              raw.category(),
              raw.dueDate(),
              status,
              linkedProjectId,
              filingStatusId));
    }

    // 7. Apply status filter if provided
    if (filters != null && filters.status() != null) {
      result = result.stream().filter(d -> d.status().equals(filters.status())).toList();
    }

    // 8. Sort by due date ascending
    return result.stream().sorted((a, b) -> a.dueDate().compareTo(b.dueDate())).toList();
  }

  /** Convenience method: calculates deadlines for a single customer. */
  @Transactional(readOnly = true)
  public List<CalculatedDeadline> calculateDeadlinesForCustomer(
      UUID customerId, LocalDate from, LocalDate to) {
    return calculateDeadlines(from, to, new DeadlineFilters(null, null, customerId));
  }

  /**
   * Calculates a summary of deadlines grouped by month and category, with counts of each status.
   */
  @Transactional(readOnly = true)
  public List<DeadlineSummary> calculateSummary(
      LocalDate from, LocalDate to, DeadlineFilters filters) {
    var deadlines = calculateDeadlines(from, to, filters);

    // Group by (month, category)
    record GroupKey(String month, String category) {}

    Map<GroupKey, List<CalculatedDeadline>> grouped =
        deadlines.stream()
            .collect(
                Collectors.groupingBy(
                    d -> new GroupKey(YearMonth.from(d.dueDate()).toString(), d.category())));

    return grouped.entrySet().stream()
        .map(
            entry -> {
              var key = entry.getKey();
              var group = entry.getValue();
              int total = group.size();
              int filed = (int) group.stream().filter(d -> "filed".equals(d.status())).count();
              int pending = (int) group.stream().filter(d -> "pending".equals(d.status())).count();
              int overdue = (int) group.stream().filter(d -> "overdue".equals(d.status())).count();
              return new DeadlineSummary(
                  key.month(), key.category(), total, filed, pending, overdue);
            })
        .sorted(
            (a, b) -> {
              int cmp = a.month().compareTo(b.month());
              return cmp != 0 ? cmp : a.category().compareTo(b.category());
            })
        .toList();
  }

  // --- Private helpers ---

  private List<Customer> loadCustomers(DeadlineFilters filters) {
    if (filters != null && filters.customerId() != null) {
      return customerRepository
          .findById(filters.customerId())
          .filter(c -> c.getLifecycleStatus() == LifecycleStatus.ACTIVE)
          .map(List::of)
          .orElse(List.of());
    }
    return customerRepository.findByLifecycleStatus(LifecycleStatus.ACTIVE);
  }

  /**
   * Generates period entries (due date + period key) for a deadline type within the given date
   * range. Annual types iterate years; monthly types iterate months.
   */
  private List<PeriodEntry> generatePeriodsForType(
      DeadlineType dt, LocalDate fye, LocalDate from, LocalDate to) {
    if (MONTHLY_SLUGS.contains(dt.slug())) {
      return generateMonthlyPeriods(dt, fye, from, to);
    }
    return generateAnnualPeriods(dt, fye, from, to);
  }

  private List<PeriodEntry> generateAnnualPeriods(
      DeadlineType dt, LocalDate fye, LocalDate from, LocalDate to) {
    List<PeriodEntry> entries = new ArrayList<>();
    // Iterate from (from.year - 1) to (to.year + 1) to catch edge cases
    for (int year = from.getYear() - 1; year <= to.getYear() + 1; year++) {
      LocalDate adjustedFye = adjustFyeToYear(fye, year);
      String periodKey = String.valueOf(year);
      LocalDate dueDate = dt.calculationRule().apply(adjustedFye, periodKey);
      if (!dueDate.isBefore(from) && !dueDate.isAfter(to)) {
        entries.add(new PeriodEntry(dueDate, periodKey));
      }
    }
    return entries;
  }

  private List<PeriodEntry> generateMonthlyPeriods(
      DeadlineType dt, LocalDate fye, LocalDate from, LocalDate to) {
    List<PeriodEntry> entries = new ArrayList<>();
    boolean isBimonthly = "sars_vat_return".equals(dt.slug());

    // Start from the month before 'from' to catch deadlines whose due date falls in range
    YearMonth startMonth = YearMonth.from(from).minusMonths(1);
    YearMonth endMonth = YearMonth.from(to).plusMonths(1);

    for (YearMonth ym = startMonth; !ym.isAfter(endMonth); ym = ym.plusMonths(1)) {
      if (isBimonthly && !VAT_BIMONTHLY_MONTHS.contains(ym.getMonthValue())) {
        continue;
      }
      String periodKey = ym.toString();
      LocalDate dueDate = dt.calculationRule().apply(fye, periodKey);
      if (!dueDate.isBefore(from) && !dueDate.isAfter(to)) {
        entries.add(new PeriodEntry(dueDate, periodKey));
      }
    }
    return entries;
  }

  /**
   * Adjusts a FYE date to a different year, handling Feb 28/29 edge cases. If the original FYE is
   * Feb 28 and the target year is a leap year, we still use Feb 28 (not Feb 29) since the FYE is a
   * fixed date.
   */
  private static LocalDate adjustFyeToYear(LocalDate fye, int year) {
    int day = fye.getDayOfMonth();
    int maxDay = YearMonth.of(year, fye.getMonthValue()).lengthOfMonth();
    return LocalDate.of(year, fye.getMonthValue(), Math.min(day, maxDay));
  }

  /** Best-effort project cross-referencing based on custom fields. */
  private UUID findLinkedProject(RawDeadline raw, Map<UUID, List<Project>> projectsByCustomer) {
    List<Project> projects = projectsByCustomer.get(raw.customerId());
    if (projects == null || projects.isEmpty()) {
      return null;
    }
    for (Project project : projects) {
      Map<String, Object> fields = project.getCustomFields();
      if (fields == null || fields.isEmpty()) {
        continue;
      }
      Object engagementType = fields.get("engagement_type");
      Object taxYear = fields.get("tax_year");
      if (engagementType != null
          && taxYear != null
          && engagementType.toString().equals(raw.category())
          && taxYear.toString().equals(raw.periodKey())) {
        return project.getId();
      }
    }
    return null;
  }

  /** Internal record for a raw deadline before status overlay. */
  private record RawDeadline(
      UUID customerId,
      String customerName,
      String deadlineTypeSlug,
      String deadlineTypeName,
      String category,
      LocalDate dueDate,
      String periodKey) {}

  /** Internal record for a generated period entry. */
  private record PeriodEntry(LocalDate dueDate, String periodKey) {}
}
