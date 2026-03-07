package io.b2mash.b2b.b2bstrawman.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "action_executions")
public class ActionExecution {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "execution_id", nullable = false)
  private UUID executionId;

  @Column(name = "action_id")
  private UUID actionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ActionExecutionStatus status;

  @Column(name = "scheduled_for")
  private Instant scheduledFor;

  @Column(name = "executed_at")
  private Instant executedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_data", columnDefinition = "jsonb")
  private Map<String, Object> resultData;

  @Column(name = "error_message", length = 2000)
  private String errorMessage;

  @Column(name = "error_detail", columnDefinition = "TEXT")
  private String errorDetail;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ActionExecution() {}

  public ActionExecution(
      UUID executionId, UUID actionId, ActionExecutionStatus status, Instant scheduledFor) {
    this.executionId = executionId;
    this.actionId = actionId;
    this.status = status;
    this.scheduledFor = scheduledFor;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void complete(Map<String, Object> resultData) {
    this.status = ActionExecutionStatus.COMPLETED;
    this.resultData = resultData;
    this.executedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void fail(String errorMessage, String errorDetail) {
    this.status = ActionExecutionStatus.FAILED;
    this.errorMessage = errorMessage;
    this.errorDetail = errorDetail;
    this.executedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void cancel() {
    this.status = ActionExecutionStatus.CANCELLED;
    this.updatedAt = Instant.now();
  }

  /**
   * Stores the automation context for delayed action execution. The context is serialized into the
   * resultData JSONB field and deserialized later by the scheduler.
   */
  public void storeContext(Map<String, Object> context) {
    this.resultData = context;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getExecutionId() {
    return executionId;
  }

  public UUID getActionId() {
    return actionId;
  }

  public ActionExecutionStatus getStatus() {
    return status;
  }

  public Instant getScheduledFor() {
    return scheduledFor;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }

  public Map<String, Object> getResultData() {
    return resultData;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getErrorDetail() {
    return errorDetail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
