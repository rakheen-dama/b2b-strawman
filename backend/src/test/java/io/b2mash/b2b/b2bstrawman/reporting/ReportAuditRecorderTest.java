package io.b2mash.b2b.b2bstrawman.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Phase 92 FU-12: {@link ReportAuditRecorder}'s timing and failure semantics, without a database
 * (the end-to-end rows are in {@code ReportAuditRowsIntegrationTest}).
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
          null,
          null,
          Map.of("slug", "section-35-data-pack", "format", "pdf"));

  private final AuditService auditService = mock(AuditService.class);
  private final RecordingTransactionManager transactionManager = new RecordingTransactionManager();
  private final ReportAuditRecorder recorder =
      new ReportAuditRecorder(auditService, transactionManager);

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
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    TransactionSynchronizationManager.setActualTransactionActive(false);
  }

  @Test
  void noTransaction_writesNow_inANewWritableTransaction() {
    recorder.recordAfterCommit(RECORD);

    verify(auditService).log(RECORD);
    assertThat(transactionManager.begun).hasSize(1);
    var definition = transactionManager.begun.getFirst();
    assertThat(definition.getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    assertThat(definition.isReadOnly()).isFalse();
    assertThat(transactionManager.commits).isEqualTo(1);
  }

  @Test
  void insideATransaction_writesOnlyAfterItCommits() {
    var synchronizations = openCallerTransaction();

    recorder.recordAfterCommit(RECORD);
    verify(auditService, never()).log(any());

    synchronizations.get().forEach(TransactionSynchronization::afterCommit);
    verify(auditService).log(RECORD);
    assertThat(transactionManager.begun.getFirst().getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Test
  void insideATransactionThatRollsBack_writesNothing() {
    var synchronizations = openCallerTransaction();

    recorder.recordAfterCommit(RECORD);
    synchronizations
        .get()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    verify(auditService, never()).log(any());
    assertThat(transactionManager.begun).isEmpty();
  }

  @Test
  void auditWriteFails_reportIsNotFailed_errorIsLogged() {
    doThrow(new IllegalStateException("audit_events unavailable")).when(auditService).log(any());

    assertThatCode(() -> recorder.recordAfterCommit(RECORD)).doesNotThrowAnyException();

    assertThat(transactionManager.rollbacks).isEqualTo(1);
    assertThat(appender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.ERROR);
              assertThat(event.getFormattedMessage())
                  .contains("REPORT_EXPORTED")
                  .contains(RECORD.entityId().toString())
                  .contains("section-35-data-pack");
              assertThat(event.getThrowableProxy().getMessage())
                  .isEqualTo("audit_events unavailable");
            });
  }

  @Test
  void auditWriteFailsAfterCommit_commitIsNotFailed() {
    var synchronizations = openCallerTransaction();
    doAnswer(
            inv -> {
              throw new IllegalStateException("pool exhausted");
            })
        .when(auditService)
        .log(any());

    recorder.recordAfterCommit(RECORD);

    // Spring propagates an afterCommit exception to the committing caller; the recorder must not
    assertThatCode(() -> synchronizations.get().forEach(TransactionSynchronization::afterCommit))
        .doesNotThrowAnyException();
    assertThat(appender.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.ERROR);
  }

  /** Simulates the caller's (report) transaction: synchronization on, a transaction active. */
  private java.util.function.Supplier<List<TransactionSynchronization>> openCallerTransaction() {
    TransactionSynchronizationManager.initSynchronization();
    TransactionSynchronizationManager.setActualTransactionActive(true);
    return TransactionSynchronizationManager::getSynchronizations;
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
