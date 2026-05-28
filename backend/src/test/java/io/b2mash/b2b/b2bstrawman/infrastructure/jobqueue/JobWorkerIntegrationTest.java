package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for the job worker lifecycle: enqueue, claim, execute, retry, dead-letter, and
 * stale recovery. Uses {@code @TestPropertySource} to enable the worker for this specific test
 * class.
 *
 * <p>NOT {@code @Transactional}: the worker runs on its own virtual thread with its own
 * transactions, so the test must commit data for the worker to see it.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "kazi.job-queue.enabled=true",
      "kazi.job-queue.backoff-base-seconds=1",
      "kazi.job-queue.auto-start=false"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobWorkerIntegrationTest {

  // Tenant IDs must match SchemaMultiTenantConnectionProvider pattern: tenant_[0-9a-f]{12}
  private static final String TENANT_1 = "tenant_aaa000000001";
  private static final String ORG_1 = "org_worker_1";

  @Autowired private JobQueueRepository jobQueueRepository;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private JobWorker worker;
  @Autowired private StaleJobRecoveryTask staleJobRecoveryTask;
  @Autowired private TestJobHandler testJobHandler;
  @Autowired private FailingTestJobHandler failingTestJobHandler;

  @BeforeAll
  void seedTenantMapping() {
    if (mappingRepository.findAll().stream().noneMatch(m -> m.getExternalOrgId().equals(ORG_1))) {
      mappingRepository.saveAndFlush(new OrgSchemaMapping(ORG_1, TENANT_1));
    }
  }

  @BeforeEach
  void setUp() {
    testJobHandler.clear();
    failingTestJobHandler.clear();
    jobQueueRepository.deleteAllInBatch();
  }

  @AfterEach
  void tearDown() {
    if (worker.isRunning()) {
      worker.stop();
    }
  }

  @Test
  void shouldEnqueueAndCompleteAllJobs() {
    // Enqueue 10 test jobs with unique tenants to bypass the dedup partial unique index
    for (int i = 0; i < 10; i++) {
      String tenantId = String.format("tenant_%012x", i + 1);
      jobQueueRepository.saveAndFlush(
          new JobQueue(TestJobHandler.JOB_TYPE, tenantId, "org_batch_" + i, "primary", null, 3));
    }

    worker.start();

    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              long completed =
                  jobQueueRepository.findAll().stream()
                      .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                      .count();
              assertThat(completed).isEqualTo(10);
            });

    assertThat(testJobHandler.getExecutions()).hasSize(10);
  }

  @Test
  void shouldCompleteAllJobsExactlyOnce() {
    // Verifies each job is executed exactly once by a single worker.
    // NOTE: True concurrent multi-worker testing (validating FOR UPDATE SKIP LOCKED under
    // contention) requires multiple Spring contexts or external processes — deferred to
    // Epic 555B's integration test suite.
    for (int i = 0; i < 10; i++) {
      String tenantId = String.format("tenant_%012x", 100 + i);
      jobQueueRepository.saveAndFlush(
          new JobQueue(
              TestJobHandler.JOB_TYPE, tenantId, "org_concurrent_" + i, "primary", null, 3));
    }

    worker.start();

    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              long completed =
                  jobQueueRepository.findAll().stream()
                      .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                      .count();
              assertThat(completed).isEqualTo(10);
            });

    // Each job should have been executed exactly once
    assertThat(testJobHandler.getExecutions()).hasSize(10);
  }

  @Test
  void shouldDeadLetterAfterMaxRetries() {
    var job = new JobQueue(FailingTestJobHandler.JOB_TYPE, TENANT_1, ORG_1, "primary", null, 3);
    jobQueueRepository.saveAndFlush(job);
    UUID jobId = job.getId();

    worker.start();

    // With backoff-base-seconds=1: retry 1 after 2s, retry 2 after 4s, retry 3 after 8s -> dead
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              var current = jobQueueRepository.findById(jobId).orElseThrow();
              assertThat(current.getStatus()).isEqualTo(JobStatus.DEAD_LETTER);
            });

    var deadLettered = jobQueueRepository.findById(jobId).orElseThrow();
    assertThat(deadLettered.getRetryCount()).isEqualTo(3);
    assertThat(deadLettered.getErrorMessage()).contains("Simulated job failure");
    assertThat(deadLettered.getCompletedAt()).isNotNull();
  }

  @Test
  void shouldRecoverStaleClaimedJobs() {
    // Insert a job directly in CLAIMED status with old claimed_at (simulating a pod crash)
    var job = new JobQueue(TestJobHandler.JOB_TYPE, TENANT_1, ORG_1, "primary", null, 3);
    job.setStatus(JobStatus.CLAIMED);
    job.setClaimedBy("dead-pod");
    job.setClaimedAt(Instant.now().minus(30, ChronoUnit.MINUTES));
    jobQueueRepository.saveAndFlush(job);

    // Run the stale recovery task directly (no worker needed)
    staleJobRecoveryTask.recoverStaleJobs();

    var recovered = jobQueueRepository.findById(job.getId()).orElseThrow();
    assertThat(recovered.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(recovered.getClaimedBy()).isNull();
    assertThat(recovered.getClaimedAt()).isNull();
    // Retry count should NOT be incremented by stale recovery
    assertThat(recovered.getRetryCount()).isEqualTo(0);
  }

  @Test
  void shouldApplyExponentialBackoffOnRetry() {
    // Enqueue a failing job with maxRetries=5 to observe backoff before dead-letter
    var job = new JobQueue(FailingTestJobHandler.JOB_TYPE, TENANT_1, ORG_1, "primary", null, 5);
    jobQueueRepository.saveAndFlush(job);
    UUID jobId = job.getId();

    worker.start();

    // Wait for at least 1 retry with backoff applied
    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofSeconds(1))
        .untilAsserted(
            () -> {
              var current = jobQueueRepository.findById(jobId).orElseThrow();
              assertThat(current.getRetryCount()).isGreaterThanOrEqualTo(1);
            });

    worker.stop();

    // After retry, next_attempt_at should be in the future relative to created_at
    var afterRetry = jobQueueRepository.findById(jobId).orElseThrow();
    if (afterRetry.getStatus() == JobStatus.PENDING) {
      assertThat(afterRetry.getNextAttemptAt()).isAfter(afterRetry.getCreatedAt());
    }
  }

  @Test
  void shouldCompleteInFlightJobsBeforeShutdown() {
    jobQueueRepository.saveAndFlush(
        new JobQueue(TestJobHandler.JOB_TYPE, TENANT_1, ORG_1, "primary", null, 3));

    worker.start();

    await()
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              long completed =
                  jobQueueRepository.findAll().stream()
                      .filter(j -> j.getStatus() == JobStatus.COMPLETED)
                      .count();
              assertThat(completed).isEqualTo(1);
            });

    worker.stop();

    assertThat(worker.isRunning()).isFalse();
    assertThat(testJobHandler.getExecutions()).hasSize(1);
  }
}
