package io.b2mash.b2b.b2bstrawman.integration.kyc;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;

/**
 * Port interface for KYC identity verification. Tenant-scoped: each org can configure their own KYC
 * provider (VerifyNow, Check ID SA, etc.) or fall back to the NoOp adapter.
 */
public interface KycVerificationPort {

  /** Unique provider identifier (e.g., "verifynow", "checkid", "noop"). */
  String providerId();

  /** Verify the identity of a person using the configured provider. */
  KycVerificationResult verify(KycVerificationRequest request);

  /** Test connectivity with the configured credentials. */
  ConnectionTestResult testConnection();
}
