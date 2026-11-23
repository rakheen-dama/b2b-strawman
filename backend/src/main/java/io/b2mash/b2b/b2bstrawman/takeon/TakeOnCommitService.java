package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnBatchResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnCommitRequest;
import io.b2mash.b2b.b2bstrawman.takeon.dataset.TakeOnRowCommitters;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The commit claim (Phase 93 655A, §17.3.3 step 1): one short transaction that locks the batch,
 * moves it {@code VALIDATED → COMMITTING} under a live sign-off on the current hash, and flushes —
 * {@code uq_take_on_batches_committing} allows one {@code COMMITTING} batch per tenant. Audits
 * {@code takeon.batch.commit_requested} in the same transaction. The work itself is the drain job's
 * ({@link TakeOnDrainJobHandler}); the controller enters through {@link TakeOnCommitFacade}, which
 * refuses up front when the job queue is off.
 *
 * <p>A double-submit blocks on the row lock until the first claim commits, then sees {@code
 * COMMITTING} → 409 {@code ALREADY_COMMITTING}.
 */
@Service
public class TakeOnCommitService {

  private final TakeOnBatchRepository batches;
  private final TakeOnAccessPolicy accessPolicy;
  private final TakeOnSignOffService signOffService;
  private final TakeOnRowCommitters committers;
  private final TakeOnBatchViews views;
  private final TakeOnAudit audit;

  public TakeOnCommitService(
      TakeOnBatchRepository batches,
      TakeOnAccessPolicy accessPolicy,
      TakeOnSignOffService signOffService,
      TakeOnRowCommitters committers,
      TakeOnBatchViews views,
      TakeOnAudit audit) {
    this.batches = batches;
    this.accessPolicy = accessPolicy;
    this.signOffService = signOffService;
    this.committers = committers;
    this.views = views;
    this.audit = audit;
  }

  @Transactional
  public TakeOnBatchResponse requestCommit(UUID batchId, TakeOnCommitRequest request) {
    var batch =
        batches
            .findByIdForUpdate(batchId)
            .orElseThrow(() -> new ResourceNotFoundException("Take-on batch", batchId));
    accessPolicy.requireWrite(batch.getDataset());
    if (batch.getStatus() == TakeOnBatchStatus.COMMITTING) {
      throw TakeOnProblems.alreadyCommitting();
    }
    if (batch.getStatus() != TakeOnBatchStatus.VALIDATED) {
      throw TakeOnProblems.wrongState("Only a validated take-on batch can be committed.");
    }
    if (batch.getBlockCount() > 0) {
      throw TakeOnProblems.blocked(); // a hard block stops the batch before it is claimed
    }
    // Only datasets whose slice has shipped a row committer can reach COMMITTING (655A: clients),
    // so the drain never meets a batch it cannot run.
    var committer =
        committers.forDataset(batch.getDataset()).orElseThrow(TakeOnProblems::commitNotAvailable);
    String hash = request == null ? null : request.validationHash();
    if (hash == null || !hash.equals(batch.getValidationHash())) {
      throw TakeOnProblems.hashMismatch();
    }
    var signOff = signOffService.requireLiveFor(batch, committer.requiredSignOff());
    if (!hash.equals(signOff.getValidationHash())) {
      throw TakeOnProblems.hashMismatch();
    }
    if (batches.existsByStatus(TakeOnBatchStatus.COMMITTING)) {
      throw TakeOnProblems.anotherBatchCommitting(); // friendly pre-check; the index decides
    }
    try {
      batch.claimCommit(hash, signOff, RequestScopes.requireMemberId());
    } catch (IllegalStateException e) {
      throw TakeOnProblems.wrongState(e.getMessage());
    }
    try {
      batches.saveAndFlush(batch);
    } catch (DataIntegrityViolationException e) {
      throw TakeOnProblems.anotherBatchCommitting(); // uq_take_on_batches_committing race
    }
    audit.commitRequested(batch, committer.requiredSignOff() == null ? null : signOff.getId());
    return views.batch(batch);
  }
}
