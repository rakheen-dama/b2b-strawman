package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import java.time.Duration;
import java.util.Set;
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
 * Integration tests for the 7 batch 2A scheduler migration handlers. Verifies handler registration,
 * the combined 12-handler set (5 batch 1 + 7 batch 2A), and enqueue-execute lifecycle for all 7 new
 * handlers.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "kazi.job-queue.enabled=true",
      "kazi.job-queue.auto-start=false",
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulerMigrationBatch2ATest {

  private static final String TENANT_1 = "tenant_ddd000000001";
  private static final String ORG_1 = "org_batch2a_1";

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
  void allSevenBatch2AHandlersRegisteredWithCorrectJobTypes() {
    Set<String> expectedTypes =
        Set.of(
            "recurring_schedule_execute",
            "dormancy_check",
            "proposal_expiry",
            "acceptance_expiry",
            "magic_link_cleanup",
            "ai_gate_expiry",
            "field_date_scan");

    assertThat(handlerRegistry.getRegisteredTypes()).containsAll(expectedTypes);

    // Verify each handler returns the correct jobType
    for (String type : expectedTypes) {
      var handler = handlerRegistry.getHandler(type);
      assertThat(handler.jobType()).isEqualTo(type);
    }
  }

  @Test
  void allTwelveHandlersRegisteredInRegistry() {
    Set<String> allExpectedTypes =
        Set.of(
            // Batch 1 (5)
            "automation_poll_triggers",
            "automation_poll_delayed",
            "accounting_sync_drain",
            "accounting_payment_poll",
            "time_reminder_check",
            // Batch 2A (7)
            "recurring_schedule_execute",
            "dormancy_check",
            "proposal_expiry",
            "acceptance_expiry",
            "magic_link_cleanup",
            "ai_gate_expiry",
            "field_date_scan");

    assertThat(handlerRegistry.getRegisteredTypes()).containsAll(allExpectedTypes);
    assertThat(handlerRegistry.getRegisteredTypes()).hasSizeGreaterThanOrEqualTo(12);
  }

  @Test
  void enqueueExecuteCycleProcessesBatch2AHandlers() {
    // Enqueue one job per handler type. Since the test tenant has no real schema with tables,
    // handlers may throw (causing dead-letter with maxRetries=1) or complete with no-op.
    // We verify that the worker claims and processes each specific job.
    var job1 = new JobQueue("recurring_schedule_execute", TENANT_1, ORG_1, "primary", null, 1);
    var job2 = new JobQueue("dormancy_check", TENANT_1, ORG_1, "primary", null, 1);
    var job3 = new JobQueue("proposal_expiry", TENANT_1, ORG_1, "primary", null, 1);
    var job4 = new JobQueue("acceptance_expiry", TENANT_1, ORG_1, "primary", null, 1);
    var job5 = new JobQueue("magic_link_cleanup", TENANT_1, ORG_1, "primary", null, 1);
    var job6 = new JobQueue("ai_gate_expiry", TENANT_1, ORG_1, "primary", null, 1);
    var job7 = new JobQueue("field_date_scan", TENANT_1, ORG_1, "primary", null, 1);

    jobQueueRepository.saveAndFlush(job1);
    jobQueueRepository.saveAndFlush(job2);
    jobQueueRepository.saveAndFlush(job3);
    jobQueueRepository.saveAndFlush(job4);
    jobQueueRepository.saveAndFlush(job5);
    jobQueueRepository.saveAndFlush(job6);
    jobQueueRepository.saveAndFlush(job7);

    worker.start();

    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              var j1 = jobQueueRepository.findById(job1.getId()).orElseThrow();
              var j2 = jobQueueRepository.findById(job2.getId()).orElseThrow();
              var j3 = jobQueueRepository.findById(job3.getId()).orElseThrow();
              var j4 = jobQueueRepository.findById(job4.getId()).orElseThrow();
              var j5 = jobQueueRepository.findById(job5.getId()).orElseThrow();
              var j6 = jobQueueRepository.findById(job6.getId()).orElseThrow();
              var j7 = jobQueueRepository.findById(job7.getId()).orElseThrow();

              assertThat(j1.getStatus()).isIn(JobStatus.COMPLETED, JobStatus.DEAD_LETTER);
              assertThat(j2.getStatus()).isIn(JobStatus.COMPLETED, JobStatus.DEAD_LETTER);
              assertThat(j3.getStatus()).isIn(JobStatus.COMPLETED, JobStatus.DEAD_LETTER);
              assertThat(j4.getStatus()).isIn(JobStatus.COMPLETED, JobStatus.DEAD_LETTER);
              assertThat(j5.getStatus()).isIn(JobStatus.COMPLETED, JobStatus.DEAD_LETTER);
              assertThat(j6.getStatus()).isIn(JobStatus.COMPLETED, JobStatus.DEAD_LETTER);
              assertThat(j7.getStatus()).isIn(JobStatus.COMPLETED, JobStatus.DEAD_LETTER);
            });
  }
}
