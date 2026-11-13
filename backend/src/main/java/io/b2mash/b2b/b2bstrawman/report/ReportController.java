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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

  private static final MediaType TEXT_CSV_UTF8 =
      MediaType.parseMediaType("text/csv; charset=UTF-8");

  private final ReportService reportService;
  private final BillingTrendService billingTrendService;

  public ReportController(ReportService reportService, BillingTrendService billingTrendService) {
    this.reportService = reportService;
    this.billingTrendService = billingTrendService;
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

  /**
   * Fees billed (issue month) vs collected (SA payment month) over the last {@code months} SA
   * calendar months (1–24, default 12), org default currency, oldest first, zero-filled (ADR-372).
   * Same gate as the profitability report.
   */
  @GetMapping("/api/reports/billing-trend")
  @RequiresCapability("FINANCIAL_VISIBILITY")
  public ResponseEntity<BillingTrendResponse> getBillingTrend(
      @RequestParam(defaultValue = "12") int months) {
    return ResponseEntity.ok(billingTrendService.getBillingTrend(months));
  }

  @GetMapping("/api/reports/profitability/export/csv")
  @RequiresCapability("FINANCIAL_VISIBILITY")
  public ResponseEntity<byte[]> exportProfitabilityCsv(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      ActorContext actor) {

    var export = reportService.exportProfitabilityCsv(from, to, actor);
    return ResponseEntity.ok()
        .contentType(TEXT_CSV_UTF8)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(export.filename()).build().toString())
        .body(export.bytes());
  }

  // --- DTOs ---

  /** A built CSV file: the download filename and its UTF-8 bytes. */
  public record ProfitabilityCsvExport(String filename, byte[] bytes) {}

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

  /**
   * GET /api/reports/billing-trend (Phase 90 643B, ADR-372): org default currency, oldest first.
   */
  public record BillingTrendResponse(
      String currency, // org default currency; other currencies excluded
      String from, // first month of the window, "2025-12"
      String to, // current month, "2026-11"
      LocalDate asOf, // LocalDate.now(clock), SA business date
      List<BillingTrendPoint> months) {} // exactly `months` entries, zero-filled

  public record BillingTrendPoint(
      String month, // "2026-06"
      BigDecimal billed, // Σ invoices.total (VAT-inclusive) by issue month
      int billedCount,
      BigDecimal collected, // Σ COMPLETED payment_events.amount by SA payment month
      int collectedCount,
      boolean partial) {} // true only for the current month
}
