package io.b2mash.b2b.b2bstrawman.invitation;

import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.orgrole.OrgRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "org_role_id", nullable = false)
  private OrgRole orgRole;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invited_by", nullable = false)
  private Member invitedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private InvitationStatus status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  protected PendingInvitation() {}

  public PendingInvitation(String email, OrgRole orgRole, Member invitedBy, Instant expiresAt) {
    this.email = email;
    this.orgRole = orgRole;
    this.invitedBy = invitedBy;
    this.status = InvitationStatus.PENDING;
    this.expiresAt = expiresAt;
  }

  @PrePersist
  void onPrePersist() {
    this.createdAt = Instant.now();
  }

  public void accept() {
    this.status = InvitationStatus.ACCEPTED;
    this.acceptedAt = Instant.now();
  }

  public void revoke() {
    this.status = InvitationStatus.REVOKED;
  }

  public void expire() {
    this.status = InvitationStatus.EXPIRED;
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public OrgRole getOrgRole() {
    return orgRole;
  }

  public Member getInvitedBy() {
    return invitedBy;
  }

  public InvitationStatus getStatus() {
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
