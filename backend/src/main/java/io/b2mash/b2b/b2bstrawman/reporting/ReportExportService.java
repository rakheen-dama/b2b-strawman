package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates report export (PDF, CSV) by coordinating definition lookup, execution, rendering,
 * and audit logging. Keeps the controller as a thin HTTP adapter.
 */
@Service
public class ReportExportService {

  private static final Map<String, String> CATEGORY_LABELS =
      Map.of(
          "TIME_ATTENDANCE", "Time & Attendance",
          "FINANCIAL", "Financial",
          "PROJECT", "Project");

  private final ReportDefinitionRepository reportDefinitionRepository;
  private final ReportExecutionService reportExecutionService;
  private final ReportRenderingService reportRenderingService;
  private final AuditService auditService;

  public ReportExportService(
      ReportDefinitionRepository reportDefinitionRepository,
      ReportExecutionService reportExecutionService,
      ReportRenderingService reportRenderingService,
      AuditService auditService) {
    this.reportDefinitionRepository = reportDefinitionRepository;
    this.reportExecutionService = reportExecutionService;
    this.reportRenderingService = reportRenderingService;
    this.auditService = auditService;
  }

  /** List all report definitions grouped by category. */
  @Transactional(readOnly = true)
  public CategorizedReportsResponse listCategorized() {
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

    return new CategorizedReportsResponse(categories);
  }

  /** Get a single report definition by slug. */
  @Transactional(readOnly = true)
  public ReportDetailResponse getBySlug(String slug) {
    var definition =
        reportDefinitionRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", slug));

    return new ReportDetailResponse(
        definition.getSlug(),
        definition.getName(),
        definition.getDescription(),
        definition.getCategory(),
        definition.getParameterSchema(),
        definition.getColumnDefinitions(),
        definition.isSystem());
  }

  /** Execute a report query with pagination. */
  @Transactional(readOnly = true)
  public ReportExecutionResponse execute(
      String slug, Map<String, Object> parameters, Pageable pageable) {
    return reportExecutionService.execute(slug, parameters, pageable);
  }

  /** Render an HTML preview for a report. */
  @Transactional(readOnly = true)
  public String renderPreviewHtml(String slug, Map<String, Object> parameters) {
    return reportRenderingService.renderPreviewHtml(slug, parameters);
  }

  /** Export report as PDF: fetch definition, execute query, render PDF, log audit event. */
  @Transactional(readOnly = true)
  public PdfExportResult exportAsPdf(String slug, Map<String, Object> parameters) {
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
    return new PdfExportResult(pdfBytes, filename);
  }

  /**
   * Export report as CSV: fetch definition, execute query, write CSV to output stream, log audit
   * event.
   */
  @Transactional(readOnly = true)
  public CsvExportResult exportAsCsv(String slug, Map<String, Object> parameters) {
    var definition =
        reportDefinitionRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", slug));

    var result = reportExecutionService.executeForExport(slug, parameters);
    String filename = reportRenderingService.generateFilename(slug, parameters, "csv");

    return new CsvExportResult(definition, result, parameters, filename);
  }

  /**
   * Write CSV content to the given output stream and log the audit event. Separated from {@link
   * #exportAsCsv} because the controller needs to set HTTP headers before writing to the response
   * stream.
   */
  public void writeCsvAndAudit(CsvExportResult csvResult, OutputStream outputStream)
      throws IOException {
    try {
      reportRenderingService.writeCsv(
          csvResult.definition(), csvResult.result(), csvResult.parameters(), outputStream);
    } finally {
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("REPORT_EXPORTED")
              .entityType("REPORT")
              .entityId(csvResult.definition().getId())
              .details(
                  Map.of(
                      "slug",
                      csvResult.definition().getSlug(),
                      "parameters",
                      csvResult.parameters(),
                      "format",
                      "csv",
                      "rowCount",
                      csvResult.result().totalElements()))
              .build());
    }
  }

  public record PdfExportResult(byte[] bytes, String filename) {}

  public record CsvExportResult(
      ReportDefinition definition,
      ReportResult result,
      Map<String, Object> parameters,
      String filename) {}

  // --- Response DTOs (owned by service, used by controller) ---

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
