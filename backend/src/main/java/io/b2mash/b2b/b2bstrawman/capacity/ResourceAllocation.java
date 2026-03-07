package io.b2mash.b2b.b2bstrawman.capacity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "resource_allocations")
public class ResourceAllocation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "member_id", nullable = false)
  private UUID memberId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "week_start", nullable = false)
  private LocalDate weekStart;

  @Column(name = "allocated_hours", nullable = false, precision = 5, scale = 2)
  private BigDecimal allocatedHours;

  @Column(name = "note", length = 500)
  private String note;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ResourceAllocation() {}

  public ResourceAllocation(
      UUID memberId,
      UUID projectId,
      LocalDate weekStart,
      BigDecimal allocatedHours,
      String note,
      UUID createdBy) {
    this.memberId = memberId;
    this.projectId = projectId;
    this.weekStart = weekStart;
    this.allocatedHours = allocatedHours;
    this.note = note;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void update(BigDecimal allocatedHours, String note) {
    this.allocatedHours = allocatedHours;
    this.note = note;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public LocalDate getWeekStart() {
    return weekStart;
  }

  public BigDecimal getAllocatedHours() {
    return allocatedHours;
  }

  public String getNote() {
    return note;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
