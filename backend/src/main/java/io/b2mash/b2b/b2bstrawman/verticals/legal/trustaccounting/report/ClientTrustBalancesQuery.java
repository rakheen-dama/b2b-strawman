package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCard;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.ledger.ClientLedgerCardRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.transaction.TrustTransactionRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Point-in-time client balances for a trust account. */
@Component
public class ClientTrustBalancesQuery implements ReportQuery {

  private final ClientLedgerCardRepository ledgerCardRepository;
  private final CustomerRepository customerRepository;
  private final TrustTransactionRepository transactionRepository;

  public ClientTrustBalancesQuery(
      ClientLedgerCardRepository ledgerCardRepository,
      CustomerRepository customerRepository,
      TrustTransactionRepository transactionRepository) {
    this.ledgerCardRepository = ledgerCardRepository;
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

    // Fetch all ledger cards for this trust account
    var ledgerCards =
        ledgerCardRepository.findByTrustAccountId(trustAccountId, Pageable.unpaged()).getContent();

    // Batch-load customer names
    var customerIds =
        ledgerCards.stream().map(ClientLedgerCard::getCustomerId).collect(Collectors.toSet());
    var customerNames =
        customerRepository.findByIdIn(customerIds).stream()
            .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

    return ledgerCards.stream()
        .map(
            card -> {
              var row = new LinkedHashMap<String, Object>();
              row.put(
                  "clientName", customerNames.getOrDefault(card.getCustomerId(), "Unknown Client"));
              row.put("customerId", card.getCustomerId());

              // When asOfDate is provided, compute historical balance from transactions
              BigDecimal balance =
                  asOfDate != null
                      ? transactionRepository.calculateClientBalanceAsOfDate(
                          card.getCustomerId(), trustAccountId, asOfDate)
                      : card.getBalance();
              row.put("balance", balance);

              row.put("totalDeposits", card.getTotalDeposits());
              row.put("totalPayments", card.getTotalPayments());
              row.put("totalFeeTransfers", card.getTotalFeeTransfers());
              row.put("totalInterestCredited", card.getTotalInterestCredited());
              row.put(
                  "lastTransactionDate",
                  card.getLastTransactionDate() != null
                      ? card.getLastTransactionDate().toString()
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
