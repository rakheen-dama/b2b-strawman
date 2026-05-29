package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import com.fasterxml.jackson.databind.JsonNode;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default implementation of {@link JobEnqueuer}. Reads all tenant mappings and creates job queue
 * entries, using a pre-filter and the dedup index as a safety net against double-enqueue.
 */
@Component
public class DefaultJobEnqueuer implements JobEnqueuer {

  private static final Logger log = LoggerFactory.getLogger(DefaultJobEnqueuer.class);
  private static final String DEFAULT_SHARD_ID = "primary";

  private final OrgSchemaMappingRepository mappingRepository;
  private final JobQueueRepository jobQueueRepository;
  private final JobQueueProperties properties;
  private final @Nullable JobQueueMetrics metrics;

  public DefaultJobEnqueuer(
      OrgSchemaMappingRepository mappingRepository,
      JobQueueRepository jobQueueRepository,
      JobQueueProperties properties,
      @Nullable JobQueueMetrics metrics) {
    this.mappingRepository = mappingRepository;
    this.jobQueueRepository = jobQueueRepository;
    this.properties = properties;
    this.metrics = metrics;
  }

  @Override
  @Transactional
  public void enqueue(
      String jobType, String tenantId, String orgId, String shardId, @Nullable JsonNode payload) {
    Objects.requireNonNull(jobType, "jobType must not be null");
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    Objects.requireNonNull(orgId, "orgId must not be null");

    // Pre-filter: skip if an active job already exists for this type + tenant
    var activeTenantIds = jobQueueRepository.findActiveTenantIdsByJobType(jobType);
    if (activeTenantIds.contains(tenantId)) {
      log.debug("Dedup pre-filter: job already active for type={}, tenant={}", jobType, tenantId);
      return;
    }

    var job =
        new JobQueue(
            jobType,
            tenantId,
            orgId,
            shardIdOrDefault(shardId),
            payload,
            properties.getMaxRetriesDefault());

    try {
      jobQueueRepository.saveAndFlush(job);
      if (metrics != null) {
        metrics.recordEnqueued(jobType);
      }
      log.debug("Enqueued job: type={}, tenant={}", jobType, tenantId);
    } catch (DataIntegrityViolationException e) {
      log.debug("Dedup: job already active for type={}, tenant={} — skipped", jobType, tenantId);
    }
  }

  @Override
  @Transactional
  public int fanOutToAllTenants(String jobType, @Nullable JsonNode payload) {
    return fanOutToAllTenants(jobType, payload, 0);
  }

  @Override
  @Transactional
  public int fanOutToAllTenants(String jobType, @Nullable JsonNode payload, int priority) {
    Objects.requireNonNull(jobType, "jobType must not be null");

    // Step 1: Pre-filter — find tenants that already have an active job of this type
    var activeTenantIds = jobQueueRepository.findActiveTenantIdsByJobType(jobType);

    // Step 2: Build entities for tenants NOT in the skip set
    var allMappings = mappingRepository.findAll();
    var newJobs = new ArrayList<JobQueue>();
    int skippedByPreFilter = 0;

    for (var mapping : allMappings) {
      String tenantId = mapping.getSchemaName();
      if (activeTenantIds.contains(tenantId)) {
        log.debug("Dedup pre-filter: skipping type={}, tenant={}", jobType, tenantId);
        skippedByPreFilter++;
        continue;
      }

      var job =
          new JobQueue(
              jobType,
              tenantId,
              mapping.getExternalOrgId(),
              shardIdOrDefault(mapping.getShardId()),
              payload,
              properties.getMaxRetriesDefault());
      job.setPriority(priority);
      newJobs.add(job);
    }

    if (newJobs.isEmpty()) {
      log.debug("fanOutToAllTenants: no new jobs to enqueue for type={}", jobType);
      return 0;
    }

    // Step 3: Save one-by-one with individual dedup handling.
    // This avoids the batch saveAll() problem where a DataIntegrityViolationException
    // marks the entire transaction rollback-only, preventing the fallback from working.
    int enqueued = 0;
    int skippedByDedup = 0;
    for (var job : newJobs) {
      try {
        jobQueueRepository.saveAndFlush(job);
        if (metrics != null) {
          metrics.recordEnqueued(jobType);
        }
        enqueued++;
      } catch (DataIntegrityViolationException e) {
        log.debug(
            "Dedup: job already active for type={}, tenant={} — skipped",
            jobType,
            job.getTenantId());
        skippedByDedup++;
      }
    }

    log.info(
        "fanOutToAllTenants: type={}, tenants={}, enqueued={}, skippedByPreFilter={}, skippedByDedup={}",
        jobType,
        allMappings.size(),
        enqueued,
        skippedByPreFilter,
        skippedByDedup);

    return enqueued;
  }

  /**
   * Falls back to {@link #DEFAULT_SHARD_ID} only when the tenant has no shard assignment (legacy
   * rows predating sharding). A tenant on a secondary shard MUST keep its real shard id so the
   * worker routes execution to the correct database — see {@code JobWorker#processJob} and the
   * D1/D2 findings in kazi-infra-review-scheduling-sharding.md.
   */
  private static String shardIdOrDefault(@Nullable String shardId) {
    return shardId != null && !shardId.isBlank() ? shardId : DEFAULT_SHARD_ID;
  }
}
