package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.ShardHealthIndicator;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for observability: Micrometer metrics and health indicators for the job queue
 * and shard subsystems. Verifies that counters, gauges, timers, and health checks are correctly
 * registered and functional.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "kazi.sharding.enabled=true",
      "kazi.job-queue.enabled=true",
      "kazi.job-queue.auto-start=false"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ObservabilityTest {

  private static final String ORG_ID = "org_obs_test";

  private final MeterRegistry meterRegistry;
  private final JobQueueMetrics jobQueueMetrics;
  private final JobQueueHealthIndicator jobQueueHealthIndicator;
  private final ShardHealthIndicator shardHealthIndicator;
  private final JobQueueRepository jobQueueRepository;
  private final OrgSchemaMappingRepository mappingRepository;

  @Autowired
  ObservabilityTest(
      MeterRegistry meterRegistry,
      JobQueueMetrics jobQueueMetrics,
      JobQueueHealthIndicator jobQueueHealthIndicator,
      ShardHealthIndicator shardHealthIndicator,
      JobQueueRepository jobQueueRepository,
      OrgSchemaMappingRepository mappingRepository) {
    this.meterRegistry = meterRegistry;
    this.jobQueueMetrics = jobQueueMetrics;
    this.jobQueueHealthIndicator = jobQueueHealthIndicator;
    this.shardHealthIndicator = shardHealthIndicator;
    this.jobQueueRepository = jobQueueRepository;
    this.mappingRepository = mappingRepository;
  }

  @BeforeAll
  void setUp() {
    // Ensure a tenant mapping exists so gauge refresh has data to poll
    if (mappingRepository.findByExternalOrgId(ORG_ID).isEmpty()) {
      mappingRepository.saveAndFlush(new OrgSchemaMapping(ORG_ID, "tenant_0b5000000001"));
    }
  }

  @Test
  void prometheusMetrics_includeJobQueueGauges() {
    // Trigger a manual gauge refresh (scheduling disabled in test profile)
    jobQueueMetrics.refreshGauges();

    var pendingGauge = meterRegistry.find("kazi_job_queue_pending_count").gauge();
    assertThat(pendingGauge).isNotNull();

    var claimedGauge = meterRegistry.find("kazi_job_queue_claimed_count").gauge();
    assertThat(claimedGauge).isNotNull();
  }

  @Test
  void healthCheck_includesJobQueueHealth() {
    Health health = jobQueueHealthIndicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsKey("pendingCount");
    assertThat(health.getDetails()).containsKey("claimedCount");
    assertThat(health.getDetails()).containsKey("deadLetterCount");
    assertThat(health.getDetails()).containsKey("oldestPendingAgeSeconds");
    assertThat(health.getDetails()).containsKey("maxPendingAgeSeconds");
  }

  @Test
  void healthCheck_includesShardHealth() {
    Health health = shardHealthIndicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsKey("activeShards");
    assertThat(health.getDetails().get("shard_primary")).isEqualTo("UP");
  }

  @Test
  void counterIncrements_whenJobCompletes() {
    // Create and persist a PENDING job
    var job = new JobQueue("OBS_TEST_JOB", "tenant_0b5000000001", ORG_ID, "primary", null, 3);
    job = jobQueueRepository.saveAndFlush(job);

    // Simulate claim
    job.setStatus(JobStatus.CLAIMED);
    Instant claimedAt = Instant.now();
    job.setClaimedAt(claimedAt);
    job.setClaimedBy("test-pod");
    jobQueueRepository.saveAndFlush(job);

    // Record completion via metrics (as JobWorker does)
    jobQueueMetrics.recordCompleted("OBS_TEST_JOB", job.getCreatedAt(), claimedAt);

    // Verify counter was incremented
    var completedCounter =
        meterRegistry
            .find("kazi_job_queue_completed_total")
            .tag("job_type", "OBS_TEST_JOB")
            .counter();
    assertThat(completedCounter).isNotNull();
    assertThat(completedCounter.count()).isGreaterThanOrEqualTo(1.0);

    // Verify execution timer was recorded
    var executionTimer =
        meterRegistry
            .find("kazi_job_queue_execution_seconds")
            .tag("job_type", "OBS_TEST_JOB")
            .timer();
    assertThat(executionTimer).isNotNull();
    assertThat(executionTimer.count()).isGreaterThanOrEqualTo(1);

    // Clean up
    jobQueueRepository.delete(job);
    jobQueueRepository.flush();
  }
}
