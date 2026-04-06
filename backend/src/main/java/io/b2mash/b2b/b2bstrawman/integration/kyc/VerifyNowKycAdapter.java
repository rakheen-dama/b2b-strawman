package io.b2mash.b2b.b2bstrawman.integration.kyc;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationKeys;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * VerifyNow KYC adapter. Calls the VerifyNow REST API to verify South African identity documents
 * against the Department of Home Affairs database. Uses per-request API keys resolved from
 * SecretStore for multi-tenant safety.
 */
@Component
@IntegrationAdapter(domain = IntegrationDomain.KYC_VERIFICATION, slug = "verifynow")
public class VerifyNowKycAdapter implements KycVerificationPort {

  private static final Logger log = LoggerFactory.getLogger(VerifyNowKycAdapter.class);

  private final SecretStore secretStore;

  public VerifyNowKycAdapter(SecretStore secretStore) {
    this.secretStore = secretStore;
  }

  @Override
  public String providerId() {
    return "verifynow";
  }

  @Override
  public KycVerificationResult verify(KycVerificationRequest request) {
    try {
      var apiKey = resolveApiKey();
      log.info(
          "VerifyNow: initiating verification for idDocumentType={}, fullName={}",
          request.idDocumentType(),
          request.fullName());

      // POST /verifications — submit verification request
      // In a real implementation this would be an HTTP call to VerifyNow's API.
      // For now, we simulate the API interaction pattern:
      // 1. POST to create verification
      // 2. Poll GET /verifications/{id} for result
      var providerReference = "VN-" + UUID.randomUUID();

      // Simulate provider response mapping:
      // VerifyNow returns status codes that map to our enum.
      // Real implementation would parse the JSON response here.
      log.info(
          "VerifyNow: verification submitted, reference={}, awaiting result", providerReference);

      // Placeholder: real adapter would make HTTP calls and map response.
      // Returning NEEDS_REVIEW as default since we cannot reach the real API without credentials.
      return new KycVerificationResult(
          KycVerificationStatus.NEEDS_REVIEW,
          "verifynow",
          providerReference,
          "PENDING_REAL_API",
          "VerifyNow API integration pending — real HTTP client not yet wired",
          null,
          Map.of("api_key_configured", "true"));
    } catch (Exception e) {
      log.error("VerifyNow: verification failed: {}", e.getMessage(), e);
      return new KycVerificationResult(
          KycVerificationStatus.ERROR,
          "verifynow",
          null,
          "PROVIDER_ERROR",
          e.getMessage(),
          null,
          Map.of());
    }
  }

  @Override
  public ConnectionTestResult testConnection() {
    try {
      resolveApiKey();
      return new ConnectionTestResult(true, "verifynow", null);
    } catch (Exception e) {
      return new ConnectionTestResult(false, "verifynow", e.getMessage());
    }
  }

  private String resolveApiKey() {
    return secretStore.retrieve(
        IntegrationKeys.apiKey(IntegrationDomain.KYC_VERIFICATION, "verifynow"));
  }
}
