package io.b2mash.b2b.b2bstrawman.report;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
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
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var response = reportService.getProjectProfitability(projectId, from, to, memberId, orgRole);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/api/customers/{customerId}/profitability")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CustomerProfitabilityResponse> getCustomerProfitability(
      @PathVariable UUID customerId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var response = reportService.getCustomerProfitability(customerId, from, to, memberId, orgRole);
    return ResponseEntity.ok(response);
  }

  // --- DTOs ---

  public record CurrencyBreakdown(
      String currency,
      BigDecimal totalBillableHours,
      BigDecimal totalNonBillableHours,
      BigDecimal totalHours,
      BigDecimal billableValue,
      BigDecimal costValue,
      BigDecimal margin,
      BigDecimal marginPercent) {}

  public record ProjectProfitabilityResponse(
      UUID projectId, String projectName, List<CurrencyBreakdown> currencies) {}

  public record CustomerProfitabilityResponse(
      UUID customerId, String customerName, List<CurrencyBreakdown> currencies) {}
}
