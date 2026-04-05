package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.interest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "interest_runs")
public class InterestRun {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trust_account_id", nullable = false)
  private UUID trustAccountId;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "lpff_rate_id", nullable = false)
  private UUID lpffRateId;

  @Column(name = "total_interest", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalInterest;

  @Column(name = "total_lpff_share", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalLpffShare;

  @Column(name = "total_client_share", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalClientShare;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "posted_at")
  private Instant postedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected InterestRun() {}

  public InterestRun(
      UUID trustAccountId, LocalDate periodStart, LocalDate periodEnd, UUID lpffRateId) {
    this.trustAccountId = trustAccountId;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
    this.lpffRateId = lpffRateId;
    this.totalInterest = BigDecimal.ZERO;
    this.totalLpffShare = BigDecimal.ZERO;
    this.totalClientShare = BigDecimal.ZERO;
    this.status = "DRAFT";
  }

  @PrePersist
  protected void onCreate() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTrustAccountId() {
    return trustAccountId;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public UUID getLpffRateId() {
    return lpffRateId;
  }

  public BigDecimal getTotalInterest() {
    return totalInterest;
  }

  public void setTotalInterest(BigDecimal totalInterest) {
    this.totalInterest = totalInterest;
  }

  public BigDecimal getTotalLpffShare() {
    return totalLpffShare;
  }

  public void setTotalLpffShare(BigDecimal totalLpffShare) {
    this.totalLpffShare = totalLpffShare;
  }

  public BigDecimal getTotalClientShare() {
    return totalClientShare;
  }

  public void setTotalClientShare(BigDecimal totalClientShare) {
    this.totalClientShare = totalClientShare;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public void setApprovedBy(UUID approvedBy) {
    this.approvedBy = approvedBy;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getPostedAt() {
    return postedAt;
  }

  public void setPostedAt(Instant postedAt) {
    this.postedAt = postedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
