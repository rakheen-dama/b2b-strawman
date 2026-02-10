package io.b2mash.b2b.b2bstrawman.billing;

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
@Table(name = "subscriptions", schema = "public")
public class Subscription {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "organization_id", nullable = false, unique = true)
  private UUID organizationId;

  @Column(name = "plan_slug", nullable = false)
  private String planSlug;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private SubscriptionStatus status;

  @Column(name = "current_period_start")
  private Instant currentPeriodStart;

  @Column(name = "current_period_end")
  private Instant currentPeriodEnd;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected Subscription() {}

  public Subscription(UUID organizationId, String planSlug) {
    this.organizationId = organizationId;
    this.planSlug = planSlug;
    this.status = SubscriptionStatus.ACTIVE;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getPlanSlug() {
    return planSlug;
  }

  public SubscriptionStatus getStatus() {
    return status;
  }

  public Instant getCurrentPeriodStart() {
    return currentPeriodStart;
  }

  public Instant getCurrentPeriodEnd() {
    return currentPeriodEnd;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void changePlan(String planSlug) {
    this.planSlug = planSlug;
    this.updatedAt = Instant.now();
  }

  public enum SubscriptionStatus {
    ACTIVE,
    CANCELLED
  }
}
