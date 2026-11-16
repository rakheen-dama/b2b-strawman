package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.LedgerTotals;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.ClientBalanceRow;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustLedgerQueryRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Point-in-time client balances for a trust account. Driven by the clients with posted rows on the
 * account (ADR-378 — a client with transactions and no lock anchor can no longer be missing); each
 * balance is derived as at {@code asOfDate} (or every posted row when absent). The columns are
 * unchanged (§16.8.6): {@code totalInterestCredited} stays, always zero, until the report
 * definition is edited (650C).
 */
@Component
public class ClientTrustBalancesQuery implements ReportQuery {

  private final TrustLedgerQueryRepository ledgerQueries;
  private final CustomerRepository customerRepository;
  private final TrustTransactionRepository transactionRepository;

  public ClientTrustBalancesQuery(
      TrustLedgerQueryRepository ledgerQueries,
      CustomerRepository customerRepository,
      TrustTransactionRepository transactionRepository) {
    this.ledgerQueries = ledgerQueries;
    this.customerRepository = customerRepository;
    this.transactionRepository = transactionRepository;
  }

  @Override
  public String getSlug() {
    return "client-trust-balances";
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
    var asOfDate = ReportParamUtils.parseDate(parameters, "asOfDate");

    // Every client with a posted row on the account (membership is date-free), balance as at
    // asOfDate
    List<ClientBalanceRow> clients = ledgerQueries.clientBalances(trustAccountId, asOfDate);

    // Batch-load customer names
    var customerIds =
        clients.stream().map(ClientBalanceRow::getCustomerId).collect(Collectors.toSet());
    var customerNames =
        customerRepository.findByIdIn(customerIds).stream()
            .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

    // G89-F13: the totals come from the posted ledger (one batch query), not the card counters,
    // and are cut at the same date as the balance so the row reconciles
    Map<UUID, LedgerTotals> totals =
        customerIds.isEmpty()
            ? Map.of()
            : LedgerTotals.byCustomer(
                asOfDate != null
                    ? transactionRepository.sumPostedByCustomerAndTypeAsOf(
                        trustAccountId, customerIds, asOfDate)
                    : transactionRepository.sumPostedByCustomerAndType(
                        trustAccountId, customerIds));

    return clients.stream()
        .map(
            client -> {
              var row = new LinkedHashMap<String, Object>();
              row.put(
                  "clientName",
                  customerNames.getOrDefault(client.getCustomerId(), "Unknown Client"));
              row.put("customerId", client.getCustomerId());
              row.put("balance", client.getBalance());

              var clientTotals = totals.getOrDefault(client.getCustomerId(), LedgerTotals.ZERO);
              row.put("totalDeposits", clientTotals.deposits());
              row.put("totalPayments", clientTotals.payments());
              row.put("totalFeeTransfers", clientTotals.feeTransfers());
              // Column kept (§16.8.6, definition edits are 650C); no interest is posted any more
              row.put("totalInterestCredited", BigDecimal.ZERO);
              row.put(
                  "lastTransactionDate",
                  client.getLastTransactionDate() != null
                      ? client.getLastTransactionDate().toString()
                      : null);
              row.put("asOfDate", asOfDate != null ? asOfDate.toString() : null);
              return (Map<String, Object>) row;
            })
        .toList();
  }

  private Map<String, Object> computeSummary(List<Map<String, Object>> rows) {
    var summary = new LinkedHashMap<String, Object>();
    BigDecimal totalBalance = BigDecimal.ZERO;

    for (var row : rows) {
      totalBalance = totalBalance.add((BigDecimal) row.get("balance"));
    }

    summary.put("totalTrustBalance", totalBalance);
    summary.put("clientCount", rows.size());
    return summary;
  }
}
