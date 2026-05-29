package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * S2: the worker executes a claimed batch with bounded in-pod parallelism rather than one job at a
 * time. {@code processBatch(batch, parallelism)} is driven directly with an explicit parallelism so
 * the assertion is deterministic and independent of the test connection-pool size (the embedded
 * pool is pinned to 3, which would otherwise cap effective parallelism to 1). The pool-derived
 * guardrail itself is covered by {@link #boundedParallelism_capsAtHalfPoolSize()}. See
 * kazi-infra-review-scheduling-sharding.md finding S2.
 *
 * <p>NOT {@code @Transactional}: each job runs in its own transaction on its own virtual thread.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {"kazi.job-queue.enabled=true", "kazi.job-queue.auto-start=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobWorkerParallelismTest {

  @Autowired private JobQueueRepository jobQueueRepository;
  @Autowired private JobWorker worker;
  @Autowired private SlowConcurrencyTestJobHandler slowHandler;

  @BeforeEach
  void setUp() {
    slowHandler.clear();
    jobQueueRepository.deleteAllInBatch();
  }

  @Test
  void boundedParallelism_capsAtHalfPoolSize() {
    assertThat(JobWorker.boundedParallelism(5, 10)).isEqualTo(5); // min(5, 10/2)
    assertThat(JobWorker.boundedParallelism(5, 3)).isEqualTo(1); // min(5, max(1,3/2))
    assertThat(JobWorker.boundedParallelism(10, 10)).isEqualTo(5); // pool-bound
    assertThat(JobWorker.boundedParallelism(10, 4)).isEqualTo(2);
    assertThat(JobWorker.boundedParallelism(5, null)).isEqualTo(5); // unknown pool → configured
    assertThat(JobWorker.boundedParallelism(0, 10)).isEqualTo(1); // floor of 1
  }

  @Test
  void processesBatchInParallel() {
    int jobCount = 5;
    for (int i = 0; i < jobCount; i++) {
      jobQueueRepository.saveAndFlush(
          new JobQueue(
              SlowConcurrencyTestJobHandler.JOB_TYPE,
              String.format("tenant_%012x", i + 1),
              "org_par_" + i,
              "primary",
              null,
              3));
    }
    List<JobQueue> batch = jobQueueRepository.findAll();

    worker.processBatch(batch, 5);

    assertThat(slowHandler.maxInFlight())
        .as("claimed batch must execute with real parallelism, not one job at a time")
        .isGreaterThanOrEqualTo(2);
    long completed =
        jobQueueRepository.findAll().stream()
            .filter(j -> j.getStatus() == JobStatus.COMPLETED)
            .count();
    assertThat(completed).isEqualTo(jobCount);
  }

  @Test
  void failingJobDoesNotBlockSiblings() {
    // One job that always fails (maxRetries=0 → dead-letters on first failure) ...
    jobQueueRepository.saveAndFlush(
        new JobQueue(
            FailingTestJobHandler.JOB_TYPE, "tenant_0000000000ff", "org_fail", "primary", null, 0));
    // ... alongside several that succeed.
    for (int i = 0; i < 4; i++) {
      jobQueueRepository.saveAndFlush(
          new JobQueue(
              SlowConcurrencyTestJobHandler.JOB_TYPE,
              String.format("tenant_%012x", 200 + i),
              "org_ok_" + i,
              "primary",
              null,
              3));
    }
    List<JobQueue> batch = jobQueueRepository.findAll();

    worker.processBatch(batch, 5);

    long completed =
        jobQueueRepository.findAll().stream()
            .filter(j -> j.getJobType().equals(SlowConcurrencyTestJobHandler.JOB_TYPE))
            .filter(j -> j.getStatus() == JobStatus.COMPLETED)
            .count();
    assertThat(completed).as("a failing sibling must not block the others").isEqualTo(4);

    long deadLettered =
        jobQueueRepository.findAll().stream()
            .filter(j -> j.getJobType().equals(FailingTestJobHandler.JOB_TYPE))
            .filter(j -> j.getStatus() == JobStatus.DEAD_LETTER)
            .count();
    assertThat(deadLettered).isEqualTo(1);
  }
}
