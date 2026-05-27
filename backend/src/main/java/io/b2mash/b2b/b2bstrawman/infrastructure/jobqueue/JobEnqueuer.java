package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

/**
 * Enqueues jobs into the distributed job queue. The primary entry point for schedulers to fan out
 * work to all tenants.
 */
public interface JobEnqueuer {

  /**
   * Enqueues a single job for a specific tenant.
   *
   * @param jobType the type identifier for this job
   * @param tenantId the tenant schema name
   * @param orgId the external organization ID
   * @param shardId the shard identifier
   * @param payload optional JSONB payload
   */
  void enqueue(
      String jobType, String tenantId, String orgId, String shardId, @Nullable JsonNode payload);

  /**
   * Fans out a job to all registered tenants with default priority (0).
   *
   * @param jobType the type identifier for this job
   * @param payload optional JSONB payload
   * @return the number of jobs actually enqueued (excludes dedup skips)
   */
  int fanOutToAllTenants(String jobType, @Nullable JsonNode payload);

  /**
   * Fans out a job to all registered tenants with a specified priority.
   *
   * @param jobType the type identifier for this job
   * @param payload optional JSONB payload
   * @param priority job priority (higher = processed first)
   * @return the number of jobs actually enqueued (excludes dedup skips)
   */
  int fanOutToAllTenants(String jobType, @Nullable JsonNode payload, int priority);
}
