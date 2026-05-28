package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import java.time.Duration;
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
 * Integration tests for the first 3 scheduler migration batch 1 handlers:
 * AutomationPollTriggersHandler, AutomationPollDelayedHandler, and AccountingSyncDrainHandler.
 * Verifies handler registration, correct jobType, and enqueue-execute lifecycle.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {"kazi.job-queue.enabled=true", "kazi.job-queue.auto-start=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulerMigrationBatch1ATest {

  private static final String TENANT_1 = "tenant_bbb000000001";
  private static final String ORG_1 = "org_batch1a_1";

  @Autowired private JobHandlerRegistry handlerRegistry;
  @Autowired private JobQueueRepository jobQueueRepository;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private JobWorker worker;

  @BeforeAll
  void seedTenantMapping() {
    if (mappingRepository.findAll().stream().noneMatch(m -> m.getExternalOrgId().equals(ORG_1))) {
      mappingRepository.saveAndFlush(new OrgSchemaMapping(ORG_1, TENANT_1));
    }
  }

  @BeforeEach
  void setUp() {
    jobQueueRepository.deleteAllInBatch();
  }

  @AfterEach
  void tearDown() {
    if (worker.isRunning()) {
      worker.stop();
    }
  }

  @Test
  void automationPollTriggersHandlerRegisteredWithCorrectJobType() {
    assertThat(handlerRegistry.getRegisteredTypes()).contains("automation_poll_triggers");

    var handler = handlerRegistry.getHandler("automation_poll_triggers");
    assertThat(handler.jobType()).isEqualTo("automation_poll_triggers");
  }

  @Test
  void automationPollDelayedHandlerRegisteredWithCorrectJobType() {
    assertThat(handlerRegistry.getRegisteredTypes()).contains("automation_poll_delayed");

    var handler = handlerRegistry.getHandler("automation_poll_delayed");
    assertThat(handler.jobType()).isEqualTo("automation_poll_delayed");
  }

  @Test
  void accountingSyncDrainHandlerRegisteredWithCorrectJobType() {
    assertThat(handlerRegistry.getRegisteredTypes()).contains("accounting_sync_drain");

    var handler = handlerRegistry.getHandler("accounting_sync_drain");
    assertThat(handler.jobType()).isEqualTo("accounting_sync_drain");
  }

  @Test
  void enqueueExecuteCycleProcessesBatch1AHandlers() {
    // Enqueue one job per handler type. The handlers will be dispatched by the worker.
    // Since the test tenant has no real schema with tables, handlers may throw (causing
    // dead-letter with maxRetries=1) or complete with no-op. We verify that the worker
    // claims and processes each specific job (no longer PENDING).
    var job1 = new JobQueue("automation_poll_triggers", TENANT_1, ORG_1, "primary", null, 1);
    jobQueueRepository.saveAndFlush(job1);
    var job2 = new JobQueue("automation_poll_delayed", TENANT_1, ORG_1, "primary", null, 1);
    jobQueueRepository.saveAndFlush(job2);
    var job3 = new JobQueue("accounting_sync_drain", TENANT_1, ORG_1, "primary", null, 1);
    jobQueueRepository.saveAndFlush(job3);

    worker.start();

    await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              var j1 = jobQueueRepository.findById(job1.getId()).orElseThrow();
              var j2 = jobQueueRepository.findById(job2.getId()).orElseThrow();
              var j3 = jobQueueRepository.findById(job3.getId()).orElseThrow();
              assertThat(j1.getStatus()).isIn(JobStatus.COMPLETED, JobStatus.DEAD_LETTER);
              assertThat(j2.getStatus()).isIn(JobStatus.COMPLETED, JobStatus.DEAD_LETTER);
              assertThat(j3.getStatus()).isIn(JobStatus.COMPLETED, JobStatus.DEAD_LETTER);
            });
  }
}
