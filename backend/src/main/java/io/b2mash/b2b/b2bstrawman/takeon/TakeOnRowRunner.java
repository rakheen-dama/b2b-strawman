package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnRowCommitter;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnRowCommitters;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the per-row commit of one {@code COMMITTING} batch (Phase 93 655A, §17.3.3 step 3, X5).
 *
 * <ol>
 *   <li><b>Precondition</b> transaction: lock the batch, re-check {@code COMMITTING}, load the live
 *       required sign-off on the batch hash. Missing while no row is terminal → the batch returns
 *       to {@code VALIDATED} with the reason and {@code takeon.batch.commit_failed} (C-10); once
 *       any row is terminal a failure leaves the batch {@code COMMITTING} for the next drain.
 *   <li><b>Rows</b>, each in its own transaction: claim the next {@code VALIDATED} row {@code FOR
 *       UPDATE SKIP LOCKED} (a row another worker holds is skipped, never waited for), re-check it
 *       under the lock, commit it. A row that throws is rolled back and marked {@code FAILED} in a
 *       fresh transaction — one bad row never stops the batch, and a redelivered or concurrent
 *       drain never sees a terminal row again.
 *   <li><b>Completion</b> transaction: lock the batch, re-check {@code COMMITTING}, and complete
 *       only when a plain (non-locking) read finds no {@code VALIDATED} row left (C-6) — a row
 *       another worker is still committing reads as {@code VALIDATED}, so that worker completes.
 * </ol>
 *
 * <p>The budget is checked after each row. Runs with {@code MEMBER_ID} bound to the member who
 * confirmed the commit (the drain binds it), and without capabilities.
 */
@Component
public class TakeOnRowRunner {

  /** How a run ended — the drain decides from it whether to look for more work. */
  public enum Outcome {
    /** This run completed the batch. */
    COMPLETED,
    /** The batch was no longer committing (already completed by another worker). */
    NOT_COMMITTING,
    /** The precondition failed before any row was terminal: back to {@code VALIDATED}. */
    RETURNED_TO_VALIDATED,
    /** No row left to claim, but another worker still holds one: it completes the batch. */
    ROWS_HELD_ELSEWHERE,
    /** The run's budget ran out; the next drain (or the sweep) continues. */
    BUDGET_EXHAUSTED,
    /** The batch cannot run now (logged); it stays {@code COMMITTING} for the next drain. */
    STALLED
  }

  static final String ATTESTATION_MISSING_REASON =
      "The attestation for this take-on batch was withdrawn before the commit ran. Attest it"
          + " again, then commit.";

  private static final Logger log = LoggerFactory.getLogger(TakeOnRowRunner.class);

  private final TakeOnBatchRepository batches;
  private final TakeOnRowRepository rows;
  private final TakeOnSignOffRepository signOffs;
  private final TakeOnRowCommitters committers;
  private final TakeOnAudit audit;
  private final TransactionTemplate tx;
  private final Clock clock;

