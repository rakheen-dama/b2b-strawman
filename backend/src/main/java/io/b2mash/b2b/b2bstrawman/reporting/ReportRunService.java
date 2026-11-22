package io.b2mash.b2b.b2bstrawman.reporting;

import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * The audited report endpoints: runs a report through its transactional entry point in {@link
 * ReportExportService} and writes the report's audit row after that transaction has completed
 * (Phase 92 FU-12).
 *
 * <p><strong>Deliberately not {@code @Transactional}.</strong> The entry point must start its own
 * read-only {@code REPEATABLE_READ} transaction (FU-3 D-1), and the audit row must be written after
 * it has released its connection — so the audit write needs a caller outside that transaction, and
 * a call through the {@code ReportExportService} proxy (a self-invocation would bypass it). An
 * annotation here would make the entry point join this transaction and bring back two pooled
 * connections per report ({@code ReportAuditConnectionBudgetIntegrationTest} guards it).
 *
 * <p>CSV needs no wrapper: its audit row is written in {@link
 * ReportExportService#writeCsvAndAudit}, after {@link ReportExportService#exportAsCsv} has
 * returned, with no transaction open.
 */
@Service
public class ReportRunService {

  private final ReportExportService reportExportService;
  private final ReportAuditRecorder reportAuditRecorder;

  public ReportRunService(
      ReportExportService reportExportService, ReportAuditRecorder reportAuditRecorder) {
    this.reportExportService = reportExportService;
    this.reportAuditRecorder = reportAuditRecorder;
  }

  /** Executes a report query with pagination; {@code REPORT_GENERATED} is written afterwards. */
  public ReportExecutionResponse execute(
      String slug, Map<String, Object> parameters, Pageable pageable) {
    return reportAuditRecorder.writeAfter(
        () -> reportExportService.execute(slug, parameters, pageable));
  }

  /** Exports a report as PDF; {@code REPORT_EXPORTED} is written afterwards. */
  public ReportExportService.PdfExportResult exportAsPdf(
      String slug, Map<String, Object> parameters) {
    return reportAuditRecorder.writeAfter(() -> reportExportService.exportAsPdf(slug, parameters));
  }
}
