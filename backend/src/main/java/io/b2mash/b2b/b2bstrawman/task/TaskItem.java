package io.b2mash.b2b.b2bstrawman.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_items")
public class TaskItem {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "task_id", nullable = false)
  private UUID taskId;

  @Column(name = "title", nullable = false, length = 500)
  private String title;

  @Column(name = "completed", nullable = false)
  private boolean completed;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TaskItem() {}

  public TaskItem(UUID taskId, String title, int sortOrder) {
    this.taskId = taskId;
    this.title = title;
    this.sortOrder = sortOrder;
    this.completed = false;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /** Flips the completed flag and updates the timestamp. */
  public void toggle() {
    this.completed = !this.completed;
    this.updatedAt = Instant.now();
  }

  /** Updates the title and sort order. */
  public void update(String title, int sortOrder) {
    this.title = title;
    this.sortOrder = sortOrder;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTaskId() {
    return taskId;
  }

  public String getTitle() {
    return title;
  }

  public boolean isCompleted() {
    return completed;
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
