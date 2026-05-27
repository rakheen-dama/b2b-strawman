package io.b2mash.b2b.b2bstrawman.infrastructure.jobqueue;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Distributed job queue entry for scheduled tenant-level work. Lives in the {@code public} schema
 * (not tenant-scoped). Workers claim jobs via {@code SELECT FOR UPDATE SKIP LOCKED}.
 */
@Entity
@Table(name = "job_queue", schema = "public")
public class JobQueue {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  private UUID id;

  @Column(name = "job_type", nullable = false, length = 100)
  private String jobType;

  @Column(name = "tenant_id", nullable = false, length = 50)
  private String tenantId;

  @Column(name = "org_id", nullable = false, length = 100)
  private String orgId;

  @Column(name = "shard_id", nullable = false, length = 50)
  private String shardId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private JobStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  private JsonNode payload;

  @Column(name = "priority", nullable = false)
  private int priority;

  @Column(name = "claimed_by", length = 100)
  private String claimedBy;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "max_retries", nullable = false)
  private int maxRetries;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** JPA requires a no-arg constructor. */
  protected JobQueue() {}

  /**
   * Creates a new job queue entry with sensible defaults.
   *
   * @param jobType the type identifier for this job (e.g., "INVOICE_GENERATION")
   * @param tenantId the tenant schema name
   * @param orgId the external organization ID
   * @param shardId the shard identifier (default "primary")
   * @param payload optional JSONB payload for job-type-specific data
   * @param maxRetries maximum retry attempts before dead-lettering
   */
  public JobQueue(
      String jobType,
      String tenantId,
      String orgId,
      String shardId,
      JsonNode payload,
      int maxRetries) {
    this.jobType = Objects.requireNonNull(jobType, "jobType must not be null");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
    this.orgId = Objects.requireNonNull(orgId, "orgId must not be null");
    this.shardId = Objects.requireNonNull(shardId, "shardId must not be null");
    this.payload = payload;
    this.maxRetries = maxRetries;
    this.status = JobStatus.PENDING;
    this.priority = 0;
    this.retryCount = 0;
    this.nextAttemptAt = Instant.now();
    this.createdAt = Instant.now();
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public String getJobType() {
    return jobType;
  }

  public String getTenantId() {
    return tenantId;
  }

  public String getOrgId() {
    return orgId;
  }

  public String getShardId() {
    return shardId;
  }

  public JobStatus getStatus() {
    return status;
  }

  public JsonNode getPayload() {
    return payload;
  }

  public int getPriority() {
    return priority;
  }

  public String getClaimedBy() {
    return claimedBy;
  }

  public Instant getClaimedAt() {
    return claimedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  // --- Mutators (used by worker in Epic 548) ---

  public void setStatus(JobStatus status) {
    this.status = status;
  }

  public void setClaimedBy(String claimedBy) {
    this.claimedBy = claimedBy;
  }

  public void setClaimedAt(Instant claimedAt) {
    this.claimedAt = claimedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public void setRetryCount(int retryCount) {
    this.retryCount = retryCount;
  }

  public void setNextAttemptAt(Instant nextAttemptAt) {
    this.nextAttemptAt = nextAttemptAt;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }
}
