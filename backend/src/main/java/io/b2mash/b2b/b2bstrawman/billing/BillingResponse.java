package io.b2mash.b2b.b2bstrawman.billing;

import java.time.Instant;

public record BillingResponse(
    String status,
    Instant trialEndsAt,
    Instant currentPeriodEnd,
    Instant graceEndsAt,
    Instant nextBillingAt,
    int monthlyAmountCents,
    String currency,
    LimitsResponse limits,
    boolean canSubscribe,
    boolean canCancel) {

  public record LimitsResponse(int maxMembers, long currentMembers) {}

  public static BillingResponse from(Subscription sub, long memberCount, BillingProperties props) {
    var status = sub.getSubscriptionStatus();
    boolean canSubscribe = canSubscribe(status);
    boolean canCancel = status == Subscription.SubscriptionStatus.ACTIVE;
    return new BillingResponse(
        status.name(),
        sub.getTrialEndsAt(),
        sub.getCurrentPeriodEnd(),
        sub.getGraceEndsAt(),
        sub.getNextBillingAt(),
        props.monthlyPriceCents(),
        props.currency(),
        new LimitsResponse(props.maxMembers(), memberCount),
        canSubscribe,
        canCancel);
  }

  /**
   * Creates a synthetic TRIALING response when no subscription row exists. Defensive fallback used
   * during the transition period where an org may not yet have a subscription record.
   */
  public static BillingResponse syntheticTrialing(long memberCount, BillingProperties props) {
    return new BillingResponse(
        "TRIALING",
        null,
        null,
        null,
        null,
        props.monthlyPriceCents(),
        props.currency(),
        new LimitsResponse(props.maxMembers(), memberCount),
        true,
        false);
  }

  private static boolean canSubscribe(Subscription.SubscriptionStatus status) {
    return switch (status) {
      case TRIALING, EXPIRED, GRACE_PERIOD, SUSPENDED, LOCKED -> true;
      default -> false;
    };
  }
}
