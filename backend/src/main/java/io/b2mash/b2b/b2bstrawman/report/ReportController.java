package io.b2mash.b2b.b2bstrawman.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

  private final ReportService reportService;

  public ReportController(ReportService reportService) {
    this.reportService = reportService;
  }

  @GetMapping("/api/projects/{projectId}/profitability")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectProfitabilityResponse> getProjectProfitability(
      @PathVariable UUID projectId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false, defaultValue = "false") boolean includeProjections) {
    var actor = ActorContext.fromRequestScopes();
    String orgRole = actor.orgRole();
    UUID memberId = actor.memberId();

    var response =
        reportService.getProjectProfitability(projectId, from, to, actor, includeProjections);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/api/customers/{customerId}/profitability")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CustomerProfitabilityResponse> getCustomerProfitability(
      @PathVariable UUID customerId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false, defaultValue = "false") boolean includeProjections) {
    var actor = ActorContext.fromRequestScopes();
    String orgRole = actor.orgRole();
    UUID memberId = actor.memberId();

    var response =
        reportService.getCustomerProfitability(customerId, from, to, actor, includeProjections);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/api/reports/utilization")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<UtilizationResponse> getUtilization(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID memberId) {
    UUID requestingMemberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var response = reportService.getUtilization(from, to, memberId, requestingMemberId, orgRole);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/api/reports/profitability")
  @RequiresCapability("FINANCIAL_VISIBILITY")
  public ResponseEntity<OrgProfitabilityResponse> getOrgProfitability(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID customerId,
      @RequestParam(required = false, defaultValue = "false") boolean includeProjections) {
    var actor = ActorContext.fromRequestScopes();
    String orgRole = actor.orgRole();
    UUID memberId = actor.memberId();

    var response =
        reportService.getOrgProfitability(from, to, customerId, actor, includeProjections);
    return ResponseEntity.ok(response);
  }

  // --- DTOs ---

  public record ProjectionData(
      BigDecimal projectedRevenue, BigDecimal projectedCost, BigDecimal projectedMargin) {}

  public record CurrencyBreakdown(
      String currency,
      BigDecimal totalBillableHours,
      BigDecimal totalNonBillableHours,
      BigDecimal totalHours,
      BigDecimal billableValue,
      BigDecimal costValue,
      BigDecimal margin,
      BigDecimal marginPercent,
      BigDecimal totalExpenseCost,
      BigDecimal totalExpenseRevenue) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ProjectProfitabilityResponse(
      UUID projectId,
      String projectName,
      List<CurrencyBreakdown> currencies,
      ProjectionData projections) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CustomerProfitabilityResponse(
      UUID customerId,
      String customerName,
      List<CurrencyBreakdown> currencies,
      ProjectionData projections) {}

  public record MemberValueBreakdown(
      String currency, BigDecimal billableValue, BigDecimal costValue) {}

  public record MemberUtilizationRecord(
      UUID memberId,
      String memberName,
      BigDecimal totalHours,
      BigDecimal billableHours,
      BigDecimal nonBillableHours,
      BigDecimal utilizationPercent,
      List<MemberValueBreakdown> currencies) {}

  public record UtilizationResponse(
      LocalDate from, LocalDate to, List<MemberUtilizationRecord> members) {}

  public record ProjectProfitabilitySummary(
      UUID projectId,
      String projectName,
      String customerName,
      String currency,
      BigDecimal billableHours,
      BigDecimal billableValue,
      BigDecimal costValue,
      BigDecimal margin,
      BigDecimal marginPercent) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record OrgProfitabilityResponse(
      List<ProjectProfitabilitySummary> projects, ProjectionData projections) {}
}
