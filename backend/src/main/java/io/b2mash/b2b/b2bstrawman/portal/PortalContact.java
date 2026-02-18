package io.b2mash.b2b.b2bstrawman.portal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "portal_contacts")
public class PortalContact {

  public enum ContactRole {
    PRIMARY,
    BILLING,
    GENERAL
  }

  public enum ContactStatus {
    ACTIVE,
    SUSPENDED,
    ARCHIVED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "org_id", nullable = false, length = 255)
  private String orgId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "display_name", length = 255)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private ContactRole role;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ContactStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected PortalContact() {}

  public PortalContact(
      String orgId, UUID customerId, String email, String displayName, ContactRole role) {
    this.orgId = orgId;
    this.customerId = customerId;
    this.email = email;
    this.displayName = displayName;
    this.role = role != null ? role : ContactRole.GENERAL;
    this.status = ContactStatus.ACTIVE;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void suspend() {
    this.status = ContactStatus.SUSPENDED;
    this.updatedAt = Instant.now();
  }

  public void archive() {
    this.status = ContactStatus.ARCHIVED;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getOrgId() {
    return orgId;
  }

  public UUID getCustomerId() {
    return customerId;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public ContactRole getRole() {
    return role;
  }

  public ContactStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
