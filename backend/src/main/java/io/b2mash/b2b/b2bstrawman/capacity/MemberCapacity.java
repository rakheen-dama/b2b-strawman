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
@Table(name = "member_capacities")
public class MemberCapacity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "member_id", nullable = false)
  private UUID memberId;

  @Column(name = "weekly_hours", nullable = false, precision = 5, scale = 2)
  private BigDecimal weeklyHours;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Column(name = "note", length = 500)
  private String note;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected MemberCapacity() {}

  public MemberCapacity(
      UUID memberId,
      BigDecimal weeklyHours,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String note,
      UUID createdBy) {
    this.memberId = memberId;
    this.weeklyHours = weeklyHours;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.note = note;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void update(
      BigDecimal weeklyHours, LocalDate effectiveFrom, LocalDate effectiveTo, String note) {
    this.weeklyHours = weeklyHours;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.note = note;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public BigDecimal getWeeklyHours() {
    return weeklyHours;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
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
