package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.takeon;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnAudit;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchCommitter;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchRepository;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchStatus;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRowRunner.Outcome;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOffKind;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The trust runner of the {@code take_on_drain} job (Phase 93 657C, §17.3.9, R-2): a trust position
 * is committed whole, so it is a {@link TakeOnBatchCommitter}, not a per-row committer. Runs {@link
 * TrustOpeningPositionService#commit} in one transaction of its own; when that throws — a {@link
 * TakeOnCommitRefusal} or anything unexpected — the transaction has rolled back everything (no
 * trust row, item, investment, cutover date or report), and a <b>separate</b> transaction returns
 * the batch to {@code VALIDATED} with {@code last_error} (the refusal's X3 text, never the
 * exception's message) and audits {@code takeon.batch.commit_failed {reason_code}}. The sign-off
 * stays live (the hash has not changed); a re-validation revokes it if the data differ.
 *
 * <p>Runs in a job: {@code TENANT_ID}, {@code ORG_ID} and {@code MEMBER_ID} bound, no capability.
 */
@Component
public class TrustTakeOnRunner implements TakeOnBatchCommitter {

  private static final Logger log = LoggerFactory.getLogger(TrustTakeOnRunner.class);

  private final TrustOpeningPositionService service;
  private final TakeOnBatchRepository batches;
  private final TakeOnAudit audit;
  private final TransactionTemplate tx;

  public TrustTakeOnRunner(
      TrustOpeningPositionService service,
      TakeOnBatchRepository batches,
      TakeOnAudit audit,
      PlatformTransactionManager transactionManager) {
    this.service = service;
    this.batches = batches;
    this.audit = audit;
    this.tx = new TransactionTemplate(transactionManager);
  }

  @Override
  public TakeOnDataset dataset() {
    return TakeOnDataset.TRUST_POSITION;
  }

  @Override
  public TakeOnSignOffKind requiredSignOff() {
    return TakeOnSignOffKind.TRUST_SIGN_OFF;
  }

  @Override
  public Outcome commit(UUID batchId) {
    TrustOpeningPositionService.Result result;
    try {
      result = tx.execute(status -> service.commit(batchId));
    } catch (TakeOnCommitRefusal refusal) {
      log.warn("Trust take-on batch {} not committed: {}", batchId, refusal.reasonCode());
      return returnToValidated(batchId, refusal.reasonCode(), refusal.userMessage());
    } catch (RuntimeException e) {
      log.warn(
          "Trust take-on batch {} not committed: unexpected {}",
          batchId,
          e.getClass().getSimpleName());
      return returnToValidated(
          batchId, TakeOnCommitRefusal.UNEXPECTED, TakeOnCommitRefusal.UNEXPECTED_MESSAGE);
    }
    if (result == null) {
      return Outcome.STALLED;
    }
    return switch (result) {
      case COMMITTED -> Outcome.COMPLETED;
      case NOT_COMMITTING -> Outcome.NOT_COMMITTING;
      case HELD_ELSEWHERE -> Outcome.ROWS_HELD_ELSEWHERE; // the holder finishes it; stop the drain
    };
  }

  /** The failure transaction: the batch back to {@code VALIDATED}, if it is still committing. */
  private Outcome returnToValidated(UUID batchId, String reasonCode, String lastError) {
    var returned = new AtomicBoolean();
    tx.executeWithoutResult(
        status ->
            batches
                .findByIdForUpdate(batchId)
                .filter(b -> b.getStatus() == TakeOnBatchStatus.COMMITTING)
                .ifPresent(
                    b -> {
                      b.returnToValidated(lastError);
                      audit.commitFailed(b, reasonCode);
                      returned.set(true);
                    }));
    return returned.get() ? Outcome.RETURNED_TO_VALIDATED : Outcome.NOT_COMMITTING;
  }
}
