package io.b2mash.b2b.b2bstrawman.provisioning;

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
@Table(name = "organizations", schema = "public")
public class Organization {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "clerk_org_id", nullable = false, unique = true)
  private String clerkOrgId;

  @Column(name = "name", nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "provisioning_status", nullable = false)
  private ProvisioningStatus provisioningStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "tier", nullable = false)
  private Tier tier;

  @Column(name = "plan_slug")
  private String planSlug;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Organization() {}

  public Organization(String clerkOrgId, String name) {
    this.clerkOrgId = clerkOrgId;
    this.name = name;
    this.provisioningStatus = ProvisioningStatus.PENDING;
    this.tier = Tier.STARTER;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
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

  public ProvisioningStatus getProvisioningStatus() {
    return provisioningStatus;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Tier getTier() {
    return tier;
  }

  public String getPlanSlug() {
    return planSlug;
  }

  public void updatePlan(Tier tier, String planSlug) {
    this.tier = tier;
    this.planSlug = planSlug;
    this.updatedAt = Instant.now();
  }

  public void markInProgress() {
    this.provisioningStatus = ProvisioningStatus.IN_PROGRESS;
    this.updatedAt = Instant.now();
  }

  public void markCompleted() {
    this.provisioningStatus = ProvisioningStatus.COMPLETED;
    this.updatedAt = Instant.now();
  }

  public void markFailed() {
    this.provisioningStatus = ProvisioningStatus.FAILED;
    this.updatedAt = Instant.now();
  }

  public enum ProvisioningStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
  }
}
