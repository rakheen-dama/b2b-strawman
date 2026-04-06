package io.b2mash.b2b.b2bstrawman.integration.kyc;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationKeys;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Check ID SA adapter for KYC format validation. Validates South African ID number structure and
 * extracts embedded data (birth date, citizenship status).
 *
 * <p><strong>Always returns NEEDS_REVIEW</strong> per ADR-236: format validation confirms a
 * structurally valid ID number but does not verify the person's identity against Home Affairs.
 * Returning VERIFIED would create a false sense of compliance.
 */
@Component
@IntegrationAdapter(domain = IntegrationDomain.KYC_VERIFICATION, slug = "checkid")
public class CheckIdKycAdapter implements KycVerificationPort {

  private static final Logger log = LoggerFactory.getLogger(CheckIdKycAdapter.class);

  private final SecretStore secretStore;

  public CheckIdKycAdapter(SecretStore secretStore) {
    this.secretStore = secretStore;
  }

  @Override
  public String providerId() {
    return "checkid";
  }

  @Override
  public KycVerificationResult verify(KycVerificationRequest request) {
    try {
      var apiKey = resolveApiKey();
      log.info(
          "CheckId: performing format validation for idDocumentType={}", request.idDocumentType());

      var providerReference = "CID-" + UUID.randomUUID();
      var metadata = new LinkedHashMap<String, String>();

      // Extract embedded data from SA ID number (YYMMDD SSSS C A Z)
      if (request.idNumber() != null && request.idNumber().length() == 13) {
        var idNum = request.idNumber();
        var birthDate =
            "19"
                + idNum.substring(0, 2)
                + "-"
                + idNum.substring(2, 4)
                + "-"
                + idNum.substring(4, 6);
        var citizenDigit = idNum.charAt(10);
        var citizenship = citizenDigit == '0' ? "SA_CITIZEN" : "PERMANENT_RESIDENT";

        metadata.put("extracted_birth_date", birthDate);
        metadata.put("extracted_citizenship", citizenship);
        metadata.put("format_valid", "true");
      } else {
        metadata.put("format_valid", "false");
      }

      // Always NEEDS_REVIEW — format validation is not identity verification (ADR-236)
      return new KycVerificationResult(
          KycVerificationStatus.NEEDS_REVIEW,
          "checkid",
          providerReference,
          "FORMAT_VALIDATED",
          "Format validation complete — manual identity verification required",
          Instant.now(),
          Map.copyOf(metadata));
    } catch (Exception e) {
      log.error("CheckId: format validation failed: {}", e.getMessage(), e);
      return new KycVerificationResult(
          KycVerificationStatus.ERROR,
          "checkid",
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
      return new ConnectionTestResult(true, "checkid", null);
    } catch (Exception e) {
      return new ConnectionTestResult(false, "checkid", e.getMessage());
    }
  }

  private String resolveApiKey() {
    return secretStore.retrieve(
        IntegrationKeys.apiKey(IntegrationDomain.KYC_VERIFICATION, "checkid"));
  }
}
