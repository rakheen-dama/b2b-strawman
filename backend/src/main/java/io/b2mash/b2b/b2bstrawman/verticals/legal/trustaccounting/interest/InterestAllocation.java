package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interest_allocations")
public class InterestAllocation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "interest_run_id", nullable = false)
  private UUID interestRunId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "average_daily_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal averageDailyBalance;

  @Column(name = "days_in_period", nullable = false)
  private int daysInPeriod;

  @Column(name = "gross_interest", nullable = false, precision = 15, scale = 2)
  private BigDecimal grossInterest;

  @Column(name = "lpff_share", nullable = false, precision = 15, scale = 2)
  private BigDecimal lpffShare;

  @Column(name = "client_share", nullable = false, precision = 15, scale = 2)
  private BigDecimal clientShare;

  @Column(name = "trust_transaction_id")
  private UUID trustTransactionId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected InterestAllocation() {}

  public InterestAllocation(
      UUID interestRunId,
      UUID customerId,
      BigDecimal averageDailyBalance,
      int daysInPeriod,
      BigDecimal grossInterest,
      BigDecimal lpffShare,
      BigDecimal clientShare) {
    this.interestRunId = interestRunId;
    this.customerId = customerId;
    this.averageDailyBalance = averageDailyBalance;
    this.daysInPeriod = daysInPeriod;
    this.grossInterest = grossInterest;
    this.lpffShare = lpffShare;
    this.clientShare = clientShare;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getInterestRunId() {
    return interestRunId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public BigDecimal getAverageDailyBalance() {
    return averageDailyBalance;
  }

  public int getDaysInPeriod() {
    return daysInPeriod;
  }

  public BigDecimal getGrossInterest() {
    return grossInterest;
  }

  public BigDecimal getLpffShare() {
    return lpffShare;
  }

  public BigDecimal getClientShare() {
    return clientShare;
  }

  public UUID getTrustTransactionId() {
    return trustTransactionId;
  }

  public void setTrustTransactionId(UUID trustTransactionId) {
    this.trustTransactionId = trustTransactionId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
