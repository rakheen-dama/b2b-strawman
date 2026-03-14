package io.b2mash.b2b.b2bstrawman.invitation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores the intended role for an invited user until they first log in and a Member record is
 * created. Lives in the public schema (cross-tenant) because invitations happen before the user
 * exists in any tenant schema.
 */
@Entity
@Table(name = "pending_invitations", schema = "public")
public class PendingInvitation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String orgSlug;

  @Column(nullable = false)
  private String email;

  @Column(nullable = false)
  private String role;

  @Column(nullable = false)
  private Instant createdAt;

  protected PendingInvitation() {}

  public PendingInvitation(String orgSlug, String email, String role) {
    this.orgSlug = orgSlug;
    this.email = email;
    this.role = role;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getOrgSlug() {
    return orgSlug;
  }

  public String getEmail() {
    return email;
  }

  public String getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
