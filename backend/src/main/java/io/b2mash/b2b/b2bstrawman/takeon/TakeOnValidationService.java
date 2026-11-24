package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnDatasetHandlers;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dry run (§17.3.2) — one {@code REPEATABLE_READ} transaction under the batch row lock: every
 * row reset and re-checked by the dataset's handler, batch-level checks, the statuses flushed and
 * the summary counts read in the same snapshot (§17.10 X2, so the cards cannot disagree with the
 * row table), the canonical hash computed, live sign-offs on another hash revoked, the batch marked
 * {@code VALIDATED}. Never creates or changes a client, matter, fee note or trust row.
 */
@Service
public class TakeOnValidationService {

  private final TakeOnBatchRepository batches;
  private final TakeOnSourceRepository sources;
  private final TakeOnRowRepository rows;
  private final TakeOnSignOffRepository signOffs;
  private final TakeOnDatasetHandlers handlers;
  private final TakeOnAccessPolicy accessPolicy;
  private final TakeOnAudit audit;

  public TakeOnValidationService(
      TakeOnBatchRepository batches,
      TakeOnSourceRepository sources,
      TakeOnRowRepository rows,
      TakeOnSignOffRepository signOffs,
      TakeOnDatasetHandlers handlers,
      TakeOnAccessPolicy accessPolicy,
      TakeOnAudit audit) {
    this.batches = batches;
    this.sources = sources;
    this.rows = rows;
    this.signOffs = signOffs;
    this.handlers = handlers;
    this.accessPolicy = accessPolicy;
    this.audit = audit;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public TakeOnBatch validate(UUID batchId) {
    var batch =
        batches
            .findByIdForUpdate(batchId) // serialises with sign-off and the commit claim (X5)
            .orElseThrow(() -> new ResourceNotFoundException("Take-on batch", batchId));
    accessPolicy.requireWrite(batch.getDataset());
    try {
      batch.requireValidatable(); // the state (409 wrong state) …
    } catch (IllegalStateException e) {
      throw TakeOnProblems.wrongState(e.getMessage());
    }
    if (batch.missingCutoverDate()) {
      throw TakeOnProblems.cutoverDateRequired(); // … then the position input (C-14)
    }
    if (batch.missingPosition()) {
      throw TakeOnProblems.positionRequired(); // 657B: the trust equation needs its four figures
    }
    if (rows.anyCommitted(batchId)) {
      // Defence in depth (655A C-10): a batch returns to VALIDATED only before any row is terminal,
      // so this cannot happen — and a committed row is never re-validated.
      throw TakeOnProblems.wrongState(
          "Part of this take-on batch has been committed. It cannot be validated again.");
    }

    var batchSources = sources.findByBatchIdOrderBySectionAsc(batchId);
    var handler = handlers.forDataset(batch.getDataset());
    var context = handler.loadContext(batch, batchSources);
    var batchRows = rows.findByBatchIdOrderBySourceIdAscRowNumberAsc(batchId);
    for (TakeOnRow row : batchRows) {
      row.resetForValidation();
      handler.mapAndValidate(row, context);
    }
    handler.validateBatchLevel(batch, context);
    rows.flush(); // statuses written before the native count reads them

    var counts = rows.countsFor(batchId); // same transaction and snapshot (X2)
    String hash = TakeOnHashes.of(batch, batchSources, batchRows);
    for (var signOff : signOffs.findByBatchIdAndRevokedAtIsNull(batchId)) {
      if (!hash.equals(signOff.getValidationHash())) {
        signOff.revoke(TakeOnSignOff.RevokedReason.REVALIDATED);
      }
    }
    batch.markValidated(
        hash,
        new TakeOnBatch.Counts(
            Math.toIntExact(counts.getTotal()),
            Math.toIntExact(counts.getValid()),
            // rows in error + the batch-level errors (657B: the trust account checks, D-191)
            Math.toIntExact(counts.getError()) + handler.batchErrorCount(context),
            Math.toIntExact(counts.getDuplicate()),
            // rows holding a block + the batch-level statutory blocks (657B: the trust equation)
            Math.toIntExact(counts.getBlocks()) + handler.batchBlockCount(context)),
        handler.summary(context));
    audit.batchValidated(batch);
    return batch;
  }
}
