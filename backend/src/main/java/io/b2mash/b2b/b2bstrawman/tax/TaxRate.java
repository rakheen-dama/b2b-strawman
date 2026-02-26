package io.b2mash.b2b.b2bstrawman.tax;

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
import java.util.Objects;
import java.util.UUID;

/** Represents a tax rate that can be applied to invoice lines. */
@Entity
@Table(name = "tax_rates")
public class TaxRate {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Column(name = "rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal rate;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Column(name = "is_exempt", nullable = false)
  private boolean isExempt;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** JPA-required no-arg constructor. */
  protected TaxRate() {}

  public TaxRate(String name, BigDecimal rate, boolean isDefault, boolean isExempt, int sortOrder) {
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.rate = Objects.requireNonNull(rate, "rate must not be null");
    this.isDefault = isDefault;
    this.isExempt = isExempt;
    this.sortOrder = sortOrder;
  }

  @PrePersist
  void onPrePersist() {
    var now = Instant.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onPreUpdate() {
    this.updatedAt = Instant.now();
  }

  /** Updates the mutable fields of this tax rate. */
  public void update(
      String name,
      BigDecimal rate,
      boolean isDefault,
      boolean isExempt,
      int sortOrder,
      boolean active) {
    this.name = Objects.requireNonNull(name, "name must not be null");
    this.rate = Objects.requireNonNull(rate, "rate must not be null");
    this.isDefault = isDefault;
    this.isExempt = isExempt;
    this.sortOrder = sortOrder;
    this.active = active;
  }

  /** Deactivates this tax rate and clears the default flag. */
  public void deactivate() {
    this.active = false;
    this.isDefault = false;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getRate() {
    return rate;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public boolean isExempt() {
    return isExempt;
  }

  public boolean isActive() {
    return active;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
