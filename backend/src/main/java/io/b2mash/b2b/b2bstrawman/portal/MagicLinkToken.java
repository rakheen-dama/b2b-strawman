package io.b2mash.b2b.b2bstrawman.portal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persistent magic link token for customer portal authentication. Tokens are stored as SHA-256
 * hashes; the raw token is returned to the client and never stored. No tenant_id column -- tokens
 * are looked up by hash across all tenants.
 */
@Entity
@Table(name = "magic_link_tokens")
public class MagicLinkToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "portal_contact_id", nullable = false)
  private UUID portalContactId;

  @Column(name = "token_hash", nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "created_ip", length = 45)
  private String createdIp;

  protected MagicLinkToken() {}

  public MagicLinkToken(
      UUID portalContactId, String tokenHash, Instant expiresAt, String createdIp) {
    this.portalContactId = portalContactId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.createdIp = createdIp;
    this.createdAt = Instant.now();
  }

  /** Marks this token as consumed. */
  public void markUsed() {
    this.usedAt = Instant.now();
  }

  /** Returns true if the token has passed its expiration time. */
  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  /** Returns true if the token has already been consumed. */
  public boolean isUsed() {
    return usedAt != null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPortalContactId() {
    return portalContactId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getCreatedIp() {
    return createdIp;
  }
}
