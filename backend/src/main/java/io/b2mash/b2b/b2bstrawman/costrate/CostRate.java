package io.b2mash.b2b.b2bstrawman.costrate;

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
@Table(name = "cost_rates")
public class CostRate {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "member_id", nullable = false)
  private UUID memberId;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency;

  @Column(name = "hourly_cost", nullable = false, precision = 12, scale = 2)
  private BigDecimal hourlyCost;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CostRate() {}

  public CostRate(
      UUID memberId,
      String currency,
      BigDecimal hourlyCost,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {
    this.memberId = memberId;
    this.currency = currency;
    this.hourlyCost = hourlyCost;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void update(
      BigDecimal hourlyCost, String currency, LocalDate effectiveFrom, LocalDate effectiveTo) {
    this.hourlyCost = hourlyCost;
    this.currency = currency;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getHourlyCost() {
    return hourlyCost;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
