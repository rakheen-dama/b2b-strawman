package io.b2mash.b2b.b2bstrawman.retainer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "retainer_agreements")
public class RetainerAgreement {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "schedule_id")
  private UUID scheduleId;

  @Column(name = "name", nullable = false, length = 300)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, length = 20)
  private RetainerType type;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private RetainerStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "frequency", nullable = false, length = 20)
  private RetainerFrequency frequency;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "allocated_hours", precision = 10, scale = 2)
  private BigDecimal allocatedHours;

  @Column(name = "period_fee", precision = 12, scale = 2)
  private BigDecimal periodFee;

  @Enumerated(EnumType.STRING)
  @Column(name = "rollover_policy", nullable = false, length = 20)
  private RolloverPolicy rolloverPolicy;

  @Column(name = "rollover_cap_hours", precision = 10, scale = 2)
  private BigDecimal rolloverCapHours;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected RetainerAgreement() {}

  public RetainerAgreement(
      UUID customerId,
      String name,
      RetainerType type,
      RetainerFrequency frequency,
      LocalDate startDate,
      LocalDate endDate,
      BigDecimal allocatedHours,
      BigDecimal periodFee,
      RolloverPolicy rolloverPolicy,
      BigDecimal rolloverCapHours,
      String notes,
      UUID createdBy) {
    this.customerId = customerId;
    this.name = name;
    this.type = type;
    this.status = RetainerStatus.ACTIVE;
    this.frequency = frequency;
    this.startDate = startDate;
    this.endDate = endDate;
    this.allocatedHours = allocatedHours;
    this.periodFee = periodFee;
    this.rolloverPolicy = rolloverPolicy != null ? rolloverPolicy : RolloverPolicy.FORFEIT;
    this.rolloverCapHours = rolloverCapHours;
    this.notes = notes;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void pause() {
    if (this.status != RetainerStatus.ACTIVE) {
      throw new IllegalStateException("Only active retainers can be paused");
    }
    this.status = RetainerStatus.PAUSED;
    this.updatedAt = Instant.now();
  }

  public void resume() {
    if (this.status != RetainerStatus.PAUSED) {
      throw new IllegalStateException("Only paused retainers can be resumed");
    }
    this.status = RetainerStatus.ACTIVE;
    this.updatedAt = Instant.now();
  }

  public void terminate() {
    if (this.status == RetainerStatus.TERMINATED) {
      throw new IllegalStateException("Retainer is already terminated");
    }
    this.status = RetainerStatus.TERMINATED;
    this.updatedAt = Instant.now();
  }

  public void updateTerms(
      String name,
      BigDecimal allocatedHours,
      BigDecimal periodFee,
      RolloverPolicy rolloverPolicy,
      BigDecimal rolloverCapHours,
      LocalDate endDate,
      String notes) {
    this.name = name;
    this.allocatedHours = allocatedHours;
    this.periodFee = periodFee;
    this.rolloverPolicy = rolloverPolicy;
    this.rolloverCapHours = rolloverCapHours;
    this.endDate = endDate;
    this.notes = notes;
    this.updatedAt = Instant.now();
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public String getName() {
    return name;
  }

  public RetainerType getType() {
    return type;
  }

  public RetainerStatus getStatus() {
    return status;
  }

  public RetainerFrequency getFrequency() {
    return frequency;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getEndDate() {
    return endDate;
  }

  public BigDecimal getAllocatedHours() {
    return allocatedHours;
  }

  public BigDecimal getPeriodFee() {
    return periodFee;
  }

  public RolloverPolicy getRolloverPolicy() {
    return rolloverPolicy;
  }

  public BigDecimal getRolloverCapHours() {
    return rolloverCapHours;
  }

  public String getNotes() {
    return notes;
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
