package io.b2mash.b2b.b2bstrawman.projecttemplate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "template_tasks")
public class TemplateTask {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // Parent FK stored as plain UUID — no @ManyToOne. Schema boundary + CASCADE handles deletion.
  @Column(name = "template_id", nullable = false)
  private UUID templateId;

  // NOTE: TemplateTask.name maps to Task.title during instantiation.
  // When creating a real Task entity: task.setTitle(tt.getName())
  // The Task entity uses the field name 'title', not 'name'.
  @Column(name = "name", nullable = false, length = 300)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "estimated_hours", precision = 10, scale = 2)
  private BigDecimal estimatedHours;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "billable", nullable = false)
  private boolean billable;

  // Valid values: PROJECT_LEAD, ANY_MEMBER, UNASSIGNED (ADR-069)
  @Column(name = "assignee_role", nullable = false, length = 20)
  private String assigneeRole;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TemplateTask() {}

  public TemplateTask(
      UUID templateId,
      String name,
      String description,
      BigDecimal estimatedHours,
      int sortOrder,
      boolean billable,
      String assigneeRole) {
    this.templateId = templateId;
    this.name = name;
    this.description = description;
    this.estimatedHours = estimatedHours;
    this.sortOrder = sortOrder;
    this.billable = billable;
    this.assigneeRole = assigneeRole != null ? assigneeRole : "UNASSIGNED";
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void update(
      String name,
      String description,
      BigDecimal estimatedHours,
      int sortOrder,
      boolean billable,
      String assigneeRole) {
    this.name = name;
    this.description = description;
    this.estimatedHours = estimatedHours;
    this.sortOrder = sortOrder;
    this.billable = billable;
    this.assigneeRole = assigneeRole != null ? assigneeRole : "UNASSIGNED";
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTemplateId() {
    return templateId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getEstimatedHours() {
    return estimatedHours;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isBillable() {
    return billable;
  }

  public String getAssigneeRole() {
    return assigneeRole;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
