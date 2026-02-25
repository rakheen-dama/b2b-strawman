package io.b2mash.b2b.b2bstrawman.integration.email;

/** Aggregated delivery statistics for the email dashboard. */
public record EmailDeliveryStats(
    long sent24h,
    long bounced7d,
    long failed7d,
    long rateLimited7d,
    long currentHourUsage,
    int hourlyLimit,
    String providerSlug) {}
