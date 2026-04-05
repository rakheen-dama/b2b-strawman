package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.report;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.reporting.ReportQuery;
import io.b2mash.b2b.b2bstrawman.reporting.ReportResult;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest.InterestAllocation;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest.InterestAllocationRepository;
import io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest.InterestRunRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Per-client interest allocation breakdown for a specific interest run. */
@Component
public class InterestAllocationReportQuery implements ReportQuery {

  private final InterestRunRepository interestRunRepository;
  private final InterestAllocationRepository allocationRepository;
  private final CustomerRepository customerRepository;

  public InterestAllocationReportQuery(
      InterestRunRepository interestRunRepository,
      InterestAllocationRepository allocationRepository,
      CustomerRepository customerRepository) {
    this.interestRunRepository = interestRunRepository;
    this.allocationRepository = allocationRepository;
    this.customerRepository = customerRepository;
  }

  @Override
  public String getSlug() {
    return "interest-allocation";
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
    var interestRunId = ReportParamUtils.parseUuid(parameters, "interest_run_id");

    // Verify the interest run exists
    interestRunRepository
        .findById(interestRunId)
        .orElseThrow(() -> new ResourceNotFoundException("InterestRun", interestRunId));

    var allocations = allocationRepository.findByInterestRunId(interestRunId);

    // Batch-load customer names
    var customerIds =
        allocations.stream().map(InterestAllocation::getCustomerId).collect(Collectors.toSet());
    var customerNames =
        customerRepository.findByIdIn(customerIds).stream()
            .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));

    return allocations.stream()
        .map(
            alloc -> {
              var row = new LinkedHashMap<String, Object>();
              row.put(
                  "clientName",
                  customerNames.getOrDefault(alloc.getCustomerId(), "Unknown Client"));
              row.put("customerId", alloc.getCustomerId());
              row.put("averageDailyBalance", alloc.getAverageDailyBalance());
              row.put("daysInPeriod", alloc.getDaysInPeriod());
              row.put("grossInterest", alloc.getGrossInterest());
              row.put("lpffShare", alloc.getLpffShare());
              row.put("clientShare", alloc.getClientShare());
              return (Map<String, Object>) row;
            })
        .toList();
  }

  private Map<String, Object> computeSummary(List<Map<String, Object>> rows) {
    var summary = new LinkedHashMap<String, Object>();
    BigDecimal totalInterest = BigDecimal.ZERO;
    BigDecimal totalLpffShare = BigDecimal.ZERO;
    BigDecimal totalClientShare = BigDecimal.ZERO;

    for (var row : rows) {
      totalInterest = totalInterest.add((BigDecimal) row.get("grossInterest"));
      totalLpffShare = totalLpffShare.add((BigDecimal) row.get("lpffShare"));
      totalClientShare = totalClientShare.add((BigDecimal) row.get("clientShare"));
    }

    summary.put("totalInterest", totalInterest);
    summary.put("totalLpffShare", totalLpffShare);
    summary.put("totalClientShare", totalClientShare);
    summary.put("allocationCount", rows.size());
    return summary;
  }
}
