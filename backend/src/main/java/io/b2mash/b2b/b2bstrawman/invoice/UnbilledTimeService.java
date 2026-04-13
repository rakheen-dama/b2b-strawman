package io.b2mash.b2b.b2bstrawman.invoice;

import io.b2mash.b2b.b2bstrawman.billingrate.BillingRateService;
import io.b2mash.b2b.b2bstrawman.billingrun.dto.BillingRunDtos;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.expense.ExpenseRepository;
import io.b2mash.b2b.b2bstrawman.fielddefinition.EntityType;
import io.b2mash.b2b.b2bstrawman.invoice.dto.CurrencyTotal;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UnbilledExpenseEntry;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UnbilledProjectGroup;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UnbilledTimeEntry;
import io.b2mash.b2b.b2bstrawman.invoice.dto.UnbilledTimeResponse;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteContext;
import io.b2mash.b2b.b2bstrawman.prerequisite.PrerequisiteService;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles unbilled time and expense aggregation queries. Extracted from InvoiceService as a focused
 * collaborator.
 */
@Service
public class UnbilledTimeService {

  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;
  private final EntityManager entityManager;
  private final OrgSettingsRepository orgSettingsRepository;
  private final ExpenseRepository expenseRepository;
  private final PrerequisiteService prerequisiteService;
  private final BillingRateService billingRateService;

  public UnbilledTimeService(
      CustomerRepository customerRepository,
      ProjectRepository projectRepository,
      EntityManager entityManager,
      OrgSettingsRepository orgSettingsRepository,
      ExpenseRepository expenseRepository,
      PrerequisiteService prerequisiteService,
      BillingRateService billingRateService) {
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
    this.entityManager = entityManager;
    this.orgSettingsRepository = orgSettingsRepository;
    this.expenseRepository = expenseRepository;
    this.prerequisiteService = prerequisiteService;
    this.billingRateService = billingRateService;
  }

