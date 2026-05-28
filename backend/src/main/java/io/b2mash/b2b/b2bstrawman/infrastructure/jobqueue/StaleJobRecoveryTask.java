package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recovers stale jobs that were claimed but never completed (e.g., pod crash). Runs on a fixed
 * delay with ShedLock protection so only one pod executes the sweep at a time.
 *
 * <p>Stale recovery does NOT increment {@code retry_count} because the job was never actually
 * executed — the claiming pod died before completing it.
 */
@Component
@ConditionalOnProperty(name = "kazi.job-queue.enabled", havingValue = "true")
public class StaleJobRecoveryTask {

  private static final Logger log = LoggerFactory.getLogger(StaleJobRecoveryTask.class);

  private final JobQueueRepository repository;
  private final JobQueueProperties properties;

  public StaleJobRecoveryTask(JobQueueRepository repository, JobQueueProperties properties) {
    this.repository = repository;
    this.properties = properties;
  }

  @Scheduled(fixedDelay = 60_000)
  @SchedulerLock(name = "stale_job_recovery", lockAtLeastFor = "30s")
  @Transactional
  public void recoverStaleJobs() {
    Instant threshold =
        Instant.now().minus(properties.getStaleClaimTimeoutMinutes(), ChronoUnit.MINUTES);
    var staleJobs = repository.findStaleClaimed(threshold);

    if (staleJobs.isEmpty()) {
      return;
    }

    for (var job : staleJobs) {
      job.setStatus(JobStatus.PENDING);
      job.setClaimedBy(null);
      job.setClaimedAt(null);
    }
    repository.flush();

    log.warn("Recovered {} stale job(s) back to PENDING", staleJobs.size());
  }
}
