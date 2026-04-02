package io.b2mash.b2b.b2bstrawman.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for subscription billing defaults.
 *
 * @param monthlyPriceCents monthly subscription price in cents (e.g., 49900 = R499.00)
 * @param trialDays number of free trial days for new subscriptions
 * @param gracePeriodDays number of days after cancellation before features are locked
 * @param currency ISO 4217 currency code (e.g., "ZAR")
 * @param itemName display name for the subscription line item
 * @param notifyUrl URL that PayFast will POST ITN notifications to
 * @param returnUrl URL to redirect the user to after successful payment
 * @param cancelUrl URL to redirect the user to if they cancel the payment flow
 * @param maxMembers maximum number of members allowed per organization
 */
@ConfigurationProperties(prefix = "heykazi.billing")
public record BillingProperties(
    int monthlyPriceCents,
    int trialDays,
    int gracePeriodDays,
    String currency,
    String itemName,
    String notifyUrl,
    String returnUrl,
    String cancelUrl,
    int maxMembers) {}
