package io.b2mash.b2b.b2bstrawman.report;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.report.ReportController.CurrencyBreakdown;
import io.b2mash.b2b.b2bstrawman.report.ReportController.CustomerProfitabilityResponse;
import io.b2mash.b2b.b2bstrawman.report.ReportController.ProjectProfitabilityResponse;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

  private static final Logger log = LoggerFactory.getLogger(ReportService.class);

  private final ReportRepository reportRepository;
  private final ProjectAccessService projectAccessService;
  private final CustomerRepository customerRepository;
  private final ProjectRepository projectRepository;

  public ReportService(
      ReportRepository reportRepository,
      ProjectAccessService projectAccessService,
      CustomerRepository customerRepository,
      ProjectRepository projectRepository) {
    this.reportRepository = reportRepository;
    this.projectAccessService = projectAccessService;
    this.customerRepository = customerRepository;
    this.projectRepository = projectRepository;
  }

  /**
   * Computes project profitability: revenue, cost, and margin per currency. Project lead, admin, or
   * owner access required (via ProjectAccessService).
   */
  @Transactional(readOnly = true)
  public ProjectProfitabilityResponse getProjectProfitability(
      UUID projectId, LocalDate from, LocalDate to, UUID memberId, String orgRole) {
    projectAccessService.requireViewAccess(projectId, memberId, orgRole);

    var project =
        projectRepository
            .findOneById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

    var revenueList = reportRepository.getProjectRevenue(projectId, from, to);
    var costList = reportRepository.getProjectCost(projectId, from, to);

    var currencies = mergeToCurrencyBreakdowns(revenueList, costList);

    log.debug("Project {} profitability: {} currency breakdown(s)", projectId, currencies.size());

    return new ProjectProfitabilityResponse(projectId, project.getName(), currencies);
  }

  /**
   * Computes customer profitability: revenue, cost, and margin per currency aggregated across all
   * linked projects. Admin or owner access required.
   */
  @Transactional(readOnly = true)
  public CustomerProfitabilityResponse getCustomerProfitability(
      UUID customerId, LocalDate from, LocalDate to, UUID memberId, String orgRole) {
    requireAdminOrOwner(orgRole);

    var customer =
        customerRepository
            .findOneById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    var revenueList = reportRepository.getCustomerRevenue(customerId, from, to);
    var costList = reportRepository.getCustomerCost(customerId, from, to);

    var currencies = mergeToCurrencyBreakdowns(revenueList, costList);

    log.debug("Customer {} profitability: {} currency breakdown(s)", customerId, currencies.size());

    return new CustomerProfitabilityResponse(customerId, customer.getName(), currencies);
  }

  /**
   * Merges revenue and cost results by currency into CurrencyBreakdown records. Margin is only
   * computed when both revenue and cost exist for the same currency.
   */
  private List<CurrencyBreakdown> mergeToCurrencyBreakdowns(
      List<RevenueCurrencyProjection> revenueList, List<CostCurrencyProjection> costList) {
    // Build a map of currency -> revenue data
    Map<String, RevenueCurrencyProjection> revenueMap = new LinkedHashMap<>();
    for (var r : revenueList) {
      revenueMap.put(r.getCurrency(), r);
    }

    // Build a map of currency -> cost data
    Map<String, CostCurrencyProjection> costMap = new LinkedHashMap<>();
    for (var c : costList) {
      costMap.put(c.getCurrency(), c);
    }

    // Collect all unique currencies (revenue + cost)
    Map<String, CurrencyBreakdown> result = new LinkedHashMap<>();

    // Process revenue currencies first
    for (var entry : revenueMap.entrySet()) {
      String currency = entry.getKey();
      var revenue = entry.getValue();
      var cost = costMap.get(currency);

      BigDecimal costValue = cost != null ? cost.getCostValue() : null;
      BigDecimal billableValue = revenue.getBillableValue();
      BigDecimal margin = computeMargin(billableValue, costValue);
      BigDecimal marginPercent = computeMarginPercent(margin, billableValue);

      result.put(
          currency,
          new CurrencyBreakdown(
              currency,
              revenue.getTotalBillableHours(),
              revenue.getTotalNonBillableHours(),
              revenue.getTotalHours(),
              billableValue,
              costValue,
              margin,
              marginPercent));
    }

    // Process cost-only currencies (no matching revenue)
    for (var entry : costMap.entrySet()) {
      String currency = entry.getKey();
      if (result.containsKey(currency)) {
        continue; // Already processed with revenue
      }
      var cost = entry.getValue();
      result.put(
          currency,
          new CurrencyBreakdown(
              currency,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              BigDecimal.ZERO,
              null,
              cost.getCostValue(),
              null,
              null));
    }

    return new ArrayList<>(result.values());
  }

  /**
   * Computes margin = billableValue - costValue. Returns null if either value is null (per
   * ADR-043).
   */
  private BigDecimal computeMargin(BigDecimal billableValue, BigDecimal costValue) {
    if (billableValue == null || costValue == null) {
      return null;
    }
    return billableValue.subtract(costValue);
  }

  /**
   * Computes marginPercent = margin / billableValue * 100. Returns null if margin is null or
   * billableValue is zero.
   */
  private BigDecimal computeMarginPercent(BigDecimal margin, BigDecimal billableValue) {
    if (margin == null || billableValue == null || billableValue.compareTo(BigDecimal.ZERO) == 0) {
      return null;
    }
    return margin.multiply(BigDecimal.valueOf(100)).divide(billableValue, 2, RoundingMode.HALF_UP);
  }

  private void requireAdminOrOwner(String orgRole) {
    if (!Roles.ORG_ADMIN.equals(orgRole) && !Roles.ORG_OWNER.equals(orgRole)) {
      throw new ForbiddenException(
          "Insufficient permissions",
          "Customer profitability is only accessible to admins and owners");
    }
  }
}
