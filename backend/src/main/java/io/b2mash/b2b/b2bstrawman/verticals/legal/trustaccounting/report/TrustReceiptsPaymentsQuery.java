package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Chronological journal of all trust receipts and payments for a date range. Uses the
 * findForStatement query (which requires a customer ID) per-client and merges results, or queries
 * all transactions for the trust account in the date range.
 */
@Component
public class TrustReceiptsPaymentsQuery implements ReportQuery {

  private final TrustTransactionRepository transactionRepository;
  private final CustomerRepository customerRepository;

  public TrustReceiptsPaymentsQuery(
      TrustTransactionRepository transactionRepository, CustomerRepository customerRepository) {
    this.transactionRepository = transactionRepository;
    this.customerRepository = customerRepository;
  }

  @Override
  public String getSlug() {
    return "trust-receipts-payments";
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
    var trustAccountId = ReportParamUtils.requireUuid(parameters, "trust_account_id");
    var dateFrom = ReportParamUtils.requireDate(parameters, "dateFrom");
    var dateTo = ReportParamUtils.requireDate(parameters, "dateTo");

    // Query transactions filtered by date range and status at database level
    var transactions =
        transactionRepository.findForReceiptsPayments(trustAccountId, dateFrom, dateTo);

    // Batch-load customer names
    var customerIds =
        transactions.stream()
            .map(t -> t.getCustomerId())
            .filter(id -> id != null)
            .collect(Collectors.toSet());
    var customerNames =
        customerRepository.findByIdIn(customerIds).stream()
            .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

    // Build rows sorted by date ascending
    var sorted =
        transactions.stream()
            .sorted(
                (a, b) -> {
                  int dateCompare = a.getTransactionDate().compareTo(b.getTransactionDate());
                  if (dateCompare != 0) return dateCompare;
                  return a.getCreatedAt().compareTo(b.getCreatedAt());
                })
            .toList();

    BigDecimal runningBalance = BigDecimal.ZERO;
    var rows = new java.util.ArrayList<Map<String, Object>>();
    for (var tx : sorted) {
      var row = new LinkedHashMap<String, Object>();
      row.put("date", tx.getTransactionDate().toString());
      row.put("reference", tx.getReference());
      row.put("type", tx.getTransactionType());
      row.put(
          "clientName",
          tx.getCustomerId() != null ? customerNames.getOrDefault(tx.getCustomerId(), "-") : "-");

      boolean isCredit = isReceiptType(tx.getTransactionType());
      if (isCredit) {
        row.put("credit", tx.getAmount());
        row.put("debit", BigDecimal.ZERO);
        runningBalance = runningBalance.add(tx.getAmount());
      } else {
        row.put("credit", BigDecimal.ZERO);
        row.put("debit", tx.getAmount());
        runningBalance = runningBalance.subtract(tx.getAmount());
      }
      row.put("balance", runningBalance);
      rows.add(row);
    }
    return rows;
  }

  private boolean isReceiptType(String type) {
    return List.of("DEPOSIT", "INTEREST_CREDIT", "TRANSFER_IN").contains(type);
  }

  private Map<String, Object> computeSummary(List<Map<String, Object>> rows) {
    var summary = new LinkedHashMap<String, Object>();
    BigDecimal totalReceipts = BigDecimal.ZERO;
    BigDecimal totalPayments = BigDecimal.ZERO;

    for (var row : rows) {
      totalReceipts = totalReceipts.add((BigDecimal) row.get("credit"));
      totalPayments = totalPayments.add((BigDecimal) row.get("debit"));
    }

    summary.put("totalReceipts", totalReceipts);
    summary.put("totalPayments", totalPayments);
    summary.put("netMovement", totalReceipts.subtract(totalPayments));
    summary.put("transactionCount", rows.size());
    return summary;
  }
}
