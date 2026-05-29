package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Micrometer metrics for the distributed job queue. Registers counters for job lifecycle events
 * (enqueue, complete, fail, dead-letter), gauges for queue depth (pending/claimed counts), and
 * timers for execution and claim-wait durations.
 *
 * <p>Counters are incremented by {@link JobWorker} via the public record methods. Gauges are polled
 * from the database every 30 seconds via {@link #refreshGauges()}.
 */
@Component
@ConditionalOnProperty(name = "kazi.job-queue.enabled", havingValue = "true")
public class JobQueueMetrics {

  private static final Logger log = LoggerFactory.getLogger(JobQueueMetrics.class);

  private final MeterRegistry registry;
  private final JobQueueRepository repository;

  private final ConcurrentHashMap<String, Counter> enqueuedCounters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Counter> completedCounters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Counter> failedCounters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Counter> deadLetterCounters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Timer> executionTimers = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Timer> claimWaitTimers = new ConcurrentHashMap<>();

  private final AtomicLong pendingCount = new AtomicLong(0);
  private final AtomicLong claimedCount = new AtomicLong(0);

  public JobQueueMetrics(MeterRegistry registry, JobQueueRepository repository) {
    this.registry = registry;
    this.repository = repository;

    registry.gauge("kazi_job_queue_pending_count", pendingCount);
    registry.gauge("kazi_job_queue_claimed_count", claimedCount);
  }

  /** Increment the enqueued counter for the given job type. */
  public void recordEnqueued(String jobType) {
    enqueuedCounters
        .computeIfAbsent(
            jobType,
            type ->
                Counter.builder("kazi_job_queue_enqueued_total")
                    .tag("job_type", type)
                    .description("Jobs enqueued")
                    .register(registry))
        .increment();
  }

  /** Increment the completed counter and record execution/claim-wait timers. */
  public void recordCompleted(String jobType, Instant createdAt, Instant claimedAt) {
    completedCounters
        .computeIfAbsent(
            jobType,
            type ->
                Counter.builder("kazi_job_queue_completed_total")
                    .tag("job_type", type)
                    .description("Jobs successfully completed")
                    .register(registry))
        .increment();

    recordExecutionTime(jobType, claimedAt);
    recordClaimWaitTime(jobType, createdAt, claimedAt);
  }

  /** Increment the failed counter and record execution time. */
  public void recordFailed(String jobType, Instant claimedAt) {
    failedCounters
        .computeIfAbsent(
            jobType,
            type ->
                Counter.builder("kazi_job_queue_failed_total")
                    .tag("job_type", type)
                    .description("Jobs that failed (retriable)")
                    .register(registry))
        .increment();

    recordExecutionTime(jobType, claimedAt);
  }

  /** Increment the dead-letter counter and record execution time. */
  public void recordDeadLettered(String jobType, Instant claimedAt) {
    deadLetterCounters
        .computeIfAbsent(
            jobType,
            type ->
                Counter.builder("kazi_job_queue_dead_letter_total")
                    .tag("job_type", type)
                    .description("Jobs that exhausted retries")
                    .register(registry))
        .increment();

    recordExecutionTime(jobType, claimedAt);
  }

  /** Polls the database for current queue depths. Called every 30 seconds by the scheduler. */
  @Scheduled(fixedRate = 30_000)
  public void refreshGauges() {
    try {
      var statusCounts = repository.countByStatus();
      long pending = 0;
      long claimed = 0;
      for (var row : statusCounts) {
        JobStatus status = (JobStatus) row[0];
        long count = (Long) row[1];
        if (status == JobStatus.PENDING) {
          pending = count;
        } else if (status == JobStatus.CLAIMED) {
          claimed = count;
        }
      }
      pendingCount.set(pending);
      claimedCount.set(claimed);
    } catch (Exception e) {
      // Gauge keeps its last value; log at trace to avoid noise during transient DB issues.
      log.trace("Gauge refresh skipped — DB unavailable: {}", e.getMessage());
    }
  }

  private void recordExecutionTime(String jobType, Instant claimedAt) {
    if (claimedAt == null) {
      return;
    }
    Duration duration = Duration.between(claimedAt, Instant.now());
    executionTimers
        .computeIfAbsent(
            jobType,
            type ->
                Timer.builder("kazi_job_queue_execution_seconds")
                    .tag("job_type", type)
                    .description("Time from claim to completion/failure")
                    .register(registry))
        .record(duration.toMillis(), TimeUnit.MILLISECONDS);
  }

  private void recordClaimWaitTime(String jobType, Instant createdAt, Instant claimedAt) {
    if (createdAt == null || claimedAt == null) {
      return;
    }
    Duration duration = Duration.between(createdAt, claimedAt);
    claimWaitTimers
        .computeIfAbsent(
            jobType,
            type ->
                Timer.builder("kazi_job_queue_claim_wait_seconds")
                    .tag("job_type", type)
                    .description("Time from enqueue to claim")
                    .register(registry))
        .record(duration.toMillis(), TimeUnit.MILLISECONDS);
  }
}
