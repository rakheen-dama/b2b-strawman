package io.b2mash.b2b.b2bstrawman.billing;

import java.time.Instant;

public record BillingResponse(
    String status,
    String tier,
    String planSlug,
    Instant trialEndsAt,
    Instant currentPeriodEnd,
    Instant graceEndsAt,
    Instant nextBillingAt,
    int monthlyAmountCents,
    String currency,
    LimitsResponse limits,
    boolean canSubscribe,
    boolean canCancel,
    String billingMethod,
    boolean adminManaged,
    String adminNote) {

  /** Backwards-compatible tier value. Always "PRO" since the tier system was removed. */
  private static final String DEFAULT_TIER = "PRO";

  /** Backwards-compatible planSlug. Always "pro" since the tier system was removed. */
  private static final String DEFAULT_PLAN_SLUG = "pro";

  public record LimitsResponse(int maxMembers, long currentMembers) {}

  public static BillingResponse from(Subscription sub, long memberCount, BillingProperties props) {
    var status = sub.getSubscriptionStatus();
    var billing = sub.getBillingMethod();
    boolean subscribable =
        status.isSubscribable()
            && (billing == BillingMethod.PAYFAST || billing == BillingMethod.MANUAL);
    boolean cancellable = status.isCancellable() && billing == BillingMethod.PAYFAST;
    return new BillingResponse(
        status.name(),
        DEFAULT_TIER,
        DEFAULT_PLAN_SLUG,
        sub.getTrialEndsAt(),
        sub.getCurrentPeriodEnd(),
        sub.getGraceEndsAt(),
        sub.getNextBillingAt(),
        props.monthlyPriceCents(),
        props.currency(),
        new LimitsResponse(props.maxMembers(), memberCount),
        subscribable,
        cancellable,
        billing.name(),
        billing.isAdminManaged(),
        sub.getAdminNote());
  }

  /**
   * Creates a synthetic TRIALING response when no subscription row exists. Defensive fallback used
   * during the transition period where an org may not yet have a subscription record.
   */
  public static BillingResponse syntheticTrialing(long memberCount, BillingProperties props) {
    return new BillingResponse(
        "TRIALING",
        DEFAULT_TIER,
        DEFAULT_PLAN_SLUG,
        null,
        null,
        null,
        null,
        props.monthlyPriceCents(),
        props.currency(),
        new LimitsResponse(props.maxMembers(), memberCount),
        true,
        false,
        BillingMethod.MANUAL.name(),
        true,
        null);
  }
}
