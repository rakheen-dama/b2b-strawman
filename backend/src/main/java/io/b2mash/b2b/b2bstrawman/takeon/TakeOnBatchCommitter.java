package io.b2mash.b2b.b2bstrawman.takeon;

import java.util.UUID;

/**
 * A dataset committed whole, in one transaction — the trust position (Phase 93 657C, §17.3.9, R-2).
 * Unlike a per-row {@code TakeOnRowCommitter}, the drain hands it the batch: it locks the batch
 * itself ({@code FOR UPDATE SKIP LOCKED}), posts everything or nothing, and on a failure returns
 * the batch to {@code VALIDATED} with the reason in a separate transaction. Not a second job type:
 * the {@code take_on_drain} job routes a batch of this dataset here instead of to the row runner.
 *
 * <p>Runs in a job: {@code TENANT_ID}, {@code ORG_ID} and {@code MEMBER_ID} (= the member who
 * confirmed the commit) are bound, {@code CAPABILITIES} is not — nothing here may check a
 * capability.
 */
public interface TakeOnBatchCommitter {

  TakeOnDataset dataset();

  /** The live sign-off a commit of this dataset requires (never null for a whole-batch commit). */
  TakeOnSignOffKind requiredSignOff();

  /**
   * Commits one {@code COMMITTING} batch: {@code COMPLETED} when this call committed it, {@code
   * NOT_COMMITTING} when it is no longer committing, {@code ROWS_HELD_ELSEWHERE} when another
   * worker holds the batch row, {@code RETURNED_TO_VALIDATED} when the commit failed and the batch
   * went back to {@code VALIDATED} with {@code last_error}.
   */
  TakeOnRowRunner.Outcome commit(UUID batchId);
}
