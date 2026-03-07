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
@Table(name = "automation_actions")
public class AutomationAction {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "rule_id", nullable = false)
  private UUID ruleId;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false, length = 30)
  private ActionType actionType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "action_config", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> actionConfig;

  @Column(name = "delay_duration")
  private Integer delayDuration;

  @Enumerated(EnumType.STRING)
  @Column(name = "delay_unit", length = 10)
  private DelayUnit delayUnit;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AutomationAction() {}

  public AutomationAction(
      UUID ruleId,
      int sortOrder,
      ActionType actionType,
      Map<String, Object> actionConfig,
      Integer delayDuration,
      DelayUnit delayUnit) {
    this.ruleId = ruleId;
    this.sortOrder = sortOrder;
    this.actionType = actionType;
    this.actionConfig = actionConfig;
    this.delayDuration = delayDuration;
    this.delayUnit = delayUnit;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void update(
      ActionType actionType,
      Map<String, Object> actionConfig,
      int sortOrder,
      Integer delayDuration,
      DelayUnit delayUnit) {
    this.actionType = actionType;
    this.actionConfig = actionConfig;
    this.sortOrder = sortOrder;
    this.delayDuration = delayDuration;
    this.delayUnit = delayUnit;
    this.updatedAt = Instant.now();
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getRuleId() {
    return ruleId;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public ActionType getActionType() {
    return actionType;
  }

  public Map<String, Object> getActionConfig() {
    return actionConfig;
  }

  public Integer getDelayDuration() {
    return delayDuration;
  }

  public DelayUnit getDelayUnit() {
    return delayUnit;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
