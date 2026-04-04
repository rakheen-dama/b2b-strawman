package io.b2mash.b2b.b2bstrawman.verticals.legal.trustaccounting.lpff;

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
@Table(name = "lpff_rates")
public class LpffRate {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "trust_account_id", nullable = false)
  private UUID trustAccountId;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "rate_percent", nullable = false, precision = 5, scale = 4)
  private BigDecimal ratePercent;

  @Column(name = "lpff_share_percent", nullable = false, precision = 5, scale = 4)
  private BigDecimal lpffSharePercent;

  @Column(name = "notes", length = 500)
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected LpffRate() {}

  public LpffRate(
      UUID trustAccountId,
      LocalDate effectiveFrom,
      BigDecimal ratePercent,
      BigDecimal lpffSharePercent,
      String notes) {
    this.trustAccountId = trustAccountId;
    this.effectiveFrom = effectiveFrom;
    this.ratePercent = ratePercent;
    this.lpffSharePercent = lpffSharePercent;
    this.notes = notes;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getTrustAccountId() {
    return trustAccountId;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public BigDecimal getRatePercent() {
    return ratePercent;
  }

  public BigDecimal getLpffSharePercent() {
    return lpffSharePercent;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
