package io.b2mash.b2b.b2bstrawman.reporting;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class TimesheetReportQuery implements ReportQuery {

  private final EntityManager entityManager;

  public TimesheetReportQuery(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public String getSlug() {
    return "timesheet";
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
    var groupBy = parameters.getOrDefault("groupBy", "member").toString();

    return switch (groupBy) {
      case "project" -> queryByProject(parameters);
      case "date" -> queryByDate(parameters);
      default -> queryByMember(parameters);
    };
  }

  private List<Map<String, Object>> queryByMember(Map<String, Object> parameters) {
    var sql =
        """
        SELECT
            m.id AS member_id,
            m.name AS member_name,
            SUM(te.duration_minutes) / 60.0 AS total_hours,
            SUM(CASE WHEN te.billable THEN te.duration_minutes ELSE 0 END) / 60.0 AS billable_hours,
            SUM(CASE WHEN NOT te.billable THEN te.duration_minutes ELSE 0 END) / 60.0 AS non_billable_hours,
            COUNT(te.id) AS entry_count
        FROM time_entries te
        JOIN tasks t ON te.task_id = t.id
        JOIN members m ON te.member_id = m.id
        WHERE te.date >= CAST(:dateFrom AS DATE)
          AND te.date <= CAST(:dateTo AS DATE)
          AND (CAST(:projectId AS UUID) IS NULL OR t.project_id = CAST(:projectId AS UUID))
          AND (CAST(:memberId AS UUID) IS NULL OR te.member_id = CAST(:memberId AS UUID))
        GROUP BY m.id, m.name
        ORDER BY total_hours DESC
        """;

    var query = entityManager.createNativeQuery(sql, Tuple.class);
    setCommonParameters(query, parameters);

    @SuppressWarnings("unchecked")
    List<Tuple> tuples = query.getResultList();
    return tuples.stream()
        .map(
            t -> {
              var row = new LinkedHashMap<String, Object>();
              row.put("groupLabel", t.get("member_name", String.class));
              row.put("totalHours", toDouble(t.get("total_hours")));
              row.put("billableHours", toDouble(t.get("billable_hours")));
              row.put("nonBillableHours", toDouble(t.get("non_billable_hours")));
              row.put("entryCount", ((Number) t.get("entry_count")).longValue());
              return (Map<String, Object>) (Map<String, ?>) row;
            })
        .toList();
  }

  private List<Map<String, Object>> queryByProject(Map<String, Object> parameters) {
    var sql =
        """
        SELECT
            t.project_id AS project_id,
            p.name AS project_name,
            SUM(te.duration_minutes) / 60.0 AS total_hours,
            SUM(CASE WHEN te.billable THEN te.duration_minutes ELSE 0 END) / 60.0 AS billable_hours,
            SUM(CASE WHEN NOT te.billable THEN te.duration_minutes ELSE 0 END) / 60.0 AS non_billable_hours,
            COUNT(te.id) AS entry_count
        FROM time_entries te
        JOIN tasks t ON te.task_id = t.id
        JOIN projects p ON t.project_id = p.id
        WHERE te.date >= CAST(:dateFrom AS DATE)
          AND te.date <= CAST(:dateTo AS DATE)
          AND (CAST(:projectId AS UUID) IS NULL OR t.project_id = CAST(:projectId AS UUID))
          AND (CAST(:memberId AS UUID) IS NULL OR te.member_id = CAST(:memberId AS UUID))
        GROUP BY t.project_id, p.name
        ORDER BY total_hours DESC
        """;

    var query = entityManager.createNativeQuery(sql, Tuple.class);
    setCommonParameters(query, parameters);

    @SuppressWarnings("unchecked")
    List<Tuple> tuples = query.getResultList();
    return tuples.stream()
        .map(
            t -> {
              var row = new LinkedHashMap<String, Object>();
              row.put("groupLabel", t.get("project_name", String.class));
              row.put("totalHours", toDouble(t.get("total_hours")));
              row.put("billableHours", toDouble(t.get("billable_hours")));
              row.put("nonBillableHours", toDouble(t.get("non_billable_hours")));
              row.put("entryCount", ((Number) t.get("entry_count")).longValue());
              return (Map<String, Object>) (Map<String, ?>) row;
            })
        .toList();
  }

  private List<Map<String, Object>> queryByDate(Map<String, Object> parameters) {
    var sql =
        """
        SELECT
            te.date AS entry_date,
            SUM(te.duration_minutes) / 60.0 AS total_hours,
            SUM(CASE WHEN te.billable THEN te.duration_minutes ELSE 0 END) / 60.0 AS billable_hours,
            SUM(CASE WHEN NOT te.billable THEN te.duration_minutes ELSE 0 END) / 60.0 AS non_billable_hours,
            COUNT(te.id) AS entry_count
        FROM time_entries te
        JOIN tasks t ON te.task_id = t.id
        WHERE te.date >= CAST(:dateFrom AS DATE)
          AND te.date <= CAST(:dateTo AS DATE)
          AND (CAST(:projectId AS UUID) IS NULL OR t.project_id = CAST(:projectId AS UUID))
          AND (CAST(:memberId AS UUID) IS NULL OR te.member_id = CAST(:memberId AS UUID))
        GROUP BY te.date
        ORDER BY te.date
        """;

    var query = entityManager.createNativeQuery(sql, Tuple.class);
    setCommonParameters(query, parameters);

    @SuppressWarnings("unchecked")
    List<Tuple> tuples = query.getResultList();
    return tuples.stream()
        .map(
            t -> {
              var row = new LinkedHashMap<String, Object>();
              row.put("groupLabel", t.get("entry_date").toString());
              row.put("totalHours", toDouble(t.get("total_hours")));
              row.put("billableHours", toDouble(t.get("billable_hours")));
              row.put("nonBillableHours", toDouble(t.get("non_billable_hours")));
              row.put("entryCount", ((Number) t.get("entry_count")).longValue());
              return (Map<String, Object>) (Map<String, ?>) row;
            })
        .toList();
  }

  private void setCommonParameters(
      jakarta.persistence.Query query, Map<String, Object> parameters) {
    query.setParameter("dateFrom", parseDate(parameters, "dateFrom"));
    query.setParameter("dateTo", parseDate(parameters, "dateTo"));
    query.setParameter("projectId", parseUuid(parameters, "projectId"));
    query.setParameter("memberId", parseUuid(parameters, "memberId"));
  }

  private Map<String, Object> computeSummary(List<Map<String, Object>> rows) {
    double totalHours = 0;
    double billableHours = 0;
    double nonBillableHours = 0;
    long entryCount = 0;

    for (var row : rows) {
      totalHours += toDouble(row.get("totalHours"));
      billableHours += toDouble(row.get("billableHours"));
      nonBillableHours += toDouble(row.get("nonBillableHours"));
      entryCount += ((Number) row.get("entryCount")).longValue();
    }

    return Map.of(
        "totalHours", totalHours,
        "billableHours", billableHours,
        "nonBillableHours", nonBillableHours,
        "entryCount", entryCount);
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
    return UUID.fromString(value.toString());
  }

  private double toDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return 0.0;
  }
}
