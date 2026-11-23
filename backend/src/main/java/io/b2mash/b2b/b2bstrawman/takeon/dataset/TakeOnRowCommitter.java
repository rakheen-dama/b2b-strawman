package io.b2mash.b2b.b2bstrawman.takeon.dataset;

import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatch;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnDataset;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnRow;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOff;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnSignOffKind;

/**
 * The per-row commit of one dataset (§17.3.3 step 3; 655A clients, 656A matters, 658A debtors). The
 * engine ({@code TakeOnRowRunner}) claims each row in its own transaction and calls {@link
 * #commitRow}; the committer must leave the row terminal ({@code CREATED}, {@code
 * SKIPPED_DUPLICATE}, {@code SKIPPED_ZERO} or {@code FAILED}). A row whose transaction throws is
 * rolled back and marked {@code FAILED} with {@link #failureMessage} in a fresh transaction, so one
 * bad row never stops the batch.
 *
 * <p>Runs in a job: {@code TENANT_ID}, {@code ORG_ID} and {@code MEMBER_ID} (= the member who
 * confirmed the commit) are bound, {@code CAPABILITIES} is not — nothing here may check a
 * capability.
 *
 * @param <C> what {@link #prepare} loads once per run and every row reads
 */
public interface TakeOnRowCommitter<C> {

  TakeOnDataset dataset();

  /** The live sign-off a commit of this dataset requires, or {@code null} when none. */
  default TakeOnSignOffKind requiredSignOff() {
    return null;
  }

  /**
   * Loads the per-run state (settings, the live sign-off …) in the precondition transaction, which
   * holds the batch lock. {@code signOff} is the live required sign-off, or {@code null}.
   */
  C prepare(TakeOnBatch batch, TakeOnSignOff signOff);

  /** Commits one claimed {@code VALIDATED} row; must leave it terminal. */
  void commitRow(TakeOnRow row, C context);

  /**
   * The user-facing reason for a row whose transaction failed — X3-style text, never an exception
   * message or a cell value.
   */
  String failureMessage(RuntimeException failure);
}