  public TakeOnRowRunner(
      TakeOnBatchRepository batches,
      TakeOnRowRepository rows,
      TakeOnSignOffRepository signOffs,
      TakeOnRowCommitters committers,
      TakeOnAudit audit,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.batches = batches;
    this.rows = rows;
    this.signOffs = signOffs;
    this.committers = committers;
    this.audit = audit;
    this.tx = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  /** One committer with what it prepared — keeps the committer's context type together. */
  private record Prepared<C>(TakeOnRowCommitter<C> committer, C context) {
    void commit(TakeOnRow row) {
      committer.commitRow(row, context);
    }
  }

  /** The precondition's result: what to run, or the outcome when there is nothing to run. */
  private record Start(Prepared<?> prepared, Outcome outcome) {}

  public Outcome run(UUID batchId, Instant deadline) {
    Start start = tx.execute(status -> precondition(batchId));
    if (start == null || start.prepared() == null) {
      return start == null ? Outcome.STALLED : start.outcome();
    }
    var prepared = start.prepared();

    while (true) {
      var claimed = new AtomicReference<UUID>();
      Boolean processed;
      try {
        processed = tx.execute(status -> commitNextRow(batchId, prepared, claimed));
      } catch (RuntimeException e) {
        UUID rowId = claimed.get();
        if (rowId == null) {
          throw e; // failed before any row was claimed: stop; the batch stays COMMITTING
        }
        markFailed(rowId, prepared.committer().failureMessage(e));
        log.warn(
            "Take-on row {} of batch {} failed and was marked FAILED ({})",
            rowId,
            batchId,
            e.getClass().getSimpleName());
        processed = Boolean.TRUE;
      }
      if (!Boolean.TRUE.equals(processed)) {
        break; // nothing left this worker can claim
      }
      if (!clock.instant().isBefore(deadline)) {
        return Outcome.BUDGET_EXHAUSTED;
      }
    }
    Outcome done = tx.execute(status -> complete(batchId));
    return done == null ? Outcome.STALLED : done;
  }

  private Start precondition(UUID batchId) {
    var batch = batches.findByIdForUpdate(batchId).orElse(null);
    if (batch == null || batch.getStatus() != TakeOnBatchStatus.COMMITTING) {
      return new Start(null, Outcome.NOT_COMMITTING);
    }
    var committer = committers.forDataset(batch.getDataset()).orElse(null);
    if (committer == null) {
      log.error("Take-on batch {} is committing but its dataset has no committer", batchId);
      return new Start(null, Outcome.STALLED);
    }
    TakeOnSignOff signOff = null;
    var kind = committer.requiredSignOff();
    if (kind != null) {
      signOff =
          signOffs
              .findByBatchIdAndKindAndRevokedAtIsNull(batchId, kind)
              .filter(o -> o.getValidationHash().equals(batch.getValidationHash()))
              .orElse(null);
      if (signOff == null) {
        if (rows.anyCommitted(batchId)) {
          log.error(
              "Take-on batch {} lost its live sign-off after rows were committed; left COMMITTING",
              batchId);
          return new Start(null, Outcome.STALLED);
        }
        batch.returnToValidated(ATTESTATION_MISSING_REASON);
        audit.commitFailed(batch, "ATTESTATION_MISSING");
        return new Start(null, Outcome.RETURNED_TO_VALIDATED);
      }
    }
    return new Start(prepare(committer, batch, signOff), null);
  }

  private static <C> Prepared<C> prepare(
      TakeOnRowCommitter<C> committer, TakeOnBatch batch, TakeOnSignOff signOff) {
    return new Prepared<>(committer, committer.prepare(batch, signOff));
  }

  /** One row in its own transaction; {@code false} when no row could be claimed. */
  private Boolean commitNextRow(UUID batchId, Prepared<?> prepared, AtomicReference<UUID> claimed) {
    var row = rows.claimNextValidated(batchId).orElse(null);
    if (row == null) {
      return Boolean.FALSE;
    }
    claimed.set(row.getId());
    if (row.getStatus() != TakeOnRowStatus.VALIDATED) {
      return Boolean.TRUE; // re-checked under the lock: someone else finished it
    }
    prepared.commit(row);
    if (!row.getStatus().isTerminal()) {
      throw new IllegalStateException("A take-on row committer left a row non-terminal");
    }
    return Boolean.TRUE;
  }

  private void markFailed(UUID rowId, String reason) {
    tx.executeWithoutResult(
        status ->
            rows.findByIdForUpdate(rowId)
                .filter(r -> r.getStatus() == TakeOnRowStatus.VALIDATED)
                .ifPresent(r -> r.markFailed(reason)));
  }

  private Outcome complete(UUID batchId) {
    var batch = batches.findByIdForUpdate(batchId).orElse(null);
    if (batch == null || batch.getStatus() != TakeOnBatchStatus.COMMITTING) {
      return Outcome.NOT_COMMITTING; // a second completer stops here (idempotent)
    }
    if (rows.anyToCommit(batchId)) {
      return Outcome.ROWS_HELD_ELSEWHERE; // plain read: another worker is mid-row (C-6)
    }
    var counts = rows.commitCountsFor(batchId);
    batch.complete(
        Math.toIntExact(counts.getCreated()),
        Math.toIntExact(counts.getSkipped()),
        Math.toIntExact(counts.getFailed()));
    audit.committed(batch);
    log.info(
        "Take-on batch {} committed: created={}, skipped={}, failed={}",
        batchId,
        counts.getCreated(),
        counts.getSkipped(),
        counts.getFailed());
    return Outcome.COMPLETED;
  }
}
