package io.b2mash.b2b.b2bstrawman.capacity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leave_blocks")
public class LeaveBlock {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "member_id", nullable = false)
  private UUID memberId;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date", nullable = false)
  private LocalDate endDate;

  @Column(name = "note", length = 500)
  private String note;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected LeaveBlock() {}

  public LeaveBlock(
      UUID memberId, LocalDate startDate, LocalDate endDate, String note, UUID createdBy) {
    this.memberId = memberId;
    this.startDate = startDate;
    this.endDate = endDate;
    this.note = note;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void update(LocalDate startDate, LocalDate endDate, String note) {
    this.startDate = startDate;
    this.endDate = endDate;
    this.note = note;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
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
