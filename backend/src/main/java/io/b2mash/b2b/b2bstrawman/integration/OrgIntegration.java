package io.b2mash.b2b.b2bstrawman.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "org_integrations")
public class OrgIntegration {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "domain", nullable = false, length = 30)
  private IntegrationDomain domain;

  @Column(name = "provider_slug", nullable = false, length = 50)
  private String providerSlug;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "config_json", columnDefinition = "jsonb")
  private String configJson;

  @Column(name = "key_suffix", length = 6)
  private String keySuffix;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected OrgIntegration() {}

  public OrgIntegration(IntegrationDomain domain, String providerSlug) {
    this.domain = domain;
    this.providerSlug = providerSlug;
    this.enabled = false;
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

  public void updateProvider(String providerSlug, String configJson) {
    this.providerSlug = providerSlug;
    this.configJson = configJson;
  }

  public void setKeySuffix(String keySuffix) {
    this.keySuffix = keySuffix;
  }

  public void clearKeySuffix() {
    this.keySuffix = null;
  }

  public void enable() {
    this.enabled = true;
  }

  public void disable() {
    this.enabled = false;
  }

  public UUID getId() {
    return id;
  }

  public IntegrationDomain getDomain() {
    return domain;
  }

  public String getProviderSlug() {
    return providerSlug;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public String getConfigJson() {
    return configJson;
  }

  public String getKeySuffix() {
    return keySuffix;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
