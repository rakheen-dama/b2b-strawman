package io.b2mash.b2b.b2bstrawman.setupstatus;

import io.b2mash.b2b.b2bstrawman.settings.OrgSettings;
import io.b2mash.b2b.b2bstrawman.settings.OrgSettingsRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnbilledTimeSummaryService {

  private final EntityManager entityManager;
  private final OrgSettingsRepository orgSettingsRepository;

  public UnbilledTimeSummaryService(
      EntityManager entityManager, OrgSettingsRepository orgSettingsRepository) {
    this.entityManager = entityManager;
    this.orgSettingsRepository = orgSettingsRepository;
  }

  @Transactional(readOnly = true)
  public UnbilledTimeSummary getProjectUnbilledSummary(UUID projectId) {
    String currency = resolveCurrency();

    var query =
        entityManager.createNativeQuery(
            """
            SELECT
              COALESCE(SUM(te.duration_minutes), 0) AS total_minutes,
              COALESCE(SUM(
                (te.duration_minutes / 60.0) * te.billing_rate_snapshot
              ), 0) AS total_amount,
              COUNT(*) AS entry_count
            FROM time_entries te
            JOIN tasks t ON t.id = te.task_id
            WHERE t.project_id = :projectId
              AND te.billable = true
              AND te.invoice_id IS NULL
              AND te.billing_rate_snapshot IS NOT NULL
            """,
            Tuple.class);
    query.setParameter("projectId", projectId);

    @SuppressWarnings("unchecked")
    List<Tuple> results = query.getResultList();

    if (results.isEmpty()) {
      return new UnbilledTimeSummary(BigDecimal.ZERO, BigDecimal.ZERO, currency, 0, null);
    }

    Tuple row = results.getFirst();
    BigDecimal totalMinutes = toBigDecimal(row.get("total_minutes"));
    BigDecimal totalAmount = toBigDecimal(row.get("total_amount"));
    int entryCount = toInt(row.get("entry_count"));

    if (entryCount == 0) {
      return new UnbilledTimeSummary(BigDecimal.ZERO, BigDecimal.ZERO, currency, 0, null);
    }

    BigDecimal totalHours = totalMinutes.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

    return new UnbilledTimeSummary(
        totalHours, totalAmount.setScale(2, RoundingMode.HALF_UP), currency, entryCount, null);
  }

  @Transactional(readOnly = true)
  public UnbilledTimeSummary getCustomerUnbilledSummary(UUID customerId) {
    String currency = resolveCurrency();

    var query =
        entityManager.createNativeQuery(
            """
            SELECT
              p.id AS project_id,
              p.name AS project_name,
              COALESCE(SUM(te.duration_minutes), 0) AS total_minutes,
              COALESCE(SUM(
                (te.duration_minutes / 60.0) * te.billing_rate_snapshot
              ), 0) AS total_amount,
              COUNT(*) AS entry_count
            FROM time_entries te
            JOIN tasks t ON t.id = te.task_id
            JOIN projects p ON p.id = t.project_id
            JOIN customer_projects cp ON cp.project_id = p.id
            WHERE cp.customer_id = :customerId
              AND te.billable = true
              AND te.invoice_id IS NULL
              AND te.billing_rate_snapshot IS NOT NULL
            GROUP BY p.id, p.name
            ORDER BY total_amount DESC
            """,
            Tuple.class);
    query.setParameter("customerId", customerId);

    @SuppressWarnings("unchecked")
    List<Tuple> results = query.getResultList();

    if (results.isEmpty()) {
      return new UnbilledTimeSummary(BigDecimal.ZERO, BigDecimal.ZERO, currency, 0, null);
    }

    List<ProjectUnbilledBreakdown> byProject = new ArrayList<>();
    BigDecimal totalHours = BigDecimal.ZERO;
    BigDecimal totalAmount = BigDecimal.ZERO;
    int totalEntryCount = 0;

    for (Tuple row : results) {
      UUID projectId = row.get("project_id", UUID.class);
      String projectName = row.get("project_name", String.class);
      BigDecimal minutes = toBigDecimal(row.get("total_minutes"));
      BigDecimal amount = toBigDecimal(row.get("total_amount"));
      int entryCount = toInt(row.get("entry_count"));

      BigDecimal hours = minutes.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
      BigDecimal scaledAmount = amount.setScale(2, RoundingMode.HALF_UP);

      byProject.add(
          new ProjectUnbilledBreakdown(projectId, projectName, hours, scaledAmount, entryCount));
      totalHours = totalHours.add(hours);
      totalAmount = totalAmount.add(scaledAmount);
      totalEntryCount += entryCount;
    }

    return new UnbilledTimeSummary(
        totalHours, totalAmount, currency, totalEntryCount, List.copyOf(byProject));
  }

  private String resolveCurrency() {
    return orgSettingsRepository
        .findForCurrentTenant()
        .map(OrgSettings::getDefaultCurrency)
        .orElse("USD");
  }

  private BigDecimal toBigDecimal(Object value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    if (value instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    return new BigDecimal(value.toString());
  }

  private int toInt(Object value) {
    if (value == null) {
      return 0;
    }
    if (value instanceof Number n) {
      return n.intValue();
    }
    return Integer.parseInt(value.toString());
  }
}
