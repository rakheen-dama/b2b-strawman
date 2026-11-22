package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes the report audit rows ({@code REPORT_GENERATED}, {@code REPORT_EXPORTED}) in a transaction
 * of their own, after the report's transaction commits (Phase 92 FU-12, 650C F-650C-1).
 *
 * <p>Why: every report entry point in {@link ReportExportService} runs in a read-only {@code
 * REPEATABLE_READ} transaction, so that a report reads on one snapshot (650C D-38; FU-3 D-1 keeps
 * it that way). {@link AuditService#log} joins its caller's transaction; inside a read-only one
 * (Hibernate flush mode MANUAL) the row was never flushed, so no report run or PDF export was ever
 * audited. Neither the report transaction nor {@code AuditService.log}'s propagation changes: every
 * other audit row still commits or rolls back with its business transaction.
 *
 * <p>Semantics:
 *
 * <ul>
 *   <li>Inside a transaction, the row is written after that transaction commits, in a new ({@code
 *       REQUIRES_NEW}) writable transaction: at {@code afterCommit} the committed transaction's
 *       resources are still bound to the thread, so joining would write into it. A report whose
 *       transaction rolls back (its rendering failed) is not audited.
 *   <li>Outside a transaction (the CSV stream, written after {@link
 *       ReportExportService#exportAsCsv} has returned), the row is written at once, in a new
 *       transaction.
 *   <li>Best effort: the report has already been produced, so a failing audit write does not fail
 *       it; it is logged at ERROR with the event type, report and actor, so the gap is visible.
 * </ul>
 *
 * The record is built by the caller, on the request thread, so the actor, IP and user agent are the
 * caller's. The tenant carries over: the new connection is checked out through the multitenant
 * connection provider, which sets {@code search_path} from {@code RequestScopes.TENANT_ID}, still
 * bound on this thread when the commit runs; {@code AuditService.log} resolves {@code actor_name}
 * in that tenant.
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

  /** Records {@code record} once the current transaction commits, or now if there is none. */
  public void recordAfterCommit(AuditEventRecord record) {
    if (TransactionSynchronizationManager.isSynchronizationActive()
        && TransactionSynchronizationManager.isActualTransactionActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              write(record);
            }
          });
    } else {
      write(record);
    }
  }

  private void write(AuditEventRecord record) {
    try {
      auditTransaction.executeWithoutResult(status -> auditService.log(record));
    } catch (RuntimeException e) {
      log.error(
          "Report audit row NOT recorded: type={}, report={}, actor={}/{}, details={}",
          record.eventType(),
          record.entityId(),
          record.actorType(),
          record.actorId(),
          record.details(),
          e);
    }
  }
}
