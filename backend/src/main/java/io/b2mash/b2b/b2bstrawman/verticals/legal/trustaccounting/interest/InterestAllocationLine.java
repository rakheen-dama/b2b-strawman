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

/**
 * One matter's share of a client's interest allocation (ADR-366, G89-F22). The client share of an
 * {@link InterestAllocation} is split across the matter buckets the client held money in during the
 * period, pro-rata by balance-days (weighted by each rate segment's rate and client fraction), to
 * the cent by largest remainder. {@code projectId == null} is the client-level (unmattered) bucket.
 * Posting writes one INTEREST_CREDIT per line, carrying the line's matter.
 */
@Entity
@Table(name = "interest_allocation_lines")
public class InterestAllocationLine {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "interest_allocation_id", nullable = false)
  private UUID interestAllocationId;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "balance_days", nullable = false, precision = 20, scale = 2)
  private BigDecimal balanceDays;

  @Column(name = "weight", nullable = false, precision = 30, scale = 10)
  private BigDecimal weight;

  @Column(name = "client_share", nullable = false, precision = 15, scale = 2)
  private BigDecimal clientShare;

  @Column(name = "trust_transaction_id")
  private UUID trustTransactionId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected InterestAllocationLine() {}

  public InterestAllocationLine(
      UUID interestAllocationId,
      UUID projectId,
      BigDecimal balanceDays,
      BigDecimal weight,
      BigDecimal clientShare) {
    this.interestAllocationId = interestAllocationId;
    this.projectId = projectId;
    this.balanceDays = balanceDays;
    this.weight = weight;
    this.clientShare = clientShare;
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getInterestAllocationId() {
    return interestAllocationId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public BigDecimal getBalanceDays() {
    return balanceDays;
  }

  public BigDecimal getWeight() {
    return weight;
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
