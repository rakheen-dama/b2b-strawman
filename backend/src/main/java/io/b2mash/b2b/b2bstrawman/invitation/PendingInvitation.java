package io.b2mash.b2b.b2bstrawman.invitation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pending_invitations")
public class PendingInvitation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "org_role_id", nullable = false)
  private UUID orgRoleId;

  @Column(name = "invited_by", nullable = false)
  private UUID invitedBy;

  @Column(name = "status", nullable = false, length = 20)
  private String status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  protected PendingInvitation() {}

  public PendingInvitation(String email, UUID orgRoleId, UUID invitedBy, Instant expiresAt) {
    this.email = email;
    this.orgRoleId = orgRoleId;
    this.invitedBy = invitedBy;
    this.status = "PENDING";
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void onPrePersist() {
    this.createdAt = Instant.now();
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public boolean isPending() {
    return "PENDING".equals(status);
  }

  public void markAccepted() {
    this.status = "ACCEPTED";
    this.acceptedAt = Instant.now();
  }

  public void markRevoked() {
    this.status = "REVOKED";
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public UUID getOrgRoleId() {
    return orgRoleId;
  }

  public UUID getInvitedBy() {
    return invitedBy;
  }

  public String getStatus() {
    return status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }
}
