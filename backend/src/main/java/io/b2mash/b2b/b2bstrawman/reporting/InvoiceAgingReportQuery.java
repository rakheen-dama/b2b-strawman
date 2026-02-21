package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class InvoiceAgingReportQuery implements ReportQuery {

  private final EntityManager entityManager;

  public InvoiceAgingReportQuery(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public String getSlug() {
    return "invoice-aging";
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
    var asOfDate = parseDate(parameters, "asOfDate");
    if (asOfDate == null) {
      asOfDate = LocalDate.now();
    }
    var customerId = parseUuid(parameters, "customerId");

    var sql =
        """
        SELECT
            i.id AS invoice_id,
            i.invoice_number AS invoice_number,
            i.customer_name AS customer_name,
            i.issue_date AS issue_date,
            i.due_date AS due_date,
            i.total AS amount,
            i.currency AS currency,
            i.status AS status,
            CAST(:asOfDate AS DATE) - i.due_date AS days_overdue,
            CASE
                WHEN CAST(:asOfDate AS DATE) - i.due_date <= 0 THEN 'CURRENT'
                WHEN CAST(:asOfDate AS DATE) - i.due_date BETWEEN 1 AND 30 THEN '1_30'
                WHEN CAST(:asOfDate AS DATE) - i.due_date BETWEEN 31 AND 60 THEN '31_60'
                WHEN CAST(:asOfDate AS DATE) - i.due_date BETWEEN 61 AND 90 THEN '61_90'
                ELSE '90_PLUS'
            END AS age_bucket
        FROM invoices i
        WHERE i.status = 'SENT'
          AND (CAST(:customerId AS UUID) IS NULL OR i.customer_id = CAST(:customerId AS UUID))
        ORDER BY days_overdue DESC
        """;

    var query = entityManager.createNativeQuery(sql, Tuple.class);
    query.setParameter("asOfDate", asOfDate);
    query.setParameter("customerId", customerId);

    @SuppressWarnings("unchecked")
    List<Tuple> tuples = query.getResultList();
    return tuples.stream()
        .map(
            t -> {
              var row = new LinkedHashMap<String, Object>();
              row.put("invoiceId", t.get("invoice_id", UUID.class));
              row.put("invoiceNumber", t.get("invoice_number", String.class));
              row.put("customerName", t.get("customer_name", String.class));
              row.put(
                  "issueDate", t.get("issue_date") != null ? t.get("issue_date").toString() : null);
              row.put("dueDate", t.get("due_date") != null ? t.get("due_date").toString() : null);
              row.put("amount", toBigDecimal(t.get("amount")));
              row.put("currency", t.get("currency", String.class));
              row.put("status", t.get("status", String.class));
              row.put("daysOverdue", ((Number) t.get("days_overdue")).intValue());
              var bucket = t.get("age_bucket", String.class);
              row.put("ageBucket", bucket);
              row.put("ageBucketLabel", mapBucketLabel(bucket));
              return (Map<String, Object>) (Map<String, ?>) row;
            })
        .toList();
  }

  private String mapBucketLabel(String bucket) {
    return switch (bucket) {
      case "CURRENT" -> "Current";
      case "1_30" -> "1-30 Days";
      case "31_60" -> "31-60 Days";
      case "61_90" -> "61-90 Days";
      case "90_PLUS" -> "90+ Days";
      default -> bucket;
    };
  }

  private Map<String, Object> computeSummary(List<Map<String, Object>> rows) {
    var summary = new LinkedHashMap<String, Object>();

    int currentCount = 0;
    BigDecimal currentAmount = BigDecimal.ZERO;
    int bucket1_30Count = 0;
    BigDecimal bucket1_30Amount = BigDecimal.ZERO;
    int bucket31_60Count = 0;
    BigDecimal bucket31_60Amount = BigDecimal.ZERO;
    int bucket61_90Count = 0;
    BigDecimal bucket61_90Amount = BigDecimal.ZERO;
    int bucket90PlusCount = 0;
    BigDecimal bucket90PlusAmount = BigDecimal.ZERO;

    for (var row : rows) {
      var bucket = (String) row.get("ageBucket");
      var amount = (BigDecimal) row.get("amount");

      switch (bucket) {
        case "CURRENT" -> {
          currentCount++;
          currentAmount = currentAmount.add(amount);
        }
        case "1_30" -> {
          bucket1_30Count++;
          bucket1_30Amount = bucket1_30Amount.add(amount);
        }
        case "31_60" -> {
          bucket31_60Count++;
          bucket31_60Amount = bucket31_60Amount.add(amount);
        }
        case "61_90" -> {
          bucket61_90Count++;
          bucket61_90Amount = bucket61_90Amount.add(amount);
        }
        case "90_PLUS" -> {
          bucket90PlusCount++;
          bucket90PlusAmount = bucket90PlusAmount.add(amount);
        }
        default -> {}
      }
    }

    summary.put("currentCount", currentCount);
    summary.put("currentAmount", currentAmount);
    summary.put("bucket1_30Count", bucket1_30Count);
    summary.put("bucket1_30Amount", bucket1_30Amount);
    summary.put("bucket31_60Count", bucket31_60Count);
    summary.put("bucket31_60Amount", bucket31_60Amount);
    summary.put("bucket61_90Count", bucket61_90Count);
    summary.put("bucket61_90Amount", bucket61_90Amount);
    summary.put("bucket90PlusCount", bucket90PlusCount);
    summary.put("bucket90PlusAmount", bucket90PlusAmount);
    summary.put("totalCount", rows.size());
    summary.put(
        "totalAmount",
        currentAmount
            .add(bucket1_30Amount)
            .add(bucket31_60Amount)
            .add(bucket61_90Amount)
            .add(bucket90PlusAmount));

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
}
