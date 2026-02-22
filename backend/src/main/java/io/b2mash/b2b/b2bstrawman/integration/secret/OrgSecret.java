package io.b2mash.b2b.b2bstrawman.integration.secret;

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
@Table(name = "org_secrets")
public class OrgSecret {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "secret_key", nullable = false, length = 200)
  private String secretKey;

  @Column(name = "encrypted_value", nullable = false, columnDefinition = "TEXT")
  private String encryptedValue;

  @Column(name = "iv", nullable = false, length = 24)
  private String iv;

  @Column(name = "key_version", nullable = false)
  private int keyVersion;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected OrgSecret() {}

  public OrgSecret(String secretKey, String encryptedValue, String iv, int keyVersion) {
    this.secretKey = secretKey;
    this.encryptedValue = encryptedValue;
    this.iv = iv;
    this.keyVersion = keyVersion;
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

  public void updateEncryptedValue(String encryptedValue, String iv, int keyVersion) {
    this.encryptedValue = encryptedValue;
    this.iv = iv;
    this.keyVersion = keyVersion;
  }

  public UUID getId() {
    return id;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public String getEncryptedValue() {
    return encryptedValue;
  }

  public String getIv() {
    return iv;
  }

  public int getKeyVersion() {
    return keyVersion;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
