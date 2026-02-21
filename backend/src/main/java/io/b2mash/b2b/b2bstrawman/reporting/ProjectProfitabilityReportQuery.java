package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.report.OrgCostProjection;
import io.b2mash.b2b.b2bstrawman.report.OrgRevenueProjection;
import io.b2mash.b2b.b2bstrawman.report.ReportRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class ProjectProfitabilityReportQuery implements ReportQuery {

  private final ReportRepository reportRepository;

  public ProjectProfitabilityReportQuery(ReportRepository reportRepository) {
    this.reportRepository = reportRepository;
  }

  @Override
  public String getSlug() {
    return "project-profitability";
  }

  @Override
  public ReportResult execute(Map<String, Object> parameters, Pageable pageable) {
    var allRows = queryRows(parameters);
    var summary = computeSummary(allRows);

    int offset = (int) pageable.getOffset();
    int size = pageable.getPageSize();
    int total = allRows.size();
    int totalPages = (total + size - 1) / size;

    var pagedRows = allRows.subList(Math.min(offset, total), Math.min(offset + size, total));

    return new ReportResult(pagedRows, summary, total, totalPages);
  }

  @Override
  public ReportResult executeAll(Map<String, Object> parameters) {
    var allRows = queryRows(parameters);
    var summary = computeSummary(allRows);
    return new ReportResult(allRows, summary);
  }

  private List<Map<String, Object>> queryRows(Map<String, Object> parameters) {
    var dateFrom = parseDate(parameters, "dateFrom");
    var dateTo = parseDate(parameters, "dateTo");
    var customerId = parseUuid(parameters, "customerId");
    var projectId = parseUuid(parameters, "projectId");

    var revenueList = reportRepository.getOrgProjectRevenue(dateFrom, dateTo, customerId);
    var costList = reportRepository.getOrgProjectCost(dateFrom, dateTo, customerId);

    return mergeToRows(revenueList, costList, projectId);
  }

  private List<Map<String, Object>> mergeToRows(
      List<OrgRevenueProjection> revenueList, List<OrgCostProjection> costList, UUID projectId) {

    // Build a map keyed by projectId, merging revenue and cost data
    var projectMap = new LinkedHashMap<UUID, Map<String, Object>>();

    for (var rev : revenueList) {
      var row =
          projectMap.computeIfAbsent(
              rev.getProjectId(),
              id -> {
                var m = new LinkedHashMap<String, Object>();
                m.put("projectId", id);
                m.put("projectName", rev.getProjectName());
                m.put("currency", rev.getCurrency());
                m.put("billableHours", BigDecimal.ZERO);
                m.put("revenue", BigDecimal.ZERO);
                m.put("cost", BigDecimal.ZERO);
                return m;
              });
      row.put("billableHours", ((BigDecimal) row.get("billableHours")).add(rev.getBillableHours()));
      row.put("revenue", ((BigDecimal) row.get("revenue")).add(rev.getBillableValue()));
    }

    for (var cost : costList) {
      var row =
          projectMap.computeIfAbsent(
              cost.getProjectId(),
              id -> {
                var m = new LinkedHashMap<String, Object>();
                m.put("projectId", id);
                m.put("projectName", cost.getProjectName());
                m.put("currency", cost.getCurrency());
                m.put("billableHours", BigDecimal.ZERO);
                m.put("revenue", BigDecimal.ZERO);
                m.put("cost", BigDecimal.ZERO);
                return m;
              });
      row.put("cost", ((BigDecimal) row.get("cost")).add(cost.getCostValue()));
    }

    // Compute margin and marginPercent for each project
    for (var row : projectMap.values()) {
      var revenue = (BigDecimal) row.get("revenue");
      var cost = (BigDecimal) row.get("cost");
      var margin = revenue.subtract(cost);
      row.put("margin", margin);
      if (revenue.compareTo(BigDecimal.ZERO) == 0) {
        row.put("marginPercent", BigDecimal.ZERO);
      } else {
        row.put(
            "marginPercent",
            margin.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP));
      }
    }

    // Apply optional projectId filter
    var rows = projectMap.values().stream().map(row -> (Map<String, Object>) row);
    if (projectId != null) {
      rows = rows.filter(row -> projectId.equals(row.get("projectId")));
    }

    return rows.toList();
  }

  private Map<String, Object> computeSummary(List<Map<String, Object>> rows) {
    var summary = new LinkedHashMap<String, Object>();

    var totalBillableHours = BigDecimal.ZERO;
    var totalRevenue = BigDecimal.ZERO;
    var totalCost = BigDecimal.ZERO;

    for (var row : rows) {
      totalBillableHours = totalBillableHours.add((BigDecimal) row.get("billableHours"));
      totalRevenue = totalRevenue.add((BigDecimal) row.get("revenue"));
      totalCost = totalCost.add((BigDecimal) row.get("cost"));
    }

    var totalMargin = totalRevenue.subtract(totalCost);
    BigDecimal avgMarginPercent;
    if (totalRevenue.compareTo(BigDecimal.ZERO) == 0) {
      avgMarginPercent = BigDecimal.ZERO;
    } else {
      avgMarginPercent =
          totalMargin
              .multiply(BigDecimal.valueOf(100))
              .divide(totalRevenue, 2, RoundingMode.HALF_UP);
    }

    summary.put("totalBillableHours", totalBillableHours);
    summary.put("totalRevenue", totalRevenue);
    summary.put("totalCost", totalCost);
    summary.put("totalMargin", totalMargin);
    summary.put("avgMarginPercent", avgMarginPercent);

    return summary;
  }

  private LocalDate parseDate(Map<String, Object> params, String key) {
    var value = params.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof LocalDate ld) {
      return ld;
    }
    return LocalDate.parse(value.toString());
  }

  private UUID parseUuid(Map<String, Object> params, String key) {
    var value = params.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof UUID uuid) {
      return uuid;
    }
    try {
      return UUID.fromString(value.toString());
    } catch (IllegalArgumentException e) {
      throw new InvalidStateException(
          "Invalid parameter", "Parameter '%s' is not a valid UUID: %s".formatted(key, value));
    }
  }
}
