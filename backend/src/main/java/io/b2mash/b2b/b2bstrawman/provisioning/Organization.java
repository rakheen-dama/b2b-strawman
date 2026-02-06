package io.b2mash.b2b.b2bstrawman.provisioning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations", schema = "public")
public class Organization {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "clerk_org_id", nullable = false, unique = true)
  private String clerkOrgId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String slug;

  @Column(name = "provisioning_status", nullable = false)
  private String provisioningStatus = "PENDING";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected Organization() {}

  public Organization(String clerkOrgId, String name, String slug) {
    this.clerkOrgId = clerkOrgId;
    this.name = name;
    this.slug = slug;
  }

  public UUID getId() {
    return id;
  }

  public String getClerkOrgId() {
    return clerkOrgId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getSlug() {
    return slug;
  }

  public void setSlug(String slug) {
    this.slug = slug;
  }

  public String getProvisioningStatus() {
    return provisioningStatus;
  }

  public void setProvisioningStatus(String provisioningStatus) {
    this.provisioningStatus = provisioningStatus;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
