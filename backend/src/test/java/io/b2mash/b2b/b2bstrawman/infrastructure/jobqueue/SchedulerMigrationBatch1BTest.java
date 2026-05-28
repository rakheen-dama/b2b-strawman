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
 * Characterization tests for all 5 batch 1 scheduler migration handlers. Verifies handler
 * registration, the full handler set, enqueue-execute lifecycle for the remaining 2 handlers, and
 * dual-mode toggle behaviour.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "kazi.job-queue.enabled=true",
      "kazi.job-queue.auto-start=false",
      "kazi.job-queue.dual-mode.accounting_sync_drain=false"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulerMigrationBatch1BTest {

  private static final String TENANT_1 = "tenant_ccc000000001";
  private static final String ORG_1 = "org_batch1b_1";

  @Autowired private JobHandlerRegistry handlerRegistry;
  @Autowired private JobQueueRepository jobQueueRepository;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private JobWorker worker;
  @Autowired private JobQueueProperties jobQueueProperties;

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
  void accountingPaymentPollHandlerRegisteredCorrectly() {
    assertThat(handlerRegistry.getRegisteredTypes()).contains("accounting_payment_poll");

    var handler = handlerRegistry.getHandler("accounting_payment_poll");
    assertThat(handler.jobType()).isEqualTo("accounting_payment_poll");
  }

  @Test
  void timeReminderHandlerRegisteredCorrectly() {
    assertThat(handlerRegistry.getRegisteredTypes()).contains("time_reminder_check");

    var handler = handlerRegistry.getHandler("time_reminder_check");
    assertThat(handler.jobType()).isEqualTo("time_reminder_check");
  }

  @Test
  void allFiveBatch1HandlersPresent() {
    Set<String> expectedTypes =
        Set.of(
            "automation_poll_triggers",
            "automation_poll_delayed",
            "accounting_sync_drain",
            "accounting_payment_poll",
            "time_reminder_check");

    assertThat(handlerRegistry.getRegisteredTypes()).containsAll(expectedTypes);
  }

  @Test
  void enqueueExecuteCycleProcessesBatch1BHandlers() {
    // Enqueue one job per handler type. Since the test tenant has no real schema with tables,
    // handlers may throw (causing dead-letter with maxRetries=1) or complete with no-op.
    // We verify that the worker claims and processes each specific job.
    var job1 = new JobQueue("accounting_payment_poll", TENANT_1, ORG_1, "primary", null, 1);
    jobQueueRepository.saveAndFlush(job1);
    var job2 = new JobQueue("time_reminder_check", TENANT_1, ORG_1, "primary", null, 1);
    jobQueueRepository.saveAndFlush(job2);

    worker.start();

    await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              var j1 = jobQueueRepository.findById(job1.getId()).orElseThrow();
              var j2 = jobQueueRepository.findById(job2.getId()).orElseThrow();
              assertThat(j1.getStatus()).isIn(JobStatus.COMPLETED, JobStatus.DEAD_LETTER);
              assertThat(j2.getStatus()).isIn(JobStatus.COMPLETED, JobStatus.DEAD_LETTER);
            });
  }

  @Test
  void dualModeConfigFlagTogglesOldPathExecution() {
    // Batch 1 dual-mode entries were removed from application-test.yml after cleanup (550B.8).
    // Batch 1 schedulers no longer check isDualMode — they delegate to fanOutToAllTenants directly.
    // The @TestPropertySource override still applies.
    assertThat(jobQueueProperties.isDualMode("accounting_sync_drain")).isFalse();

    // Batch 1 types no longer have dual-mode entries — default is false
    assertThat(jobQueueProperties.isDualMode("automation_poll_triggers")).isFalse();
    assertThat(jobQueueProperties.isDualMode("automation_poll_delayed")).isFalse();
    assertThat(jobQueueProperties.isDualMode("accounting_payment_poll")).isFalse();
    assertThat(jobQueueProperties.isDualMode("time_reminder_check")).isFalse();

    // An unconfigured job type should still default to false
    assertThat(jobQueueProperties.isDualMode("some_unknown_type")).isFalse();
  }
}
