package io.b2mash.b2b.b2bstrawman.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRecord;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * Phase 92 FU-12: {@link ReportAuditRecorder} (a new writable transaction, best effort) and {@link
 * ReportRunService} (the row is written after the entry point returns, never when it throws),
 * without a database. The end-to-end rows are in {@code ReportAuditRowsIntegrationTest}, the
 * connection budget in {@code ReportAuditConnectionBudgetIntegrationTest}.
 */
class ReportAuditRecorderTest {

  private static final AuditEventRecord RECORD =
      new AuditEventRecord(
          "REPORT_EXPORTED",
          "REPORT",
          UUID.randomUUID(),
          UUID.randomUUID(),
          "USER",
          "API",
          "203.0.113.7",
          "probe-agent/1.0",
          Map.of("slug", "section-35-data-pack", "format", "pdf"));

  private final AuditService auditService = mock(AuditService.class);
  private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
  private final ReportAuditRecorder recorder =
      new ReportAuditRecorder(auditService, transactionManager);

  private final ReportExportService reportExportService = mock(ReportExportService.class);
  private final ReportRunService runService = new ReportRunService(reportExportService, recorder);

  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private final Logger logger = (Logger) LoggerFactory.getLogger(ReportAuditRecorder.class);

  @BeforeEach
  void attachAppender() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
  }

  // --- ReportAuditRecorder --------------------------------------------------------------------

  @Test
  void record_writesInANewWritableTransaction() {
    recorder.record(RECORD);

    verify(auditService).log(RECORD);
    assertThat(transactionManager.begun).hasSize(1);
    var definition = transactionManager.begun.getFirst();
    assertThat(definition.getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    assertThat(definition.isReadOnly()).isFalse();
    assertThat(transactionManager.commits).isEqualTo(1);
  }

  @Test
  void record_auditWriteFails_doesNotThrow_logsErrorWithTheRowsFields() {
    doThrow(new IllegalStateException("audit_events unavailable")).when(auditService).log(any());

    assertThatCode(() -> recorder.record(RECORD)).doesNotThrowAnyException();

    assertThat(transactionManager.rollbacks).isEqualTo(1);
    assertThat(appender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getFormattedMessage())
                  .contains("REPORT_EXPORTED")
                  .contains(RECORD.entityId().toString())
                  .contains(RECORD.actorId().toString())
                  .contains("source=API")
                  .contains("ip=203.0.113.7")
                  .contains("userAgent=probe-agent/1.0")
                  .contains("section-35-data-pack");
              assertThat(event.getThrowableProxy().getMessage())
                  .isEqualTo("audit_events unavailable");
            });
  }

  // --- ReportRunService -----------------------------------------------------------------------

  @Test
  void runService_pdf_writesTheReturnedRowAfterTheEntryPointReturns() {
    var pdf = new ReportExportService.PdfExportResult(new byte[] {1}, "r.pdf");
    when(reportExportService.exportAsPdf(anyString(), anyMap()))
        .thenReturn(new AuditedReport<>(pdf, RECORD));

    assertThat(runService.exportAsPdf("section-35-data-pack", Map.of())).isSameAs(pdf);

    var order = inOrder(reportExportService, auditService);
    order.verify(reportExportService).exportAsPdf("section-35-data-pack", Map.of());
    order.verify(auditService).log(RECORD);
  }

  @Test
  void runService_execute_writesTheReturnedRowAfterTheEntryPointReturns() {
    var response = mock(ReportExecutionResponse.class);
    var pageable = PageRequest.of(0, 10);
    when(reportExportService.execute("timesheet", Map.of(), pageable))
        .thenReturn(new AuditedReport<>(response, RECORD));

    assertThat(runService.execute("timesheet", Map.of(), pageable)).isSameAs(response);

    var order = inOrder(reportExportService, auditService);
    order.verify(reportExportService).execute("timesheet", Map.of(), pageable);
    order.verify(auditService).log(RECORD);
  }

  /** The entry point throws on a rollback or a failed commit: nothing is written. */
  @Test
  void runService_entryPointThrows_writesNothing() {
    when(reportExportService.exportAsPdf(anyString(), anyMap()))
        .thenThrow(new IllegalStateException("render failed"));

    assertThatThrownBy(() -> runService.exportAsPdf("timesheet", Map.of()))
        .hasMessage("render failed");

    verify(auditService, never()).log(any());
    assertThat(transactionManager.begun).isEmpty();
  }

  @Test
  void runService_auditWriteFails_reportIsStillReturned_errorIsLogged() {
    var pdf = new ReportExportService.PdfExportResult(new byte[] {1}, "r.pdf");
    when(reportExportService.exportAsPdf(anyString(), anyMap()))
        .thenReturn(new AuditedReport<>(pdf, RECORD));
    doThrow(new IllegalStateException("pool exhausted")).when(auditService).log(any());

    assertThat(runService.exportAsPdf("timesheet", Map.of())).isSameAs(pdf);
    assertThat(appender.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.ERROR);
  }

  /** Records what was begun, committed and rolled back; touches no resource. */
  private static final class RecordingTransactionManager
      extends AbstractPlatformTransactionManager {

    final List<TransactionDefinition> begun = new ArrayList<>();
    int commits;
    int rollbacks;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      begun.add(definition);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rollbacks++;
    }
  }
}
