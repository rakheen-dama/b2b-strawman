package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestment;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.investment.TrustInvestmentRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Investment list with status, principal, interest earned for a trust account. */
@Component
public class InvestmentRegisterQuery implements ReportQuery {

  private final TrustInvestmentRepository investmentRepository;
  private final CustomerRepository customerRepository;

  public InvestmentRegisterQuery(
      TrustInvestmentRepository investmentRepository, CustomerRepository customerRepository) {
    this.investmentRepository = investmentRepository;
    this.customerRepository = customerRepository;
  }

  @Override
  public String getSlug() {
    return "investment-register";
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
    var trustAccountId = ReportParamUtils.parseUuid(parameters, "trust_account_id");
    var statusFilter = ReportParamUtils.parseString(parameters, "status");

    var investments =
        investmentRepository
            .findByTrustAccountIdOrderByDepositDateDesc(trustAccountId, Pageable.unpaged())
            .getContent();

    // Filter by status if provided
    if (statusFilter != null) {
      investments =
          investments.stream().filter(inv -> statusFilter.equals(inv.getStatus())).toList();
    }

    // Batch-load customer names
    var customerIds =
        investments.stream().map(TrustInvestment::getCustomerId).collect(Collectors.toSet());
    var customerNames =
        customerRepository.findByIdIn(customerIds).stream()
            .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

    return investments.stream()
        .map(
            inv -> {
              var row = new LinkedHashMap<String, Object>();
              row.put(
                  "clientName", customerNames.getOrDefault(inv.getCustomerId(), "Unknown Client"));
              row.put("institution", inv.getInstitution());
              row.put("accountNumber", inv.getAccountNumber());
              row.put("principal", inv.getPrincipal());
              row.put("interestRate", inv.getInterestRate());
              row.put("depositDate", inv.getDepositDate().toString());
              row.put(
                  "maturityDate",
                  inv.getMaturityDate() != null ? inv.getMaturityDate().toString() : null);
              row.put("interestEarned", inv.getInterestEarned());
              row.put("status", inv.getStatus());
              return (Map<String, Object>) row;
            })
        .toList();
  }

  private Map<String, Object> computeSummary(List<Map<String, Object>> rows) {
    var summary = new LinkedHashMap<String, Object>();
    BigDecimal totalPrincipal = BigDecimal.ZERO;
    BigDecimal totalInterestEarned = BigDecimal.ZERO;
    int activeCount = 0;

    for (var row : rows) {
      totalPrincipal = totalPrincipal.add((BigDecimal) row.get("principal"));
      totalInterestEarned = totalInterestEarned.add((BigDecimal) row.get("interestEarned"));
      if ("ACTIVE".equals(row.get("status"))) {
        activeCount++;
      }
    }

    summary.put("totalPrincipal", totalPrincipal);
    summary.put("totalInterestEarned", totalInterestEarned);
    summary.put("activeCount", activeCount);
    summary.put("totalCount", rows.size());
    return summary;
  }
}
