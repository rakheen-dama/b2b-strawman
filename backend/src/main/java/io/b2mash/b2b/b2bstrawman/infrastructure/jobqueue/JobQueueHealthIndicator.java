package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the distributed job queue. Reports UP when the queue is operational and
 * pending job age is within threshold. Reports DOWN if the oldest pending job exceeds the
 * configured threshold (indicating a stalled queue).
 */
@Component
@ConditionalOnProperty(name = "kazi.job-queue.enabled", havingValue = "true")
public class JobQueueHealthIndicator implements HealthIndicator {

  /** Maximum age of a pending job before the health check reports DOWN. */
  private static final Duration MAX_PENDING_AGE = Duration.ofMinutes(30);

  private final JobQueueRepository repository;

  public JobQueueHealthIndicator(JobQueueRepository repository) {
    this.repository = repository;
  }

  @Override
  public Health health() {
    try {
      var statusCounts = repository.countByStatus();
      long pendingCount = 0;
      long claimedCount = 0;
      long deadLetterCount = 0;

      for (var row : statusCounts) {
        JobStatus status = (JobStatus) row[0];
        long count = (Long) row[1];
        switch (status) {
          case PENDING -> pendingCount = count;
          case CLAIMED -> claimedCount = count;
          case DEAD_LETTER -> deadLetterCount = count;
          default -> {
            // COMPLETED, FAILED — not relevant for health
          }
        }
      }

      Instant oldestPending = repository.findOldestPendingCreatedAt();
      Duration oldestPendingAge =
          oldestPending != null ? Duration.between(oldestPending, Instant.now()) : Duration.ZERO;

      var builder =
          oldestPendingAge.compareTo(MAX_PENDING_AGE) <= 0
              ? Health.up()
              : Health.down().withDetail("reason", "oldest pending job exceeds age threshold");

      return builder
          .withDetail("pendingCount", pendingCount)
          .withDetail("claimedCount", claimedCount)
          .withDetail("deadLetterCount", deadLetterCount)
          .withDetail("oldestPendingAgeSeconds", oldestPendingAge.toSeconds())
          .withDetail("maxPendingAgeSeconds", MAX_PENDING_AGE.toSeconds())
          .build();
    } catch (Exception e) {
      return Health.down().withDetail("error", e.getMessage()).build();
    }
  }
}
