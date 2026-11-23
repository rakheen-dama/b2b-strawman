package io.b2mash.b2b.b2bstrawman.takeon;

import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobEnqueuer;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobQueueProperties;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnBatchResponse;
import io.b2mash.b2b.b2bstrawman.takeon.TakeOnBatchService.TakeOnCommitRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The controller's entry to a commit (Phase 93 655A, §17.3.3 step 1). Deliberately <b>not</b>
 * {@code @Transactional}:
 *
 * <ol>
 *   <li>the job queue switched off → 409 {@code TAKE_ON_QUEUE_DISABLED} before any claim (FD-35: no
 *       batch is ever left {@code COMMITTING} with nothing to run it);
 *   <li>the claim, {@link TakeOnCommitService#requestCommit}, in its own transaction — committed
 *       when it returns;
 *   <li>then {@code take_on_drain} is enqueued in a second, separate transaction ({@code
 *       DefaultJobEnqueuer.enqueue} is {@code REQUIRED} and there is no outer transaction to join).
 *       Never {@code afterCommit} (a REQUIRED method there joins the finished transaction and its
 *       insert is never committed) and never inside the claim (a dedup violation would mark the
 *       claim rollback-only). The payload is {@code null}: the drain finds its work by status, and
 *       no PII reaches the queue.
 * </ol>
 *
 * <p>An enqueue that fails is logged and the request still answers 202: the batch is {@code
 * COMMITTING} and {@link TakeOnDrainSweep} enqueues the drain within five minutes.
 */
@Component
public class TakeOnCommitFacade {

  private static final Logger log = LoggerFactory.getLogger(TakeOnCommitFacade.class);

  private final JobQueueProperties jobQueueProperties;
  private final TakeOnCommitService commitService;
  private final JobEnqueuer jobEnqueuer;

  public TakeOnCommitFacade(
      JobQueueProperties jobQueueProperties,
      TakeOnCommitService commitService,
      JobEnqueuer jobEnqueuer) {
    this.jobQueueProperties = jobQueueProperties;
    this.commitService = commitService;
    this.jobEnqueuer = jobEnqueuer;
  }

  public TakeOnBatchResponse commit(UUID batchId, TakeOnCommitRequest request) {
    if (!jobQueueProperties.isEnabled()) {
      throw TakeOnProblems.queueDisabled(); // before any claim
    }
    var response = commitService.requestCommit(batchId, request); // tx 1: the claim, committed
    try {
      jobEnqueuer.enqueue(
          TakeOnDrainJobHandler.JOB_TYPE,
          RequestScopes.requireTenantId(),
          RequestScopes.requireOrgId(),
          RequestScopes.getShardIdOrDefault(),
          null); // tx 2: its own
    } catch (RuntimeException e) {
      log.warn(
          "take_on_drain enqueue failed for batch={} — the sweep will enqueue it ({})",
          batchId,
          e.getClass().getSimpleName());
    }
    return response;
  }
}
