package io.b2mash.b2b.b2bstrawman.integration.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounting_tax_code_mapping")
public class AccountingTaxCodeMapping {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "provider_id", nullable = false, length = 20)
  private String providerId;

  @Column(name = "kazi_tax_mode", nullable = false, length = 30)
  private String kaziTaxMode;

  @Column(name = "external_tax_code", nullable = false, length = 50)
  private String externalTaxCode;

  @Column(name = "display_label", nullable = false, length = 100)
  private String displayLabel;

  @Column(name = "is_default", nullable = false)
  private boolean isDefault;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AccountingTaxCodeMapping() {}

  public AccountingTaxCodeMapping(
      String providerId,
      String kaziTaxMode,
      String externalTaxCode,
      String displayLabel,
      boolean isDefault) {
    this.providerId = providerId;
    this.kaziTaxMode = kaziTaxMode;
    this.externalTaxCode = externalTaxCode;
    this.displayLabel = displayLabel;
    this.isDefault = isDefault;
  }

  @PrePersist
  void onPrePersist() {
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @PreUpdate
  void onPreUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getProviderId() {
    return providerId;
  }

  public String getKaziTaxMode() {
    return kaziTaxMode;
  }

  public String getExternalTaxCode() {
    return externalTaxCode;
  }

  public String getDisplayLabel() {
    return displayLabel;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
