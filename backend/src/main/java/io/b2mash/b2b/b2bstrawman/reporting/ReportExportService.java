package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates report export (PDF, CSV) by coordinating definition lookup, execution, rendering,
 * and audit logging. Keeps the controller as a thin HTTP adapter.
 *
 * <p>The four entry points that run a report query ({@link #execute}, {@link #renderPreviewHtml},
 * {@link #exportAsPdf}, {@link #exportAsCsv}) open the transaction the query joins, read-only at
 * {@code REPEATABLE_READ} (Phase 92 650C, CR-4): a report reads in several statements (the trust
 * schedule's movements, then its ledger balances; the Section 35 pack's seven parts), and one
 * snapshot keeps a commit landing between them from making a report disagree with itself — the
 * {@code TrustIntegrityService} / {@code TrustMonthlyListService} precedent (648E R-2). An
 * isolation on {@code ReportExecutionService} or a query bean would be ignored: the transaction is
 * already open. Safe here where the 649C ruling declined it on write paths: these transactions do
 * no writes, and in PostgreSQL a read-only transaction cannot fail with a serialization error
 * (40001) at {@code REPEATABLE_READ}.
 *
 * <p>Audit rows ({@code REPORT_GENERATED} on execute, {@code REPORT_EXPORTED} on PDF and CSV) go
 * through {@link ReportAuditRecorder}: written after this transaction commits, in a transaction of
 * their own, so this one stays read-only (Phase 92 FU-12, 650C F-650C-1 — a row joined to it was
 * never flushed). A preview ({@link #renderPreviewHtml}) is not audited.
 */
@Service
public class ReportExportService {

  private static final Map<String, String> CATEGORY_LABELS =
      Map.of(
          "TIME_ATTENDANCE", "Time & Attendance",
          "FINANCIAL", "Financial",
          "PROJECT", "Matter");

  private final ReportDefinitionRepository reportDefinitionRepository;
  private final ReportExecutionService reportExecutionService;
  private final ReportRenderingService reportRenderingService;
  private final ReportAuditRecorder reportAuditRecorder;

  public ReportExportService(
      ReportDefinitionRepository reportDefinitionRepository,
      ReportExecutionService reportExecutionService,
      ReportRenderingService reportRenderingService,
      ReportAuditRecorder reportAuditRecorder) {
    this.reportDefinitionRepository = reportDefinitionRepository;
    this.reportExecutionService = reportExecutionService;
    this.reportRenderingService = reportRenderingService;
    this.reportAuditRecorder = reportAuditRecorder;
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
                            .filter(def -> reportExecutionService.canAccess(def.getSlug()))
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
    reportExecutionService.assertCanAccess(slug);
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
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReportExecutionResponse execute(
      String slug, Map<String, Object> parameters, Pageable pageable) {
    return reportExecutionService.execute(slug, parameters, pageable);
  }

  /** Render an HTML preview for a report. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public String renderPreviewHtml(String slug, Map<String, Object> parameters) {
    return reportRenderingService.renderPreviewHtml(slug, parameters);
  }

  /** Export report as PDF: fetch definition, execute query, render PDF, log audit event. */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PdfExportResult exportAsPdf(String slug, Map<String, Object> parameters) {
    var definition =
        reportDefinitionRepository
            .findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException("ReportDefinition", slug));

    var result = reportExecutionService.executeForExport(slug, parameters);
    byte[] pdfBytes = reportRenderingService.renderPdf(definition, result, parameters);

    reportAuditRecorder.recordAfterCommit(
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
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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
      // No transaction is open here (exportAsCsv has returned): written now, in its own.
      reportAuditRecorder.recordAfterCommit(
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
