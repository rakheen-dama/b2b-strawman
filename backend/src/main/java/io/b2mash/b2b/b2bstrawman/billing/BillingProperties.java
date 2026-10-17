package io.b2mash.b2b.b2bstrawman.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for subscription billing defaults (manual firm-level billing, ADR-335).
 *
 * @param monthlyPriceCents monthly subscription price in cents (e.g., 49900 = R499.00)
 * @param trialDays number of free trial days for new subscriptions
 * @param gracePeriodDays number of days after cancellation before features are locked
 * @param currency ISO 4217 currency code (e.g., "ZAR")
 * @param maxMembers maximum number of members allowed per organization
 */
@ConfigurationProperties(prefix = "heykazi.billing")
public record BillingProperties(
    int monthlyPriceCents, int trialDays, int gracePeriodDays, String currency, int maxMembers) {}
