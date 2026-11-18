package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerService.InvestedStatement;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionTypeLabels;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Per-client transaction history with running balance for a trust account.
 *
 * <p>648F (§16.8.5): for a client with section 86(4) investments placed from the account, {@code
 * summary.invested} carries the period's invested block ({@link
 * ClientLedgerService#getClientInvestedStatement}: opening / closing invested balance, capital
 * placed, interest gross / Fund's 5% / net, returned to trust). The report body prints it from the
 * trust-reports v4 definition (650C); the rows and columns are unchanged.
 */
@Component
public class ClientLedgerStatementQuery implements ReportQuery {

  private final TrustTransactionRepository transactionRepository;
  private final TrustLedgerQueryRepository ledgerQueries;
  private final ClientLedgerService clientLedgerService;

  public ClientLedgerStatementQuery(
      TrustTransactionRepository transactionRepository,
      TrustLedgerQueryRepository ledgerQueries,
      ClientLedgerService clientLedgerService) {
    this.transactionRepository = transactionRepository;
    this.ledgerQueries = ledgerQueries;
    this.clientLedgerService = clientLedgerService;
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
      List<Map<String, Object>> rows,
      BigDecimal openingBalance,
      BigDecimal closingBalance,
      InvestedStatement invested) {}

  private QueryResult buildRowsAndBalances(Map<String, Object> parameters) {
    var trustAccountId = ReportParamUtils.requireUuid(parameters, "trust_account_id");
    var customerId = ReportParamUtils.requireUuid(parameters, "customer_id");
    var dateFrom = ReportParamUtils.requireDate(parameters, "dateFrom");
    var dateTo = ReportParamUtils.requireDate(parameters, "dateTo");

    // Calculate opening balance (all transactions before dateFrom)
    var openingBalance =
        ledgerQueries.clientBalance(trustAccountId, customerId, dateFrom.minusDays(1));

    // Get transactions in the date range
    var transactions =
        transactionRepository.findForStatement(customerId, trustAccountId, dateFrom, dateTo);

    BigDecimal runningBalance = openingBalance;
    var rows = new java.util.ArrayList<Map<String, Object>>();

    for (var tx : transactions) {
      var row = new LinkedHashMap<String, Object>();
      row.put("date", tx.getTransactionDate().toString());
      row.put("reference", tx.getReference());
      row.put("type", TrustTransactionTypeLabels.firmLabel(tx.getTransactionType()));
      row.put("description", tx.getDescription());

      // Credit or debit by the row's own posted effect (ADR-378), never by type name
      boolean isCredit = tx.getLedgerEffect().signum() > 0;
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

    // 648F: the client's section 86(4) investments placed from this account, for the range. An
    // inverted range has no rows and no invested block, as before 648F (no upstream check rejects
    // it; getClientInvestedStatement would throw)
    var invested =
        dateTo.isBefore(dateFrom)
            ? InvestedStatement.NONE
            : clientLedgerService.getClientInvestedStatement(
                customerId, trustAccountId, dateFrom, dateTo);

    // Closing balance is the final running balance after all transactions
    return new QueryResult(rows, openingBalance, runningBalance, invested);
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
    // A nested map: the firm report screen shows flat summary values only (R-A, 650C prints it)
    if (!queryResult.invested().isEmpty()) {
      var inv = queryResult.invested();
      var invested = new LinkedHashMap<String, Object>();
      invested.put("openingBalance", inv.openingBalance());
      invested.put("capitalPlaced", inv.capitalPlaced());
      invested.put("interestGross", inv.interestGross());
      invested.put("interestFundShare", inv.interestFundShare());
      invested.put("interestNet", inv.interestNet());
      invested.put("returnedToTrust", inv.returnedToTrust());
      invested.put("closingBalance", inv.closingBalance());
      summary.put("invested", invested);
    }
    return summary;
  }
}
