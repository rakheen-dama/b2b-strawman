package io.b2mash.b2b.b2bstrawman.billing.payfast;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for PayFast payment gateway credentials.
 *
 * @param merchantId PayFast merchant ID
 * @param merchantKey PayFast merchant key
 * @param passphrase PayFast passphrase used for signature verification
 * @param sandbox whether to use the PayFast sandbox environment
 */
@ConfigurationProperties(prefix = "heykazi.billing.payfast")
public record PayFastBillingProperties(
    String merchantId, String merchantKey, String passphrase, boolean sandbox) {}
