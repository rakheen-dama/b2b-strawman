package io.b2mash.b2b.b2bstrawman.reporting;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report-definitions")
public class ReportingController {

  private final ReportExportService reportExportService;

  public ReportingController(ReportExportService reportExportService) {
    this.reportExportService = reportExportService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ReportExportService.CategorizedReportsResponse> listReportDefinitions() {
    return ResponseEntity.ok(reportExportService.listCategorized());
  }

  @GetMapping("/{slug}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ReportExportService.ReportDetailResponse> getReportDefinition(
      @PathVariable String slug) {
    return ResponseEntity.ok(reportExportService.getBySlug(slug));
  }

  @PostMapping("/{slug}/execute")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ReportExecutionResponse> executeReport(
      @PathVariable String slug, @Valid @RequestBody ExecuteReportRequest request) {
    var pageable = PageRequest.of(request.page(), request.size());
    return ResponseEntity.ok(reportExportService.execute(slug, request.parameters(), pageable));
  }

  @GetMapping("/{slug}/preview")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<String> previewReport(
      @PathVariable String slug, @RequestParam Map<String, Object> parameters) {
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(reportExportService.renderPreviewHtml(slug, parameters));
  }

  @GetMapping("/{slug}/export/pdf")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<byte[]> exportPdf(
      @PathVariable String slug, @RequestParam Map<String, Object> parameters) {
    var result = reportExportService.exportAsPdf(slug, parameters);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header("Content-Disposition", "attachment; filename=\"" + result.filename() + "\"")
        .body(result.bytes());
  }

  @GetMapping("/{slug}/export/csv")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public void exportCsv(
      @PathVariable String slug,
      @RequestParam Map<String, Object> parameters,
      HttpServletResponse response)
      throws IOException {
    var csvResult = reportExportService.exportAsCsv(slug, parameters);
    response.setContentType("text/csv; charset=UTF-8");
    response.setHeader(
        "Content-Disposition", "attachment; filename=\"" + csvResult.filename() + "\"");
    reportExportService.writeCsvAndAudit(csvResult, response.getOutputStream());
  }

  // --- Request DTOs ---

  public record ExecuteReportRequest(
      Map<String, Object> parameters, @Min(0) int page, @Min(1) @Max(500) int size) {}
}
