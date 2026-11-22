package io.b2mash.b2b.b2bstrawman.reporting;

import java.util.Map;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * The audited report endpoints (execute, PDF): runs a report through its transactional entry point
 * in {@link ReportExportService} and then writes the audit row the entry point returned (Phase 92
 * FU-12).
 *
 * <p>The entry point is called through its proxy (never a self-invocation), so it starts and ends
 * its own read-only {@code REPEATABLE_READ} transaction (FU-3 D-1). A normal return means that
 * transaction has committed and released its connection, so the audit write holds one pooled
 * connection. A rollback, or a failed commit, throws: nothing is written.
 *
 * <p><strong>Deliberately not {@code @Transactional}.</strong> An annotation here would make the
 * entry point join this transaction (no longer its own snapshot), would write the row before the
 * report's outcome is known, and would hold two connections ({@code
 * ReportAuditConnectionBudgetIntegrationTest} guards it).
 *
 * <p>CSV needs no wrapper: its row is written in {@link ReportExportService#writeCsvAndAudit},
 * after {@link ReportExportService#exportAsCsv} has returned, with no transaction open.
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

  /** Executes a report query with pagination, then writes {@code REPORT_GENERATED}. */
  public ReportExecutionResponse execute(
      String slug, Map<String, Object> parameters, Pageable pageable) {
    return recorded(reportExportService.execute(slug, parameters, pageable));
  }

  /** Exports a report as PDF, then writes {@code REPORT_EXPORTED}. */
  public ReportExportService.PdfExportResult exportAsPdf(
      String slug, Map<String, Object> parameters) {
    return recorded(reportExportService.exportAsPdf(slug, parameters));
  }

  private <T> T recorded(AuditedReport<T> report) {
    reportAuditRecorder.record(report.audit());
    return report.result();
  }
}
