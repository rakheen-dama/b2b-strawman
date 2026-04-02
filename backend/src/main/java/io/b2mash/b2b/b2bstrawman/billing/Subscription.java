package io.b2mash.b2b.b2bstrawman.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "subscriptions", schema = "public")
public class Subscription {

  private static final Map<SubscriptionStatus, Set<SubscriptionStatus>> VALID_TRANSITIONS =
      Map.of(
          SubscriptionStatus.TRIALING,
              Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.EXPIRED),
          SubscriptionStatus.ACTIVE,
              Set.of(SubscriptionStatus.PENDING_CANCELLATION, SubscriptionStatus.PAST_DUE),
          SubscriptionStatus.PENDING_CANCELLATION,
              Set.of(SubscriptionStatus.GRACE_PERIOD, SubscriptionStatus.ACTIVE),
          SubscriptionStatus.PAST_DUE,
              Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.SUSPENDED),
          SubscriptionStatus.SUSPENDED,
              Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.LOCKED),
          SubscriptionStatus.EXPIRED, Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.LOCKED),
          SubscriptionStatus.GRACE_PERIOD,
              Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.LOCKED),
          SubscriptionStatus.LOCKED, Set.of(SubscriptionStatus.ACTIVE));

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "organization_id", nullable = false, unique = true)
  private UUID organizationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "subscription_status", nullable = false)
  private SubscriptionStatus subscriptionStatus;

  @Column(name = "payfast_token")
  private String payfastToken;

  @Column(name = "trial_ends_at")
  private Instant trialEndsAt;

  @Column(name = "grace_ends_at")
  private Instant graceEndsAt;

  @Column(name = "monthly_amount_cents")
  private Integer monthlyAmountCents;

  @Column(name = "currency", length = 3)
  private String currency;

  @Column(name = "last_payment_at")
  private Instant lastPaymentAt;

  @Column(name = "next_billing_at")
  private Instant nextBillingAt;

  @Column(name = "payfast_payment_id")
  private String payfastPaymentId;

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

  public Subscription(UUID organizationId) {
    this(organizationId, 14);
  }

  public Subscription(UUID organizationId, int trialDays) {
    this.organizationId = organizationId;
    this.subscriptionStatus = SubscriptionStatus.TRIALING;
    this.trialEndsAt = Instant.now().plus(Duration.ofDays(trialDays));
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void transitionTo(SubscriptionStatus newStatus) {
    validateTransition(this.subscriptionStatus, newStatus);
    this.subscriptionStatus = newStatus;
    this.updatedAt = Instant.now();
  }

  private void validateTransition(SubscriptionStatus from, SubscriptionStatus to) {
    Set<SubscriptionStatus> allowed = VALID_TRANSITIONS.get(from);
    if (allowed == null || !allowed.contains(to)) {
      throw new IllegalStateException(
          "Invalid subscription status transition: %s -> %s".formatted(from, to));
    }
  }

  // --- Getters ---

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public SubscriptionStatus getSubscriptionStatus() {
    return subscriptionStatus;
  }

  public String getPayfastToken() {
    return payfastToken;
  }

  public Instant getTrialEndsAt() {
    return trialEndsAt;
  }

  public Instant getGraceEndsAt() {
    return graceEndsAt;
  }

  public Integer getMonthlyAmountCents() {
    return monthlyAmountCents;
  }

  public String getCurrency() {
    return currency;
  }

  public Instant getLastPaymentAt() {
    return lastPaymentAt;
  }

  public Instant getNextBillingAt() {
    return nextBillingAt;
  }

  public String getPayfastPaymentId() {
    return payfastPaymentId;
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

  // --- Setters for mutable fields ---

  public void setPayfastToken(String payfastToken) {
    this.payfastToken = payfastToken;
  }

  public void setTrialEndsAt(Instant trialEndsAt) {
    this.trialEndsAt = trialEndsAt;
  }

  public void setGraceEndsAt(Instant graceEndsAt) {
    this.graceEndsAt = graceEndsAt;
  }

  public void setMonthlyAmountCents(Integer monthlyAmountCents) {
    this.monthlyAmountCents = monthlyAmountCents;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public void setLastPaymentAt(Instant lastPaymentAt) {
    this.lastPaymentAt = lastPaymentAt;
  }

  public void setNextBillingAt(Instant nextBillingAt) {
    this.nextBillingAt = nextBillingAt;
  }

  public void setPayfastPaymentId(String payfastPaymentId) {
    this.payfastPaymentId = payfastPaymentId;
  }

  public void setCurrentPeriodStart(Instant currentPeriodStart) {
    this.currentPeriodStart = currentPeriodStart;
  }

  public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
    this.currentPeriodEnd = currentPeriodEnd;
  }

  public void setCancelledAt(Instant cancelledAt) {
    this.cancelledAt = cancelledAt;
  }

  public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PENDING_CANCELLATION,
    PAST_DUE,
    SUSPENDED,
    GRACE_PERIOD,
    EXPIRED,
    LOCKED;

    /** Returns true if this status allows initiating a new subscription. */
    public boolean isSubscribable() {
      return switch (this) {
        case TRIALING, EXPIRED, GRACE_PERIOD, SUSPENDED, LOCKED -> true;
        default -> false;
      };
    }

    /** Returns true if this status allows cancellation. */
    public boolean isCancellable() {
      return this == ACTIVE;
    }

    /** Returns true if this status allows write operations (creating projects, tasks, etc.). */
    public boolean isWriteEnabled() {
      return switch (this) {
        case TRIALING, ACTIVE, PENDING_CANCELLATION, PAST_DUE, GRACE_PERIOD -> true;
        default -> false;
      };
    }
  }
}
