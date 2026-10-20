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

  @GetMapping("/api/matters/{matterId}/profitability")
  public ResponseEntity<MatterProfitabilityResponse> getProjectProfitability(
      @PathVariable UUID matterId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      ActorContext actor) {

    var response = reportService.getProjectProfitability(matterId, from, to, actor);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/api/clients/{clientId}/profitability")
  public ResponseEntity<ClientProfitabilityResponse> getCustomerProfitability(
      @PathVariable UUID clientId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      ActorContext actor) {

    var response = reportService.getCustomerProfitability(clientId, from, to, actor);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/api/reports/utilization")
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
      ActorContext actor) {

    var response = reportService.getOrgProfitability(from, to, customerId, actor);
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
      BigDecimal marginPercent,
      BigDecimal totalExpenseCost,
      BigDecimal totalExpenseRevenue) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MatterProfitabilityResponse(
      UUID matterId, String matterName, List<CurrencyBreakdown> currencies) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ClientProfitabilityResponse(
      UUID clientId, String clientName, List<CurrencyBreakdown> currencies) {}

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

  public record MatterProfitabilitySummary(
      UUID matterId,
      String matterName,
      String clientName,
      String currency,
      BigDecimal billableHours,
      BigDecimal billableValue,
      BigDecimal costValue,
      BigDecimal margin,
      BigDecimal marginPercent) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record OrgProfitabilityResponse(List<MatterProfitabilitySummary> matters) {}
}
