package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes a report audit row ({@code REPORT_GENERATED}, {@code REPORT_EXPORTED}) in a writable
 * transaction of its own, best effort (Phase 92 FU-12, 650C F-650C-1).
 *
 * <p>Why a transaction of its own: every report entry point in {@link ReportExportService} runs in
 * a read-only {@code REPEATABLE_READ} transaction, so that a report reads on one snapshot (650C
 * D-38; FU-3 D-1 keeps it that way). {@link AuditService#log} joins its caller's transaction;
 * inside a read-only one (Hibernate flush mode MANUAL) the row was never flushed. So the entry
 * points return the row ({@link AuditedReport}) and it is written here, after they have returned:
 * by {@link ReportRunService} (execute, PDF) or by {@link ReportExportService#writeCsvAndAudit}
 * (CSV, after {@link ReportExportService#exportAsCsv} has returned). By then the report transaction
 * has committed and released its connection, so the write holds one pooled connection ({@code
 * ReportAuditConnectionBudgetIntegrationTest}). {@code AuditService.log}'s propagation is
 * unchanged: every other audit row still commits or rolls back with its business transaction.
 *
 * <p>{@code REQUIRES_NEW}, so the write never joins a caller's transaction. Best effort: the report
 * has already been produced, so a failing write does not fail it; it is logged at ERROR with the
 * row's fields, so the gap is visible and the row can be reconstructed.
 *
 * <p>The record is built on the request thread, so the actor, IP and user agent are the caller's.
 * The tenant carries over: the new connection is checked out through the multitenant connection
 * provider, which sets {@code search_path} from {@code RequestScopes.TENANT_ID}, still bound on
 * this thread; {@code AuditService.log} resolves {@code actor_name} in that tenant.
 */
@Component
public class ReportAuditRecorder {

  private static final Logger log = LoggerFactory.getLogger(ReportAuditRecorder.class);

  private final AuditService auditService;
  private final TransactionTemplate auditTransaction;

  public ReportAuditRecorder(
      AuditService auditService, PlatformTransactionManager transactionManager) {
    this.auditService = auditService;
    var template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    template.setName("ReportAuditRecorder");
    this.auditTransaction = template;
  }

  /** Writes {@code record} now, in a new transaction; a failure is logged at ERROR, not thrown. */
  public void record(AuditEventRecord record) {
    try {
      auditTransaction.executeWithoutResult(status -> auditService.log(record));
    } catch (RuntimeException e) {
      log.error(
          "Report audit row NOT recorded: type={}, report={}, actor={}/{}, source={}, ip={},"
              + " userAgent={}, details={}",
          record.eventType(),
          record.entityId(),
          record.actorType(),
          record.actorId(),
          record.source(),
          record.ipAddress(),
          record.userAgent(),
          record.details(),
          e);
    }
  }
}
