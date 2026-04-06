package io.b2mash.b2b.b2bstrawman.integration.kyc;

/**
 * Response DTO indicating whether a KYC provider is configured and which provider it is.
 *
 * @param configured true if a KYC integration is configured and enabled for the tenant
 * @param provider the provider slug (e.g., "verifynow", "checkid"), or null if not configured
 */
public record KycIntegrationStatusResponse(boolean configured, String provider) {}
