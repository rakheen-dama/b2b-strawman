package io.b2mash.b2b.b2bstrawman.reporting;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
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
 * of their own, once the report's transaction has committed (Phase 92 FU-12, 650C F-650C-1).
 *
 * <p>Why: every report entry point in {@link ReportExportService} runs in a read-only {@code
 * REPEATABLE_READ} transaction, so that a report reads on one snapshot (650C D-38; FU-3 D-1 keeps
 * it that way). {@link AuditService#log} joins its caller's transaction; inside a read-only one
 * (Hibernate flush mode MANUAL) the row was never flushed, so no report run or PDF export was ever
 * audited. Neither the report transaction nor {@code AuditService.log}'s propagation changes: every
 * other audit row still commits or rolls back with its business transaction.
 *
 * <p>Two parts, because a commit callback is too early to write. At {@code afterCommit} the report
 * transaction's connection is still checked out of the pool (Spring runs the callback before {@code
 * cleanupAfterCompletion} closes the session; measured in {@code
 * ReportAuditConnectionBudgetIntegrationTest}), so a write there holds two connections per report.
 *
 * <ul>
 *   <li>{@link #recordAfterCommit} (the audit sites, inside the report transaction) only
 *       <em>collects</em> the row when that transaction commits. A report whose transaction rolls
 *       back is not audited.
 *   <li>{@link #writeAfter} (called by {@link ReportRunService}, which is not transactional, around
 *       a call through the {@code ReportExportService} proxy) writes the collected rows after that
 *       call has returned, when the report transaction has completed and its connection is back in
 *       the pool: one connection at a time.
 * </ul>
 *
 * Other cases: outside any transaction (the CSV stream, written after {@link
 * ReportExportService#exportAsCsv} has returned) the row is written at once. A direct caller that
 * is not inside {@link #writeAfter} (tests, a future in-process caller) still gets its row, written
 * at {@code afterCommit} in a {@code REQUIRES_NEW} transaction: correct, but it briefly holds a
 * second connection, so production report endpoints go through {@link ReportRunService}.
 *
 * <p>Every write is a new ({@code REQUIRES_NEW}) writable transaction, and best effort: the report
 * has already been produced, so a failing write does not fail it; it is logged at ERROR with the
 * row's fields, so the gap is visible and the row can be reconstructed.
 *
 * <p>The record is built by the caller, on the request thread, so the actor, IP and user agent are
 * the caller's. The tenant carries over: the new connection is checked out through the multitenant
 * connection provider, which sets {@code search_path} from {@code RequestScopes.TENANT_ID}, still
 * bound on this thread; {@code AuditService.log} resolves {@code actor_name} in that tenant.
 */
@Component
public class ReportAuditRecorder {

  private static final Logger log = LoggerFactory.getLogger(ReportAuditRecorder.class);

  /**
   * Rows whose report transaction committed, to be written by the enclosing {@link #writeAfter}.
   */
  private static final ScopedValue<List<AuditEventRecord>> COMMITTED = ScopedValue.newInstance();

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

  /**
   * Runs {@code reportCall} — a call through the proxy of a transactional report entry point — and
   * then writes the audit rows whose transaction committed during it. The caller must not be
   * transactional: inside a transaction this only runs the call, and the rows are written at that
   * transaction's commit (see {@link #recordAfterCommit}).
   */
  public <T> T writeAfter(Supplier<T> reportCall) {
    if (TransactionSynchronizationManager.isActualTransactionActive() || COMMITTED.isBound()) {
      return reportCall.get();
    }
    var committed = new ArrayList<AuditEventRecord>();
    try {
      return ScopedValue.where(COMMITTED, committed).call(reportCall::get);
    } finally {
      // The report transaction has completed (committed or rolled back) and released its
      // connection; only committed rows were collected.
      committed.forEach(this::write);
    }
  }

  /**
   * Records {@code record} for the current transaction: collected at its commit for the enclosing
   * {@link #writeAfter}, else written at its commit; with no transaction, written now.
   */
  public void recordAfterCommit(AuditEventRecord record) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()
        || !TransactionSynchronizationManager.isActualTransactionActive()) {
      write(record);
      return;
    }
    Runnable onCommit =
        COMMITTED.isBound() ? collectInto(COMMITTED.get(), record) : () -> write(record);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            onCommit.run();
          }
        });
  }

  private static Runnable collectInto(List<AuditEventRecord> committed, AuditEventRecord record) {
    return () -> committed.add(record);
  }

  private void write(AuditEventRecord record) {
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
