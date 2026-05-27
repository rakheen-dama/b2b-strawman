package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.b2mash.b2b.b2bstrawman.TestcontainersConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class JobQueueEntityTest {

  @Autowired private JobQueueRepository jobQueueRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldPersistAndRetrieveJobQueueEntity() {
    var job = new JobQueue("INVOICE_GENERATION", "tenant_abc", "org_123", "primary", null, 3);

    var saved = jobQueueRepository.saveAndFlush(job);

    var found = jobQueueRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getJobType()).isEqualTo("INVOICE_GENERATION");
    assertThat(found.getTenantId()).isEqualTo("tenant_abc");
    assertThat(found.getOrgId()).isEqualTo("org_123");
    assertThat(found.getShardId()).isEqualTo("primary");
    assertThat(found.getPayload()).isNull();
    assertThat(found.getMaxRetries()).isEqualTo(3);
    assertThat(found.getId()).isNotNull();
  }

  @Test
  void shouldHaveCorrectDefaults() {
    var job = new JobQueue("SYNC_CONTACTS", "tenant_xyz", "org_456", "primary", null, 3);

    var saved = jobQueueRepository.saveAndFlush(job);

    var found = jobQueueRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(found.getRetryCount()).isEqualTo(0);
    assertThat(found.getShardId()).isEqualTo("primary");
    assertThat(found.getPriority()).isEqualTo(0);
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getNextAttemptAt()).isNotNull();
    assertThat(found.getClaimedBy()).isNull();
    assertThat(found.getClaimedAt()).isNull();
    assertThat(found.getCompletedAt()).isNull();
    assertThat(found.getErrorMessage()).isNull();
  }

  @Test
  void shouldRoundTripJsonbPayload() {
    ObjectNode payload = objectMapper.createObjectNode();
    payload.put("invoiceId", "INV-001");
    payload.put("amount", 49900);
    payload.put("currency", "ZAR");

    var job = new JobQueue("INVOICE_GENERATION", "tenant_abc", "org_123", "primary", payload, 3);

    var saved = jobQueueRepository.saveAndFlush(job);

    var found = jobQueueRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getPayload()).isNotNull();
    assertThat(found.getPayload().get("invoiceId").asText()).isEqualTo("INV-001");
    assertThat(found.getPayload().get("amount").asInt()).isEqualTo(49900);
    assertThat(found.getPayload().get("currency").asText()).isEqualTo("ZAR");
  }

  @Test
  void shouldHaveShedLockStaleJobRecoveryRow() {
    // The V24 migration seeds a 'stale_job_recovery' row in the shedlock table.
    // Verify it exists (migration ran successfully).
    var count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM public.shedlock WHERE name = 'stale_job_recovery'",
            Integer.class);
    assertThat(count).isEqualTo(1);
  }

  @Test
  void shouldFindClaimableJobsOrderedByPriorityAndNextAttempt() {
    var highPriority = new JobQueue("REPORT_GEN", "tenant_a", "org_a", "primary", null, 3);
    highPriority.setPriority(10);
    highPriority.setNextAttemptAt(Instant.now().minusSeconds(60));

    var lowPriority = new JobQueue("REPORT_GEN", "tenant_b", "org_b", "primary", null, 3);
    lowPriority.setPriority(1);
    lowPriority.setNextAttemptAt(Instant.now().minusSeconds(120));

    var futureJob = new JobQueue("REPORT_GEN", "tenant_c", "org_c", "primary", null, 3);
    futureJob.setNextAttemptAt(Instant.now().plusSeconds(3600));

    jobQueueRepository.saveAndFlush(highPriority);
    jobQueueRepository.saveAndFlush(lowPriority);
    jobQueueRepository.saveAndFlush(futureJob);

    // findClaimable uses FOR UPDATE SKIP LOCKED — need a non-transactional context
    // for the locking to work. In a @Transactional test, we can at least verify
    // the ordering and filtering logic.
    var claimable = jobQueueRepository.findClaimable(10);

    // Future job should be excluded (next_attempt_at > NOW())
    assertThat(claimable).extracting(JobQueue::getTenantId).doesNotContain("tenant_c");
    // High priority should come first
    if (claimable.size() >= 2) {
      assertThat(claimable.get(0).getPriority())
          .isGreaterThanOrEqualTo(claimable.get(1).getPriority());
    }
  }

  @Test
  void shouldFindStaleClaimedJobs() {
    var staleJob = new JobQueue("STALE_TEST", "tenant_stale", "org_stale", "primary", null, 3);
    staleJob.setStatus(JobStatus.CLAIMED);
    staleJob.setClaimedAt(Instant.now().minusSeconds(3600));
    staleJob.setClaimedBy("worker-1");

    var freshJob = new JobQueue("STALE_TEST", "tenant_fresh", "org_fresh", "primary", null, 3);
    freshJob.setStatus(JobStatus.CLAIMED);
    freshJob.setClaimedAt(Instant.now());
    freshJob.setClaimedBy("worker-2");

    jobQueueRepository.saveAndFlush(staleJob);
    jobQueueRepository.saveAndFlush(freshJob);

    var threshold = Instant.now().minusSeconds(900); // 15 minutes ago
    var staleJobs = jobQueueRepository.findStaleClaimed(threshold);

    assertThat(staleJobs).extracting(JobQueue::getTenantId).contains("tenant_stale");
    assertThat(staleJobs).extracting(JobQueue::getTenantId).doesNotContain("tenant_fresh");
  }
}
