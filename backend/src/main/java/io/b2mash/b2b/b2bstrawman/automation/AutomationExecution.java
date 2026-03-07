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
@Table(name = "automation_executions")
public class AutomationExecution {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "rule_id", nullable = false)
  private UUID ruleId;

  @Column(name = "trigger_event_type", nullable = false, length = 100)
  private String triggerEventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "trigger_event_data", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> triggerEventData;

  @Column(name = "conditions_met", nullable = false)
  private boolean conditionsMet;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private ExecutionStatus status;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "error_message", length = 2000)
  private String errorMessage;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AutomationExecution() {}

  public AutomationExecution(
      UUID ruleId,
      String triggerEventType,
      Map<String, Object> triggerEventData,
      boolean conditionsMet,
      ExecutionStatus status) {
    this.ruleId = ruleId;
    this.triggerEventType = triggerEventType;
    this.triggerEventData = triggerEventData;
    this.conditionsMet = conditionsMet;
    this.status = status;
    this.startedAt = Instant.now();
    this.createdAt = Instant.now();
  }

  public void complete() {
    this.status = ExecutionStatus.ACTIONS_COMPLETED;
    this.completedAt = Instant.now();
  }

  public void fail(String errorMessage) {
    this.status = ExecutionStatus.ACTIONS_FAILED;
    this.errorMessage = errorMessage;
    this.completedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getRuleId() {
    return ruleId;
  }

  public String getTriggerEventType() {
    return triggerEventType;
  }

  public Map<String, Object> getTriggerEventData() {
    return triggerEventData;
  }

  public boolean isConditionsMet() {
    return conditionsMet;
  }

  public ExecutionStatus getStatus() {
    return status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
