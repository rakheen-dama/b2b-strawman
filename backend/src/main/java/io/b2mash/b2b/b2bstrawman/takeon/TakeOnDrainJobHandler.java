package io.b2mash.b2b.b2bstrawman.takeon;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue.JobHandler;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.annotation.Nullable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The per-tenant take-on drain ({@code take_on_drain}, Phase 93 655A, §17.3.3 step 2, X5). One job
 * type per tenant: the queue's dedup ({@code idx_job_queue_dedup (job_type, tenant_id)}) ignores
 * payloads, so a job per batch would strand all but the first; row locks, not the queue, guard
 * against double processing.
 *
 * <p>Loops: claims the oldest {@code COMMITTING} batch ({@code FOR UPDATE SKIP LOCKED}, a short
 * transaction), binds {@code MEMBER_ID} to the member who confirmed the commit ({@code
 * runForTenantOnShardWithMember}, keeping the worker's shard; audit rows and {@code created_by}
 * name the member), and runs {@link TakeOnRowRunner}; returns when no {@code COMMITTING} batch is
 * left, when another worker holds the batch's last rows, or when the run budget (10 minutes, below
 * the 15-minute stale-claim recovery) is spent — {@link TakeOnDrainSweep} or the next queued drain
 * continues. A redelivered or concurrent run is harmless: every claim re-checks status under its
 * lock. The job payload is always {@code null}.
 */
@Component
public class TakeOnDrainJobHandler implements JobHandler {

  public static final String JOB_TYPE = "take_on_drain";

  /** Below the queue's 15-minute stale-claim recovery ({@code JobQueueProperties}). */
  public static final Duration DEFAULT_BUDGET = Duration.ofMinutes(10);

  private static final Logger log = LoggerFactory.getLogger(TakeOnDrainJobHandler.class);

  private final TakeOnBatchRepository batches;
  private final TakeOnRowRunner runner;
  private final TransactionTemplate tx;
  private final Clock clock;

  public TakeOnDrainJobHandler(
      TakeOnBatchRepository batches,
      TakeOnRowRunner runner,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.batches = batches;
    this.runner = runner;
    this.tx = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  @Override
  public String jobType() {
    return JOB_TYPE;
  }

  @Override
  public void execute(@Nullable JsonNode payload) {
    drainFor(DEFAULT_BUDGET);
  }

  private record Claim(UUID batchId, UUID actor) {}

  /**
   * Drains the bound tenant within {@code budget} (checked after each row; {@code Duration.ZERO}
   * commits exactly one row). Public so tests can inject a budget without a property (no new
   * context key). Needs {@code TENANT_ID} bound, as the job worker binds it.
   */
  public void drainFor(Duration budget) {
    Instant deadline = clock.instant().plus(budget);
    var seen = new HashSet<UUID>();
    while (true) {
      Optional<Claim> claim =
          tx.execute(
              status ->
                  batches
                      .findNextCommittingSkipLocked()
                      .map(b -> new Claim(b.getId(), b.getCommitRequestedBy())));
      if (claim == null || claim.isEmpty()) {
        return; // no committing batch left (or another worker is claiming it this instant)
      }
      var c = claim.get();
      if (!seen.add(c.batchId())) {
        return; // the same batch again: another worker holds its last rows, or it stalled
      }
      if (c.actor() == null) {
        log.error("Take-on batch {} is committing without a requesting member", c.batchId());
        return;
      }
      var outcome = new AtomicReference<TakeOnRowRunner.Outcome>();
      RequestScopes.runForTenantOnShardWithMember(
          RequestScopes.requireTenantId(),
          RequestScopes.getOrgIdOrNull(),
          RequestScopes.getShardIdOrDefault(),
          c.actor(),
          () -> outcome.set(runner.run(c.batchId(), deadline)));
      switch (outcome.get()) {
        case COMPLETED, NOT_COMMITTING, RETURNED_TO_VALIDATED -> {
          // look for the next committing batch
        }
        case ROWS_HELD_ELSEWHERE, BUDGET_EXHAUSTED, STALLED -> {
          return;
        }
      }
      if (!clock.instant().isBefore(deadline)) {
        return;
      }
    }
  }
}
