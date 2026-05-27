package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link JobEnqueuer}. Reads all tenant mappings and creates job queue
 * entries, using a pre-filter and the dedup index as a safety net against double-enqueue.
 */
@Component
@EnableConfigurationProperties(JobQueueProperties.class)
public class DefaultJobEnqueuer implements JobEnqueuer {

  private static final Logger log = LoggerFactory.getLogger(DefaultJobEnqueuer.class);
  private static final String DEFAULT_SHARD_ID = "primary";

  private final OrgSchemaMappingRepository mappingRepository;
  private final JobQueueRepository jobQueueRepository;
  private final JobQueueProperties properties;

  public DefaultJobEnqueuer(
      OrgSchemaMappingRepository mappingRepository,
      JobQueueRepository jobQueueRepository,
      JobQueueProperties properties) {
    this.mappingRepository = mappingRepository;
    this.jobQueueRepository = jobQueueRepository;
    this.properties = properties;
  }

  @Override
  public void enqueue(
      String jobType, String tenantId, String orgId, String shardId, @Nullable JsonNode payload) {
    Objects.requireNonNull(jobType, "jobType must not be null");
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(orgId, "orgId must not be null");

    // Pre-filter: skip if an active job already exists for this type + tenant
    var activeTenantIds = new HashSet<>(jobQueueRepository.findActiveTenantIdsByJobType(jobType));
    if (activeTenantIds.contains(tenantId)) {
      log.debug("Dedup pre-filter: job already active for type={}, tenant={}", jobType, tenantId);
      return;
    }

    var job =
        new JobQueue(
            jobType,
            tenantId,
            orgId,
            shardId != null ? shardId : DEFAULT_SHARD_ID,
            payload,
            properties.getMaxRetriesDefault());

    try {
      jobQueueRepository.saveAndFlush(job);
      log.debug("Enqueued job: type={}, tenant={}", jobType, tenantId);
    } catch (DataIntegrityViolationException e) {
      log.debug("Dedup: job already active for type={}, tenant={} — skipped", jobType, tenantId);
    }
  }

  @Override
  public int fanOutToAllTenants(String jobType, @Nullable JsonNode payload) {
    return fanOutToAllTenants(jobType, payload, 0);
  }

  @Override
  public int fanOutToAllTenants(String jobType, @Nullable JsonNode payload, int priority) {
    Objects.requireNonNull(jobType, "jobType must not be null");

    // Step 1: Pre-filter — find tenants that already have an active job of this type
    var activeTenantIds = new HashSet<>(jobQueueRepository.findActiveTenantIdsByJobType(jobType));

    // Step 2: Build entities for tenants NOT in the skip set
    var allMappings = mappingRepository.findAll();
    var newJobs = new ArrayList<JobQueue>();

    for (var mapping : allMappings) {
      String tenantId = mapping.getSchemaName();
      if (activeTenantIds.contains(tenantId)) {
        log.debug("Dedup pre-filter: skipping type={}, tenant={}", jobType, tenantId);
        continue;
      }

      var job =
          new JobQueue(
              jobType,
              tenantId,
              mapping.getExternalOrgId(),
              DEFAULT_SHARD_ID,
              payload,
              properties.getMaxRetriesDefault());
      job.setPriority(priority);
      newJobs.add(job);
    }

    if (newJobs.isEmpty()) {
      log.debug("fanOutToAllTenants: no new jobs to enqueue for type={}", jobType);
      return 0;
    }

    // Step 3: Batch save — race-condition duplicates hit the unique index
    int enqueued = saveWithDedupFallback(jobType, newJobs);

    log.info(
        "fanOutToAllTenants: type={}, tenants={}, enqueued={}, skipped={}",
        jobType,
        allMappings.size(),
        enqueued,
        allMappings.size() - enqueued);

    return enqueued;
  }

  /**
   * Attempts batch save first. If a DataIntegrityViolationException occurs (race condition), falls
   * back to one-by-one inserts with individual dedup handling.
   */
  private int saveWithDedupFallback(String jobType, List<JobQueue> jobs) {
    try {
      jobQueueRepository.saveAll(jobs);
      jobQueueRepository.flush();
      return jobs.size();
    } catch (DataIntegrityViolationException e) {
      log.debug("Batch save hit dedup conflict for type={}, falling back to one-by-one", jobType);
      return saveOneByOne(jobType, jobs);
    }
  }

  private int saveOneByOne(String jobType, List<JobQueue> jobs) {
    int enqueued = 0;
    for (var job : jobs) {
      try {
        jobQueueRepository.saveAndFlush(job);
        enqueued++;
      } catch (DataIntegrityViolationException e) {
        log.debug(
            "Dedup: job already active for type={}, tenant={} — skipped",
            jobType,
            job.getTenantId());
      }
    }
    return enqueued;
  }
}
