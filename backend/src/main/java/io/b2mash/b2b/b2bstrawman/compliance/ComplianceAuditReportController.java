package io.b2mash.b2b.b2bstrawman.compliance;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compliance/audit-reports")
public class ComplianceAuditReportController {

  private final ComplianceAuditReportService reportService;

  public ComplianceAuditReportController(ComplianceAuditReportService reportService) {
    this.reportService = reportService;
  }

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_MANAGE")
  public ResponseEntity<Page<ComplianceAuditReportResponse>> listReports(Pageable pageable) {
    return ResponseEntity.ok(
        reportService.findReports(pageable).map(r -> ComplianceAuditReportResponse.from(r, null)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_MANAGE")
  public ResponseEntity<ComplianceAuditReportResponse> getReport(@PathVariable UUID id) {
    return ResponseEntity.ok(
        ComplianceAuditReportResponse.from(
            reportService.findReport(id), reportService.findingCounts(id)));
  }

  @GetMapping("/{reportId}/findings")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_MANAGE")
  public ResponseEntity<Page<ComplianceAuditFindingResponse>> listFindings(
      @PathVariable UUID reportId,
      @RequestParam(required = false) String severity,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String status,
      Pageable pageable) {
    return ResponseEntity.ok(
        reportService
            .findFindings(
                reportId,
                new FindingFilterParams(
                    splitParam(severity), splitParam(category), splitParam(status)),
                pageable)
            .map(ComplianceAuditFindingResponse::from));
  }

  @PatchMapping("/{reportId}/findings/{findingId}")
  @PreAuthorize("isAuthenticated()")
  @RequiresCapability("AI_REVIEW")
  public ResponseEntity<ComplianceAuditFindingResponse> updateFindingStatus(
      @PathVariable UUID reportId,
      @PathVariable UUID findingId,
      @RequestBody UpdateFindingStatusRequest request) {
    return ResponseEntity.ok(
        ComplianceAuditFindingResponse.from(
            reportService.updateFindingStatus(
                reportId,
                findingId,
                request.status(),
                request.resolutionNotes(),
                RequestScopes.requireMemberId())));
  }

  private static List<String> splitParam(String param) {
    return param == null || param.isBlank() ? null : Arrays.asList(param.split(","));
  }

  // -- DTOs --

  public record ComplianceAuditReportResponse(
      UUID id,
      String overallGrade,
      String overallAssessment,
      String status,
      Map<String, Object> categoryScores,
      FindingCounts findingCounts,
      Instant publishedAt,
      UUID publishedBy) {
    public static ComplianceAuditReportResponse from(
        ComplianceAuditReport report, FindingCounts counts) {
      return new ComplianceAuditReportResponse(
          report.getId(),
          report.getOverallGrade(),
          report.getOverallAssessment(),
          report.getStatus(),
          report.getCategoryScores(),
          counts,
          report.getPublishedAt(),
          report.getPublishedBy());
    }
  }

  public record ComplianceAuditFindingResponse(
      UUID id,
      String findingId,
      String severity,
      String category,
      String title,
      String description,
      String regulatoryBasis,
      String remediation,
      String entityType,
      UUID entityId,
      String status,
      UUID resolvedBy,
      Instant resolvedAt,
      String resolutionNotes) {
    public static ComplianceAuditFindingResponse from(ComplianceAuditFinding finding) {
      return new ComplianceAuditFindingResponse(
          finding.getId(),
          finding.getFindingId(),
          finding.getSeverity(),
          finding.getCategory(),
          finding.getTitle(),
          finding.getDescription(),
          finding.getRegulatoryBasis(),
          finding.getRemediation(),
          finding.getEntityType(),
          finding.getEntityId(),
          finding.getStatus(),
          finding.getResolvedBy(),
          finding.getResolvedAt(),
          finding.getResolutionNotes());
    }
  }

  public record UpdateFindingStatusRequest(String status, String resolutionNotes) {}
}
