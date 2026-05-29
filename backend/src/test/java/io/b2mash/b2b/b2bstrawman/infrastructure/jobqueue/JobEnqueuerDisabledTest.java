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

/**
 * S1 regression: when {@code kazi.job-queue.enabled=false} (the default in {@code
 * application-test.yml}) the enqueuer must NOT write rows that nothing will ever drain — the worker
 * bean is not even created. Without the guard, schedulers calling {@code fanOutToAllTenants} during
 * a dual-mode rollout would silently fill {@code public.job_queue}. See
 * kazi-infra-review-scheduling-sharding.md finding S1.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class JobEnqueuerDisabledTest {

  @Autowired private JobEnqueuer enqueuer;
  @Autowired private JobQueueRepository jobQueueRepository;
  @Autowired private OrgSchemaMappingRepository mappingRepository;
  @Autowired private JobQueueProperties properties;

  @BeforeEach
  void setUp() {
    jobQueueRepository.deleteAll();
    jobQueueRepository.flush();
    mappingRepository.deleteAll();
    mappingRepository.flush();
    mappingRepository.saveAndFlush(new OrgSchemaMapping("org_disabled_1", "tenant_disabled_1"));
    mappingRepository.saveAndFlush(new OrgSchemaMapping("org_disabled_2", "tenant_disabled_2"));
  }

  @Test
  void queueDisabled_isThePreconditionForThisTest() {
    assertThat(properties.isEnabled()).isFalse();
  }

  @Test
  void fanOutToAllTenants_whenDisabled_enqueuesNothing() {
    int enqueued = enqueuer.fanOutToAllTenants("DISABLED_FANOUT_JOB", null);

    assertThat(enqueued).as("fan-out must no-op when the queue is disabled").isZero();
    assertThat(jobQueueRepository.findAll())
        .as("no job rows may be written when the queue is disabled")
        .noneMatch(j -> j.getJobType().equals("DISABLED_FANOUT_JOB"));
  }

  @Test
  void enqueueSingleJob_whenDisabled_enqueuesNothing() {
    enqueuer.enqueue("DISABLED_SINGLE_JOB", "tenant_disabled_1", "org_disabled_1", "primary", null);

    assertThat(jobQueueRepository.findAll())
        .as("no job rows may be written when the queue is disabled")
        .noneMatch(j -> j.getJobType().equals("DISABLED_SINGLE_JOB"));
  }
}
