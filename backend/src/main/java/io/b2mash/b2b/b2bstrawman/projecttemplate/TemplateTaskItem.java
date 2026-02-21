package io.b2mash.b2b.b2bstrawman.projecttemplate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "template_task_items")
public class TemplateTaskItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "template_task_id", nullable = false)
  private UUID templateTaskId;

  @Column(name = "title", nullable = false, length = 500)
  private String title;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TemplateTaskItem() {}

  public TemplateTaskItem(UUID templateTaskId, String title, int sortOrder) {
    this.templateTaskId = templateTaskId;
    this.title = title;
    this.sortOrder = sortOrder;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTemplateTaskId() {
    return templateTaskId;
  }

  public String getTitle() {
    return title;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
