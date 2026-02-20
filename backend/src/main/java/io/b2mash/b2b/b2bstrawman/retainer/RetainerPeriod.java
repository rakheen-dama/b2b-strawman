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
@Table(name = "retainer_periods")
public class RetainerPeriod {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "agreement_id", nullable = false)
  private UUID agreementId;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private PeriodStatus status;

  @Column(name = "allocated_hours", precision = 10, scale = 2)
  private BigDecimal allocatedHours;

  @Column(name = "base_allocated_hours", precision = 10, scale = 2)
  private BigDecimal baseAllocatedHours;

  @Column(name = "rollover_hours_in", nullable = false, precision = 10, scale = 2)
  private BigDecimal rolloverHoursIn;

  @Column(name = "consumed_hours", nullable = false, precision = 10, scale = 2)
  private BigDecimal consumedHours;

  @Column(name = "overage_hours", nullable = false, precision = 10, scale = 2)
  private BigDecimal overageHours;

  @Column(name = "remaining_hours", nullable = false, precision = 10, scale = 2)
  private BigDecimal remainingHours;

  @Column(name = "rollover_hours_out", nullable = false, precision = 10, scale = 2)
  private BigDecimal rolloverHoursOut;

  @Column(name = "invoice_id")
  private UUID invoiceId;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "closed_by")
  private UUID closedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected RetainerPeriod() {}

  public RetainerPeriod(
      UUID agreementId,
      LocalDate periodStart,
      LocalDate periodEnd,
      BigDecimal allocatedHours,
      BigDecimal baseAllocatedHours,
      BigDecimal rolloverHoursIn) {
    this.agreementId = agreementId;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.status = PeriodStatus.OPEN;
    this.allocatedHours = allocatedHours;
    this.baseAllocatedHours = baseAllocatedHours;
    this.rolloverHoursIn = rolloverHoursIn != null ? rolloverHoursIn : BigDecimal.ZERO;
    this.consumedHours = BigDecimal.ZERO;
    this.overageHours = BigDecimal.ZERO;
    // remainingHours = allocatedHours for HOUR_BANK; 0 for FIXED_FEE (allocatedHours is null)
    this.remainingHours = allocatedHours != null ? allocatedHours : BigDecimal.ZERO;
    this.rolloverHoursOut = BigDecimal.ZERO;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  /**
   * Recalculates remainingHours from consumed hours. For HOUR_BANK: remainingHours = MAX(0,
   * allocatedHours - consumedHours) For FIXED_FEE (allocatedHours is null): just update
   * consumedHours, remainingHours stays 0
   */
  public void updateConsumption(BigDecimal newConsumedHours) {
    if (this.status == PeriodStatus.CLOSED) {
      throw new IllegalStateException("Cannot update consumption on a closed period");
    }
    this.consumedHours = newConsumedHours;
    if (this.allocatedHours != null) {
      BigDecimal diff = this.allocatedHours.subtract(newConsumedHours);
      this.remainingHours = diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
    }
    this.updatedAt = Instant.now();
  }

  /**
   * Closes the period with final calculated values. Sets status = CLOSED and records close
   * metadata.
   */
  public void close(
      UUID invoiceId, UUID closedBy, BigDecimal overageHours, BigDecimal rolloverHoursOut) {
    if (this.status == PeriodStatus.CLOSED) {
      throw new IllegalStateException("Period is already closed");
    }
    this.status = PeriodStatus.CLOSED;
    this.invoiceId = invoiceId;
    this.closedBy = closedBy;
    this.overageHours = overageHours != null ? overageHours : BigDecimal.ZERO;
    this.rolloverHoursOut = rolloverHoursOut != null ? rolloverHoursOut : BigDecimal.ZERO;
    this.closedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getAgreementId() {
    return agreementId;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public PeriodStatus getStatus() {
    return status;
  }

  public BigDecimal getAllocatedHours() {
    return allocatedHours;
  }

  public BigDecimal getBaseAllocatedHours() {
    return baseAllocatedHours;
  }

  public BigDecimal getRolloverHoursIn() {
    return rolloverHoursIn;
  }

  public BigDecimal getConsumedHours() {
    return consumedHours;
  }

  public BigDecimal getOverageHours() {
    return overageHours;
  }

  public BigDecimal getRemainingHours() {
    return remainingHours;
  }

  public BigDecimal getRolloverHoursOut() {
    return rolloverHoursOut;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public UUID getClosedBy() {
    return closedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
