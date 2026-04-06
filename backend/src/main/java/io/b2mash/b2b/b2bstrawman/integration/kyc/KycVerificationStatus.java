package io.b2mash.b2b.b2bstrawman.integration.kyc;

/** Outcome of a KYC identity verification check. */
public enum KycVerificationStatus {
  VERIFIED,
  NOT_VERIFIED,
  NEEDS_REVIEW,
  ERROR
}
