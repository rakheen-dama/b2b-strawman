package io.b2mash.b2b.b2bstrawman.integration.accounting.xero;

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

@Entity
@Table(name = "accounting_xero_connection")
public class AccountingXeroConnection {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_integration_id", nullable = false, unique = true)
  private UUID orgIntegrationId;

  @Column(name = "xero_tenant_id", nullable = false, length = 50)
  private String xeroTenantId;

  @Column(name = "xero_org_name", nullable = false, length = 255)
  private String xeroOrgName;

  @Column(name = "connected_by_member_id", nullable = false)
  private UUID connectedByMemberId;

  @Column(name = "connected_at", nullable = false)
  private Instant connectedAt;

  @Column(name = "last_token_refresh_at")
  private Instant lastTokenRefreshAt;

  @Column(name = "access_token_expires_at", nullable = false)
  private Instant accessTokenExpiresAt;

  @Column(name = "scope", nullable = false, length = 500)
  private String scope;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private XeroConnectionStatus status;

  @Column(name = "last_poll_at")
  private Instant lastPollAt;

  @Column(name = "refresh_failure_count", nullable = false)
  private int refreshFailureCount;

  @Column(name = "disconnected_at")
  private Instant disconnectedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AccountingXeroConnection() {}

  public AccountingXeroConnection(
      UUID orgIntegrationId,
      String xeroTenantId,
      String xeroOrgName,
      UUID connectedByMemberId,
      Instant accessTokenExpiresAt,
      String scope) {
    this.orgIntegrationId = orgIntegrationId;
    this.xeroTenantId = xeroTenantId;
    this.xeroOrgName = xeroOrgName;
    this.connectedByMemberId = connectedByMemberId;
    this.connectedAt = Instant.now();
    this.accessTokenExpiresAt = accessTokenExpiresAt;
    this.scope = scope;
    this.status = XeroConnectionStatus.CONNECTED;
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

  /** Record a successful token refresh — resets the failure counter. */
  public void recordTokenRefresh(Instant newExpiresAt) {
    this.lastTokenRefreshAt = Instant.now();
    this.accessTokenExpiresAt = newExpiresAt;
    this.status = XeroConnectionStatus.CONNECTED;
    this.refreshFailureCount = 0;
  }

  /** Increment the refresh failure counter and return the new value. */
  public int incrementRefreshFailureCount() {
    return ++this.refreshFailureCount;
  }

  /** Mark the connection as having a failed token refresh. */
  public void markRefreshFailed() {
    this.status = XeroConnectionStatus.REFRESH_FAILED;
  }

  /** Mark the connection as revoked. */
  public void markRevoked() {
    this.status = XeroConnectionStatus.REVOKED;
    this.disconnectedAt = Instant.now();
  }

  /** Record a successful payment poll. */
  public void recordPoll() {
    this.lastPollAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrgIntegrationId() {
    return orgIntegrationId;
  }

  public String getXeroTenantId() {
    return xeroTenantId;
  }

  public String getXeroOrgName() {
    return xeroOrgName;
  }

  public UUID getConnectedByMemberId() {
    return connectedByMemberId;
  }

  public Instant getConnectedAt() {
    return connectedAt;
  }

  public Instant getLastTokenRefreshAt() {
    return lastTokenRefreshAt;
  }

  public Instant getAccessTokenExpiresAt() {
    return accessTokenExpiresAt;
  }

  public String getScope() {
    return scope;
  }

  public XeroConnectionStatus getStatus() {
    return status;
  }

  public Instant getLastPollAt() {
    return lastPollAt;
  }

  public int getRefreshFailureCount() {
    return refreshFailureCount;
  }

  public Instant getDisconnectedAt() {
    return disconnectedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
