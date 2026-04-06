package io.b2mash.b2b.b2bstrawman.integration.kyc;

import java.time.Instant;
import java.util.Map;

/**
 * Result of a KYC identity verification check returned by the adapter.
 *
 * @param status verification outcome
 * @param providerName name of the provider that performed the check
 * @param providerReference provider-specific transaction or reference ID
 * @param reasonCode machine-readable reason code from the provider
 * @param reasonDescription human-readable explanation
 * @param verifiedAt timestamp when the provider confirmed verification (null if not verified)
 * @param metadata additional provider-specific key-value data
 */
public record KycVerificationResult(
    KycVerificationStatus status,
    String providerName,
    String providerReference,
    String reasonCode,
    String reasonDescription,
    Instant verifiedAt,
    Map<String, String> metadata) {}
