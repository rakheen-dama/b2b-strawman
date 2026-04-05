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
    var queryResult = buildRowsAndBalances(parameters);
    var summary = computeSummary(queryResult);

    int offset = (int) pageable.getOffset();
    int size = pageable.getPageSize();
    int total = queryResult.rows.size();
    int totalPages = (total + size - 1) / size;

    var pagedRows =
        queryResult.rows.subList(Math.min(offset, total), Math.min(offset + size, total));
    return new ReportResult(pagedRows, summary, total, totalPages);
  }

  @Override
  public ReportResult executeAll(Map<String, Object> parameters) {
    var queryResult = buildRowsAndBalances(parameters);
    var summary = computeSummary(queryResult);
    return new ReportResult(queryResult.rows, summary);
  }

  private record QueryResult(
      List<Map<String, Object>> rows, BigDecimal openingBalance, BigDecimal closingBalance) {}

  private QueryResult buildRowsAndBalances(Map<String, Object> parameters) {
    var trustAccountId = ReportParamUtils.requireUuid(parameters, "trust_account_id");
    var customerId = ReportParamUtils.requireUuid(parameters, "customer_id");
    var dateFrom = ReportParamUtils.requireDate(parameters, "dateFrom");
    var dateTo = ReportParamUtils.requireDate(parameters, "dateTo");

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

    // Closing balance is the final running balance after all transactions
    return new QueryResult(rows, openingBalance, runningBalance);
  }

  private boolean isCreditType(String type) {
    return List.of("DEPOSIT", "TRANSFER_IN", "INTEREST_CREDIT").contains(type);
  }

  private Map<String, Object> computeSummary(QueryResult queryResult) {
    var summary = new LinkedHashMap<String, Object>();

    BigDecimal totalDebits = BigDecimal.ZERO;
    BigDecimal totalCredits = BigDecimal.ZERO;
    for (var row : queryResult.rows) {
      totalDebits = totalDebits.add((BigDecimal) row.get("debit"));
      totalCredits = totalCredits.add((BigDecimal) row.get("credit"));
    }

    summary.put("openingBalance", queryResult.openingBalance);
    summary.put("closingBalance", queryResult.closingBalance);
    summary.put("totalDebits", totalDebits);
    summary.put("totalCredits", totalCredits);
    summary.put("transactionCount", queryResult.rows.size());
    return summary;
  }
}
