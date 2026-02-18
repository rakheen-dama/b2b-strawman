package io.b2mash.b2b.b2bstrawman.report;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.ForbiddenException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.report.ReportController.CurrencyBreakdown;
import io.b2mash.b2b.b2bstrawman.report.ReportController.CustomerProfitabilityResponse;
import io.b2mash.b2b.b2bstrawman.report.ReportController.MemberUtilizationRecord;
import io.b2mash.b2b.b2bstrawman.report.ReportController.MemberValueBreakdown;
import io.b2mash.b2b.b2bstrawman.report.ReportController.OrgProfitabilityResponse;
import io.b2mash.b2b.b2bstrawman.report.ReportController.ProjectProfitabilityResponse;
import io.b2mash.b2b.b2bstrawman.report.ReportController.ProjectProfitabilitySummary;
import io.b2mash.b2b.b2bstrawman.report.ReportController.UtilizationResponse;
import io.b2mash.b2b.b2bstrawman.security.Roles;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final CustomerProjectRepository customerProjectRepository;
  private final ProjectRepository projectRepository;

  public ReportService(
      ReportRepository reportRepository,
      ProjectAccessService projectAccessService,
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      ProjectRepository projectRepository) {
    this.reportRepository = reportRepository;
    this.projectAccessService = projectAccessService;
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
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
            .findById(projectId)
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
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    var revenueList = reportRepository.getCustomerRevenue(customerId, from, to);
    var costList = reportRepository.getCustomerCost(customerId, from, to);

    var currencies = mergeToCurrencyBreakdowns(revenueList, costList);

    log.debug("Customer {} profitability: {} currency breakdown(s)", customerId, currencies.size());

    return new CustomerProfitabilityResponse(customerId, customer.getName(), currencies);
  }

  /**
   * Computes team/self utilization: billable vs total hours per member, with per-member
   * per-currency value breakdown. Self-service: regular members can only query their own data.
   */
  @Transactional(readOnly = true)
  public UtilizationResponse getUtilization(
      LocalDate from, LocalDate to, UUID memberId, UUID requestingMemberId, String orgRole) {

    boolean isAdminOrOwner = Roles.ORG_ADMIN.equals(orgRole) || Roles.ORG_OWNER.equals(orgRole);

    // Self-service restriction: members can only query themselves
    if (!isAdminOrOwner) {
      if (memberId != null && !memberId.equals(requestingMemberId)) {
        throw new ForbiddenException(
            "Insufficient permissions", "Members can only view their own utilization");
      }
      // If memberId is null for a member, restrict to self
      memberId = requestingMemberId;
    }

    // Fetch utilization data
    List<MemberUtilizationProjection> utilizationData;
    if (memberId != null) {
      utilizationData = reportRepository.getMemberUtilization(memberId, from, to);
    } else {
      utilizationData = reportRepository.getMemberUtilization(from, to);
    }

    // Fetch per-member per-currency value breakdown (scoped to single member when applicable)
    List<MemberValueProjection> valueData;
    if (memberId != null) {
      valueData = reportRepository.getMemberBillableValues(memberId, from, to);
    } else {
      valueData = reportRepository.getMemberBillableValues(from, to);
    }
    Map<UUID, List<MemberValueProjection>> valuesByMember =
        valueData.stream().collect(Collectors.groupingBy(MemberValueProjection::getMemberId));

    // Build response records
    List<MemberUtilizationRecord> members =
        utilizationData.stream()
            .map(
                u -> {
                  BigDecimal totalHours = u.getTotalHours();
                  BigDecimal billableHours = u.getBillableHours();
                  BigDecimal utilizationPercent;
                  if (totalHours.compareTo(BigDecimal.ZERO) == 0) {
                    utilizationPercent = BigDecimal.ZERO;
                  } else {
                    utilizationPercent =
                        billableHours
                            .multiply(BigDecimal.valueOf(100))
                            .divide(totalHours, 2, RoundingMode.HALF_UP);
                  }

                  List<MemberValueBreakdown> currencies =
                      valuesByMember.getOrDefault(u.getMemberId(), List.of()).stream()
                          .map(
                              v ->
                                  new MemberValueBreakdown(
                                      v.getCurrency(), v.getBillableValue(), v.getCostValue()))
                          .toList();

                  return new MemberUtilizationRecord(
                      u.getMemberId(),
                      u.getMemberName(),
                      totalHours,
                      billableHours,
                      u.getNonBillableHours(),
                      utilizationPercent,
                      currencies);
                })
            .toList();

    log.debug("Utilization: {} member(s) for date range {} to {}", members.size(), from, to);

    return new UtilizationResponse(from, to, members);
  }

  /**
   * Computes org-level profitability: revenue, cost, and margin per project per currency. Admin or
   * owner only. Optional customerId filter narrows to linked projects.
   */
  @Transactional(readOnly = true)
  public OrgProfitabilityResponse getOrgProfitability(
      LocalDate from, LocalDate to, UUID customerId, UUID memberId, String orgRole) {
    requireAdminOrOwner(orgRole, "Org profitability is only accessible to admins and owners");

    var revenueList = reportRepository.getOrgProjectRevenue(from, to, customerId);
    var costList = reportRepository.getOrgProjectCost(from, to, customerId);

    // Build cost map keyed by "projectId|currency"
    Map<String, OrgCostProjection> costMap = new LinkedHashMap<>();
    for (var c : costList) {
      costMap.put(c.getProjectId() + "|" + c.getCurrency(), c);
    }

    // Build customer name map by enriching with CustomerProjectRepository
    // Include project IDs from both revenue and cost lists
    Map<UUID, String> customerNameByProject = new LinkedHashMap<>();
    var revenueProjectIds =
        revenueList.stream().map(OrgRevenueProjection::getProjectId).distinct().toList();
    var costProjectIds = costList.stream().map(OrgCostProjection::getProjectId).distinct().toList();
    var allProjectIds =
        java.util.stream.Stream.concat(revenueProjectIds.stream(), costProjectIds.stream())
            .distinct()
            .toList();
    for (var projectId : allProjectIds) {
      var links = customerProjectRepository.findByProjectId(projectId);
      if (!links.isEmpty()) {
        var customer = customerRepository.findById(links.getFirst().getCustomerId());
        customer.ifPresent(c -> customerNameByProject.put(projectId, c.getName()));
      }
    }

    // Merge revenue and cost, compute margin
    List<ProjectProfitabilitySummary> projects = new ArrayList<>();
    for (var revenue : revenueList) {
      String key = revenue.getProjectId() + "|" + revenue.getCurrency();
      var cost = costMap.get(key);

      BigDecimal costValue = cost != null ? cost.getCostValue() : null;
      BigDecimal billableValue = revenue.getBillableValue();
      BigDecimal margin = computeMargin(billableValue, costValue);
      BigDecimal marginPercent = computeMarginPercent(margin, billableValue);
      String customerName = customerNameByProject.get(revenue.getProjectId());

      projects.add(
          new ProjectProfitabilitySummary(
              revenue.getProjectId(),
              revenue.getProjectName(),
              customerName,
              revenue.getCurrency(),
              revenue.getBillableHours(),
              billableValue,
              costValue,
              margin,
              marginPercent));
    }

    // Also add cost-only entries (projects with cost but no revenue in query)
    for (var entry : costMap.entrySet()) {
      String key = entry.getKey();
      boolean alreadyProcessed =
          revenueList.stream()
              .anyMatch(r -> (r.getProjectId() + "|" + r.getCurrency()).equals(key));
      if (!alreadyProcessed) {
        var cost = entry.getValue();
        String customerName = customerNameByProject.get(cost.getProjectId());
        projects.add(
            new ProjectProfitabilitySummary(
                cost.getProjectId(),
                cost.getProjectName(),
                customerName,
                cost.getCurrency(),
                BigDecimal.ZERO,
                null,
                cost.getCostValue(),
                null,
                null));
      }
    }

    // Sort by margin DESC, nulls last
    projects.sort(
        Comparator.comparing(
            ProjectProfitabilitySummary::margin, Comparator.nullsLast(Comparator.reverseOrder())));

    log.debug("Org profitability: {} project-currency entries", projects.size());

    return new OrgProfitabilityResponse(projects);
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
    requireAdminOrOwner(orgRole, "This resource is only accessible to admins and owners");
  }

  private void requireAdminOrOwner(String orgRole, String detail) {
    if (!Roles.ORG_ADMIN.equals(orgRole) && !Roles.ORG_OWNER.equals(orgRole)) {
      throw new ForbiddenException("Insufficient permissions", detail);
    }
  }
}