  @Transactional(readOnly = true)
  public List<BillingRunDtos.CustomerUnbilledSummary> getUnbilledSummary(
      LocalDate periodFrom, LocalDate periodTo, String currency) {

    String sql =
        """
        SELECT
            c.id AS customer_id,
            c.name AS customer_name,
            c.email AS customer_email,
            COALESCE(time_agg.unbilled_time_count, 0) AS unbilled_time_count,
            COALESCE(time_agg.unbilled_time_amount, 0) AS unbilled_time_amount,
            COALESCE(exp_agg.unbilled_expense_count, 0) AS unbilled_expense_count,
            COALESCE(exp_agg.unbilled_expense_amount, 0) AS unbilled_expense_amount
        FROM customers c
        LEFT JOIN LATERAL (
            SELECT
                COUNT(*) AS unbilled_time_count,
                SUM((te.duration_minutes / 60.0) * te.billing_rate_snapshot) AS unbilled_time_amount
            FROM time_entries te
            JOIN tasks t ON te.task_id = t.id
            JOIN projects p ON t.project_id = p.id
            JOIN customer_projects cp ON cp.project_id = p.id AND cp.customer_id = c.id
            WHERE te.billable = true
              AND te.invoice_id IS NULL
              AND te.billing_rate_currency = :currency
              AND te.date >= :periodFrom
              AND te.date <= :periodTo
        ) time_agg ON true
        LEFT JOIN LATERAL (
            SELECT
                COUNT(*) AS unbilled_expense_count,
                SUM(e.amount * (1 + COALESCE(e.markup_percent, 0) / 100.0)) AS unbilled_expense_amount
            FROM expenses e
            JOIN projects p ON e.project_id = p.id
            JOIN customer_projects cp ON cp.project_id = p.id AND cp.customer_id = c.id
            WHERE e.billable = true
              AND e.invoice_id IS NULL
              AND e.currency = :currency
              AND e.date >= :periodFrom
              AND e.date <= :periodTo
        ) exp_agg ON true
        WHERE c.lifecycle_status = 'ACTIVE'
          AND (COALESCE(time_agg.unbilled_time_count, 0) > 0 OR COALESCE(exp_agg.unbilled_expense_count, 0) > 0)
        ORDER BY c.name
        """;

    @SuppressWarnings("unchecked")
    List<Tuple> rows =
        entityManager
            .createNativeQuery(sql, Tuple.class)
            .setParameter("currency", currency)
            .setParameter("periodFrom", periodFrom)
            .setParameter("periodTo", periodTo)
            .getResultList();

    return rows.stream()
        .map(
            row -> {
              UUID customerId = row.get("customer_id", UUID.class);
              String customerName = row.get("customer_name", String.class);
              String customerEmail = row.get("customer_email", String.class);
              int timeCount = ((Number) row.get("unbilled_time_count")).intValue();
              BigDecimal timeAmount =
                  new BigDecimal(row.get("unbilled_time_amount").toString())
                      .setScale(2, RoundingMode.HALF_UP);
              int expenseCount = ((Number) row.get("unbilled_expense_count")).intValue();
              BigDecimal expenseAmount =
                  new BigDecimal(row.get("unbilled_expense_amount").toString())
                      .setScale(2, RoundingMode.HALF_UP);
              BigDecimal totalAmount = timeAmount.add(expenseAmount);

              var prereqCheck =
                  prerequisiteService.checkForContext(
                      PrerequisiteContext.INVOICE_GENERATION, EntityType.CUSTOMER, customerId);
              boolean hasIssues = !prereqCheck.passed();
              String issueReason =
                  hasIssues
                      ? prereqCheck.violations().stream()
                          .map(v -> v.message())
                          .collect(Collectors.joining("; "))
                      : null;

              return new BillingRunDtos.CustomerUnbilledSummary(
                  customerId,
                  customerName,
                  customerEmail,
                  timeCount,
                  timeAmount,
                  expenseCount,
                  expenseAmount,
                  totalAmount,
                  hasIssues,
                  issueReason);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public UnbilledTimeResponse getUnbilledTime(UUID customerId, LocalDate from, LocalDate to) {
    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    String sql =
        """
        SELECT te.id AS te_id, te.date AS te_date, te.duration_minutes AS te_duration,
               te.billing_rate_snapshot AS te_rate, te.billing_rate_currency AS te_currency,
               te.description AS te_description,
               te.member_id AS te_member_id,
               t.title AS task_title,
               p.id AS project_id, p.name AS project_name,
               m.name AS member_name
        FROM time_entries te
        JOIN tasks t ON te.task_id = t.id
        JOIN projects p ON t.project_id = p.id
        JOIN customer_projects cp ON cp.project_id = p.id
        JOIN members m ON te.member_id = m.id
        WHERE cp.customer_id = :customerId
          AND te.billable = true
          AND te.invoice_id IS NULL
          AND (CAST(:fromDate AS DATE) IS NULL OR te.date >= CAST(:fromDate AS DATE))
          AND (CAST(:toDate AS DATE) IS NULL OR te.date <= CAST(:toDate AS DATE))
        ORDER BY p.name, te.date, m.name
        """;

    @SuppressWarnings("unchecked")
    List<Tuple> rows =
        entityManager
            .createNativeQuery(sql, Tuple.class)
            .setParameter("customerId", customerId)
            .setParameter("fromDate", from)
            .setParameter("toDate", to)
            .getResultList();

    // Pre-load org settings to use the default currency as fallback for null-rate entries
    var orgSettings = orgSettingsRepository.findForCurrentTenant().orElse(null);
    String fallbackCurrency =
        orgSettings != null && orgSettings.getDefaultCurrency() != null
            ? orgSettings.getDefaultCurrency()
            : "USD";

    Map<UUID, List<UnbilledTimeEntry>> grouped = new LinkedHashMap<>();
    Map<UUID, String> projectNames = new LinkedHashMap<>();

    for (Tuple row : rows) {
      UUID projectId = row.get("project_id", UUID.class);
      String projectName = row.get("project_name", String.class);
      projectNames.putIfAbsent(projectId, projectName);

      BigDecimal rate = row.get("te_rate", BigDecimal.class);
      String currency = row.get("te_currency", String.class);
      int durationMinutes = ((Number) row.get("te_duration")).intValue();

      Object dateObj = row.get("te_date");
      LocalDate entryDate;
      if (dateObj instanceof LocalDate ld) {
        entryDate = ld;
      } else {
        entryDate = ((java.sql.Date) dateObj).toLocalDate();
      }

      String rateSource = null;
      if (rate == null) {
        // TODO: N+1 — resolveRate is called per entry. Batch rate resolution would
        // pre-load rates for all (memberId, projectId) pairs in one query.
        // Fall back to live rate card resolution for null-snapshot entries
        UUID memberId = row.get("te_member_id", UUID.class);
        var resolved = billingRateService.resolveRate(memberId, projectId, entryDate);
        if (resolved.isPresent()) {
          rate = resolved.get().hourlyRate();
          currency = resolved.get().currency();
          rateSource = "RESOLVED";
        }
      } else {
        rateSource = "SNAPSHOT";
      }

      BigDecimal billableValue = null;
      if (rate != null) {
        billableValue =
            BigDecimal.valueOf(durationMinutes)
                .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP)
                .multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);
      }

      var entry =
          new UnbilledTimeEntry(
              row.get("te_id", UUID.class),
              row.get("task_title", String.class),
              row.get("member_name", String.class),
              entryDate,
              durationMinutes,
              rate,
              currency,
              billableValue,
              row.get("te_description", String.class),
              rateSource);

      grouped.computeIfAbsent(projectId, k -> new ArrayList<>()).add(entry);
    }

    Map<String, double[]> grandHours = new LinkedHashMap<>();
    Map<String, BigDecimal> grandAmounts = new LinkedHashMap<>();

    List<UnbilledProjectGroup> projectGroups = new ArrayList<>();
    for (var mapEntry : grouped.entrySet()) {
      UUID projectId = mapEntry.getKey();
      List<UnbilledTimeEntry> entries = mapEntry.getValue();
      String projectName = projectNames.get(projectId);

      Map<String, double[]> projHours = new LinkedHashMap<>();
      Map<String, BigDecimal> projAmounts = new LinkedHashMap<>();

      for (var e : entries) {
        String cur = e.billingRateCurrency() != null ? e.billingRateCurrency() : fallbackCurrency;
        double hours = e.durationMinutes() / 60.0;
        projHours.computeIfAbsent(cur, k -> new double[1])[0] += hours;
        projAmounts.merge(
            cur, e.billableValue() != null ? e.billableValue() : BigDecimal.ZERO, BigDecimal::add);

        grandHours.computeIfAbsent(cur, k -> new double[1])[0] += hours;
        grandAmounts.merge(
            cur, e.billableValue() != null ? e.billableValue() : BigDecimal.ZERO, BigDecimal::add);
      }

      Map<String, CurrencyTotal> projTotals = new LinkedHashMap<>();
      for (var curEntry : projHours.entrySet()) {
        projTotals.put(
            curEntry.getKey(),
            new CurrencyTotal(
                curEntry.getValue()[0],
                projAmounts.getOrDefault(curEntry.getKey(), BigDecimal.ZERO)));
      }

      projectGroups.add(new UnbilledProjectGroup(projectId, projectName, entries, projTotals));
    }

    Map<String, CurrencyTotal> grandTotals = new LinkedHashMap<>();
    for (var curEntry : grandHours.entrySet()) {
      grandTotals.put(
          curEntry.getKey(),
          new CurrencyTotal(
              curEntry.getValue()[0],
              grandAmounts.getOrDefault(curEntry.getKey(), BigDecimal.ZERO)));
    }

    var unbilledExpenses = expenseRepository.findUnbilledBillableByCustomerId(customerId);
    // orgSettings already loaded above for currency fallback
    BigDecimal orgMarkup =
        orgSettings != null ? orgSettings.getDefaultExpenseMarkupPercent() : null;

    Map<UUID, String> expenseProjectNames = new LinkedHashMap<>();
    for (var expense : unbilledExpenses) {
      if (!expenseProjectNames.containsKey(expense.getProjectId())) {
        projectRepository
            .findById(expense.getProjectId())
            .ifPresent(p -> expenseProjectNames.put(p.getId(), p.getName()));
      }
    }

    List<UnbilledExpenseEntry> expenseEntries =
        unbilledExpenses.stream()
            .map(
                e ->
                    new UnbilledExpenseEntry(
                        e.getId(),
                        e.getProjectId(),
                        expenseProjectNames.getOrDefault(e.getProjectId(), "Unknown"),
                        e.getDate(),
                        e.getDescription(),
                        e.getAmount(),
                        e.getCurrency(),
                        e.getCategory().name(),
                        e.getMarkupPercent(),
                        e.computeBillableAmount(orgMarkup),
                        e.getNotes()))
            .toList();

    Map<String, BigDecimal> expenseTotals = new LinkedHashMap<>();
    for (var entry : expenseEntries) {
      expenseTotals.merge(entry.currency(), entry.billableAmount(), BigDecimal::add);
    }

    return new UnbilledTimeResponse(
        customerId, customer.getName(), projectGroups, grandTotals, expenseEntries, expenseTotals);
  }
}
