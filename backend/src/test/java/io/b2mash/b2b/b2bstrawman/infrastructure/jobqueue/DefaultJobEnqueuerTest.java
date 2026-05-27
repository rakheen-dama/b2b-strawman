package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import static org.assertj.core.api.Assertions.assertThat;

import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMapping;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class DefaultJobEnqueuerTest {

  @Autowired private JobEnqueuer enqueuer;

  @Autowired private JobQueueRepository jobQueueRepository;

  @Autowired private OrgSchemaMappingRepository mappingRepository;

  @Autowired private JobQueueProperties properties;

  @BeforeEach
  void setUp() {
    // Clear any existing data — both tables for deterministic assertions
    jobQueueRepository.deleteAll();
    jobQueueRepository.flush();
    mappingRepository.deleteAll();
    mappingRepository.flush();

    // Seed 3 test tenant mappings
    mappingRepository.saveAndFlush(new OrgSchemaMapping("org_enq_1", "tenant_enq_1"));
    mappingRepository.saveAndFlush(new OrgSchemaMapping("org_enq_2", "tenant_enq_2"));
    mappingRepository.saveAndFlush(new OrgSchemaMapping("org_enq_3", "tenant_enq_3"));
  }

  @Test
  void fanOutToAllTenantsShouldEnqueueOneJobPerTenant() {
    int enqueued = enqueuer.fanOutToAllTenants("TEST_JOB", null);

    assertThat(enqueued).isEqualTo(3);

    var jobs = jobQueueRepository.findAll();
    var testJobs = jobs.stream().filter(j -> j.getJobType().equals("TEST_JOB")).toList();
    assertThat(testJobs).hasSize(3);
    assertThat(testJobs)
        .extracting(JobQueue::getTenantId)
        .contains("tenant_enq_1", "tenant_enq_2", "tenant_enq_3");
    assertThat(testJobs)
        .allSatisfy(
            j -> {
              assertThat(j.getStatus()).isEqualTo(JobStatus.PENDING);
              assertThat(j.getMaxRetries()).isEqualTo(properties.getMaxRetriesDefault());
            });
  }

  @Test
  void fanOutToAllTenantsShouldDedupOnSecondCall() {
    // First enqueue
    int first = enqueuer.fanOutToAllTenants("DEDUP_JOB", null);
    assertThat(first).isEqualTo(3);

    // Second enqueue — all jobs are still PENDING, so dedup pre-filter should skip all
    int second = enqueuer.fanOutToAllTenants("DEDUP_JOB", null);
    assertThat(second).isEqualTo(0);

    // Total count should be unchanged
    long totalDedupJobs =
        jobQueueRepository.findAll().stream()
            .filter(j -> j.getJobType().equals("DEDUP_JOB"))
            .count();
    assertThat(totalDedupJobs).isEqualTo(first);
  }

  @Test
  void fanOutShouldEnqueueForCompletedTenantAfterCompletion() {
    // First enqueue
    enqueuer.fanOutToAllTenants("REQUEUE_JOB", null);

    // Mark one job as COMPLETED
    var jobs =
        jobQueueRepository.findAll().stream()
            .filter(
                j -> j.getJobType().equals("REQUEUE_JOB") && j.getTenantId().equals("tenant_enq_1"))
            .toList();
    assertThat(jobs).hasSize(1);
    jobs.getFirst().setStatus(JobStatus.COMPLETED);
    jobQueueRepository.saveAndFlush(jobs.getFirst());

    // Re-enqueue — should create exactly 1 new job for tenant_enq_1
    int requeued = enqueuer.fanOutToAllTenants("REQUEUE_JOB", null);
    assertThat(requeued).isEqualTo(1);

    // Verify there are now 2 REQUEUE_JOB entries for tenant_enq_1 (1 COMPLETED + 1 PENDING)
    long tenant1Jobs =
        jobQueueRepository.findAll().stream()
            .filter(
                j -> j.getJobType().equals("REQUEUE_JOB") && j.getTenantId().equals("tenant_enq_1"))
            .count();
    assertThat(tenant1Jobs).isEqualTo(2);
  }

  @Test
  void enqueueSingleJobShouldDedupOnSecondCall() {
    enqueuer.enqueue("SINGLE_JOB", "tenant_enq_1", "org_enq_1", "primary", null);

    // Second enqueue for same type + tenant — dedup index should prevent duplicate
    enqueuer.enqueue("SINGLE_JOB", "tenant_enq_1", "org_enq_1", "primary", null);

    long count =
        jobQueueRepository.findAll().stream()
            .filter(
                j -> j.getJobType().equals("SINGLE_JOB") && j.getTenantId().equals("tenant_enq_1"))
            .count();
    assertThat(count).isEqualTo(1);
  }

  @Test
  void propertiesShouldBindFromTestYaml() {
    // application-test.yml sets enabled: false and poll-interval-ms: 100
    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.getPollIntervalMs()).isEqualTo(100);
    // Defaults for fields not overridden in test profile
    assertThat(properties.getBatchSize()).isEqualTo(20);
    assertThat(properties.getMaxRetriesDefault()).isEqualTo(3);
    assertThat(properties.getBackoffBaseSeconds()).isEqualTo(10);
    assertThat(properties.getStaleClaimTimeoutMinutes()).isEqualTo(15);
  }

  @Test
  void isDualModeShouldReturnCorrectValues() {
    // No dual-mode entries configured in test — should default to false
    assertThat(properties.isDualMode("UNKNOWN_JOB")).isFalse();
    assertThat(properties.isDualMode("INVOICE_GENERATION")).isFalse();
  }
}
