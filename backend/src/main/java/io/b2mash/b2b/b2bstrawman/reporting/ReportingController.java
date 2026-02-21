package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

  private static final Map<String, String> CATEGORY_LABELS =
      Map.of(
          "TIME_ATTENDANCE", "Time & Attendance",
          "FINANCIAL", "Financial",
          "PROJECT", "Project");

  private final ReportDefinitionRepository reportDefinitionRepository;
  private final ReportExecutionService reportExecutionService;
  private final ReportRenderingService reportRenderingService;
  private final AuditService auditService;

  public ReportingController(
      ReportDefinitionRepository reportDefinitionRepository,
      ReportExecutionService reportExecutionService,
      ReportRenderingService reportRenderingService,
      AuditService auditService) {
    this.reportDefinitionRepository = reportDefinitionRepository;
    this.reportExecutionService = reportExecutionService;
    this.reportRenderingService = reportRenderingService;
    this.auditService = auditService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<CategorizedReportsResponse> listReportDefinitions() {
    var definitions = reportDefinitionRepository.findAllByOrderByCategoryAscSortOrderAsc();

    var categories =
        definitions.stream()
            .collect(
                Collectors.groupingBy(
                    ReportDefinition::getCategory,
                    java.util.LinkedHashMap::new,
                    Collectors.toList()))
            .entrySet()
            .stream()
            .map(
                entry ->
                    new CategoryGroup(
                        entry.getKey(),
                        CATEGORY_LABELS.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue().stream()
                            .map(
                                def ->
                                    new ReportSummary(
                                        def.getSlug(), def.getName(), def.getDescription()))
                            .toList()))
            .toList();

    return ResponseEntity.ok(new CategorizedReportsResponse(categories));
  }

  @GetMapping("/{slug}")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ReportDetailResponse> getReportDefinition(@PathVariable String slug) {
    var definition =
        reportDefinitionRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", slug));

    return ResponseEntity.ok(
        new ReportDetailResponse(
            definition.getSlug(),
            definition.getName(),
            definition.getDescription(),
            definition.getCategory(),
            definition.getParameterSchema(),
            definition.getColumnDefinitions(),
            definition.isSystem()));
  }

  @PostMapping("/{slug}/execute")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ReportExecutionResponse> executeReport(
      @PathVariable String slug, @Valid @RequestBody ExecuteReportRequest request) {
    var pageable = PageRequest.of(request.page(), request.size());
    var response = reportExecutionService.execute(slug, request.parameters(), pageable);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{slug}/preview")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<String> previewReport(
      @PathVariable String slug, @RequestParam Map<String, Object> parameters) {
    var definition =
        reportDefinitionRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", slug));

    var result = reportExecutionService.executeForExport(slug, parameters);
    // Limit to 50 rows for preview
    var limitedRows = result.rows().size() > 50 ? result.rows().subList(0, 50) : result.rows();
    var limitedResult =
        new ReportResult(
            limitedRows, result.summary(), result.totalElements(), result.totalPages());

    String html = reportRenderingService.renderHtml(definition, limitedResult, parameters);
    return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
  }

  @GetMapping("/{slug}/export/pdf")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<byte[]> exportPdf(
      @PathVariable String slug, @RequestParam Map<String, Object> parameters) {
    var definition =
        reportDefinitionRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", slug));

    var result = reportExecutionService.executeForExport(slug, parameters);
    byte[] pdfBytes = reportRenderingService.renderPdf(definition, result, parameters);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("REPORT_EXPORTED")
            .entityType("REPORT")
            .entityId(definition.getId())
            .details(
                Map.of(
                    "slug",
                    slug,
                    "parameters",
                    parameters,
                    "format",
                    "pdf",
                    "rowCount",
                    result.totalElements()))
            .build());

    String filename = reportRenderingService.generateFilename(slug, parameters, "pdf");
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
        .body(pdfBytes);
  }

  // --- Request/Response DTOs ---

  public record ExecuteReportRequest(
      Map<String, Object> parameters, @Min(0) int page, @Min(1) @Max(500) int size) {}

  // --- Response DTOs ---

  public record CategorizedReportsResponse(List<CategoryGroup> categories) {}

  public record CategoryGroup(String category, String label, List<ReportSummary> reports) {}

  public record ReportSummary(String slug, String name, String description) {}

  public record ReportDetailResponse(
      String slug,
      String name,
      String description,
      String category,
      Map<String, Object> parameterSchema,
      Map<String, Object> columnDefinitions,
      boolean isSystem) {}
}
