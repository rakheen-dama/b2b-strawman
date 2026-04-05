package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Per-client transaction history with running balance for a trust account. */
@Component
public class ClientLedgerStatementQuery implements ReportQuery {

  private final TrustTransactionRepository transactionRepository;

  public ClientLedgerStatementQuery(TrustTransactionRepository transactionRepository) {
    this.transactionRepository = transactionRepository;
  }

  @Override
  public String getSlug() {
    return "client-ledger-statement";
  }

  @Override
  public ReportResult execute(Map<String, Object> parameters, Pageable pageable) {
    var allRows = queryRows(parameters);
    var summary = computeSummary(allRows, parameters);

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
    var summary = computeSummary(allRows, parameters);
    return new ReportResult(allRows, summary);
  }

  private List<Map<String, Object>> queryRows(Map<String, Object> parameters) {
    var trustAccountId = ReportParamUtils.parseUuid(parameters, "trust_account_id");
    var customerId = ReportParamUtils.parseUuid(parameters, "customer_id");
    var dateFrom = ReportParamUtils.parseDate(parameters, "dateFrom");
    var dateTo = ReportParamUtils.parseDate(parameters, "dateTo");

    // Calculate opening balance (all transactions before dateFrom)
    var openingBalance =
        transactionRepository.calculateClientBalanceAsOfDate(
            customerId, trustAccountId, dateFrom.minusDays(1));

    // Get transactions in the date range
    var transactions =
        transactionRepository.findForStatement(customerId, trustAccountId, dateFrom, dateTo);

    BigDecimal runningBalance = openingBalance;
    var rows = new java.util.ArrayList<Map<String, Object>>();

    for (var tx : transactions) {
      var row = new LinkedHashMap<String, Object>();
      row.put("date", tx.getTransactionDate().toString());
      row.put("reference", tx.getReference());
      row.put("type", tx.getTransactionType());
      row.put("description", tx.getDescription());

      boolean isCredit = isCreditType(tx.getTransactionType());
      if (isCredit) {
        row.put("credit", tx.getAmount());
        row.put("debit", BigDecimal.ZERO);
        runningBalance = runningBalance.add(tx.getAmount());
      } else {
        row.put("credit", BigDecimal.ZERO);
        row.put("debit", tx.getAmount());
        runningBalance = runningBalance.subtract(tx.getAmount());
      }
      row.put("runningBalance", runningBalance);
      rows.add(row);
    }

    return rows;
  }

  private boolean isCreditType(String type) {
    return List.of("DEPOSIT", "TRANSFER_IN", "INTEREST_CREDIT").contains(type);
  }

  private Map<String, Object> computeSummary(
      List<Map<String, Object>> rows, Map<String, Object> parameters) {
    var summary = new LinkedHashMap<String, Object>();
    var trustAccountId = ReportParamUtils.parseUuid(parameters, "trust_account_id");
    var customerId = ReportParamUtils.parseUuid(parameters, "customer_id");
    var dateFrom = ReportParamUtils.parseDate(parameters, "dateFrom");
    var dateTo = ReportParamUtils.parseDate(parameters, "dateTo");

    var openingBalance =
        transactionRepository.calculateClientBalanceAsOfDate(
            customerId, trustAccountId, dateFrom.minusDays(1));
    var closingBalance =
        transactionRepository.calculateClientBalanceAsOfDate(customerId, trustAccountId, dateTo);

    BigDecimal totalDebits = BigDecimal.ZERO;
    BigDecimal totalCredits = BigDecimal.ZERO;
    for (var row : rows) {
      totalDebits = totalDebits.add((BigDecimal) row.get("debit"));
      totalCredits = totalCredits.add((BigDecimal) row.get("credit"));
    }

    summary.put("openingBalance", openingBalance);
    summary.put("closingBalance", closingBalance);
    summary.put("totalDebits", totalDebits);
    summary.put("totalCredits", totalCredits);
    summary.put("transactionCount", rows.size());
    return summary;
  }
}
