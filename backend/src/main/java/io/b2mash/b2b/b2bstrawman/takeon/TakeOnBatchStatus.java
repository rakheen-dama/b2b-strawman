package io.b2mash.b2b.b2bstrawman.takeon;

/**
 * Lifecycle of a take-on batch (§17.3.2). Transitions are guarded methods on {@link TakeOnBatch};
 * stored in {@code take_on_batches.status} (CHECK {@code chk_take_on_batch_status}).
 */
public enum TakeOnBatchStatus {
  UPLOADED,
  MAPPED,
  VALIDATED,
  COMMITTING,
  COMPLETED,
  CANCELLED;

  /** Not yet committed or cancelled — may still be changed, cancelled or purged. */
  public boolean isOpen() {
    return this == UPLOADED || this == MAPPED || this == VALIDATED;
  }
}
