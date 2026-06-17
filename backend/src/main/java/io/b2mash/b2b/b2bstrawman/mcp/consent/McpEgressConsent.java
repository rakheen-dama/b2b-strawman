package io.b2mash.b2b.b2bstrawman.mcp.consent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only POPIA data-egress consent record for the MCP connector (Epic 565A, §11.7). A firm's
 * decision to enable or disable LLM data egress is captured as a {@code GRANTED} / {@code REVOKED}
 * row; revoking never mutates a prior row — it inserts a new {@code REVOKED} record, so the table
 * is the firm's full consent audit trail. The "current" consent state is the newest row by {@code
 * consented_at} (see {@link
 * McpEgressConsentRepository#findTopByOrderByConsentedAtDescCreatedAtDesc()}).
 *
 * <p>Mirrors the {@code AiFirmProfile} convention: hand-rolled UUID id via {@code @GeneratedValue},
 * {@code created_at} stamped in {@code @PrePersist}, protected no-arg constructor, domain-factory
 * construction ({@link #grant}/{@link #revoke}), no Lombok and no setters.
 */
@Entity
@Table(name = "mcp_egress_consents")
public class McpEgressConsent {

  private static final String ACTION_GRANTED = "GRANTED";
  private static final String ACTION_REVOKED = "REVOKED";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "consented_by", nullable = false, updatable = false)
  private UUID consentedBy;

  @Column(name = "consented_at", nullable = false, updatable = false)
  private Instant consentedAt;

  @Column(name = "consent_version", nullable = false, updatable = false)
  private String consentVersion;

  @Column(name = "action", nullable = false, updatable = false)
  private String action;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected McpEgressConsent() {}

  private McpEgressConsent(UUID consentedBy, String consentVersion, String action) {
    this.consentedBy = consentedBy;
    this.consentVersion = consentVersion;
    this.action = action;
    this.consentedAt = Instant.now();
  }

  /** A new GRANTED consent decision by {@code memberId} for {@code consentVersion}. */
  public static McpEgressConsent grant(UUID memberId, String consentVersion) {
    return new McpEgressConsent(memberId, consentVersion, ACTION_GRANTED);
  }

  /** A new REVOKED consent decision by {@code memberId} for {@code consentVersion}. */
  public static McpEgressConsent revoke(UUID memberId, String consentVersion) {
    return new McpEgressConsent(memberId, consentVersion, ACTION_REVOKED);
  }

  @PrePersist
  void onPrePersist() {
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getConsentedBy() {
    return consentedBy;
  }

  public Instant getConsentedAt() {
    return consentedAt;
  }

  public String getConsentVersion() {
    return consentVersion;
  }

  public String getAction() {
    return action;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public boolean isGranted() {
    return ACTION_GRANTED.equals(action);
  }
}
