package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportService.TakeOnReportResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnReportService.TakeOnReportSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The trust take-on reports (Phase 93 657C, §17.4.2, P-12): the list and the snapshot as JSON.
 * {@code VIEW_TRUST} — the gate of the Rule 54.15 monthly lists ({@code
 * TrustMonthlyListController}), never {@code ReportingController}'s {@code FINANCIAL_VISIBILITY}
 * (the Phase 92 R-2 defect, X1). The PDF and CSV downloads are 657D's.
 */
@RestController
public class TakeOnReportController {

  private final TakeOnReportService reportService;

  public TakeOnReportController(TakeOnReportService reportService) {
    this.reportService = reportService;
  }

  @GetMapping("/api/take-on/reports")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<List<TakeOnReportSummaryResponse>> list(
      @RequestParam(required = false) UUID trustAccountId) {
    return ResponseEntity.ok(reportService.list(trustAccountId));
  }

  @GetMapping("/api/take-on/reports/{reportId}")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<TakeOnReportResponse> get(@PathVariable UUID reportId) {
    return ResponseEntity.ok(reportService.get(reportId));
  }
}
