package io.b2mash.b2b.b2bstrawman.integration.kyc;

import io.b2mash.b2b.b2bstrawman.integration.ConnectionTestResult;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationAdapter;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationDomain;
import io.b2mash.b2b.b2bstrawman.integration.IntegrationKeys;
import io.b2mash.b2b.b2bstrawman.integration.secret.SecretStore;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
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
  private static final Pattern SA_ID_PATTERN = Pattern.compile("\\d{13}");
  private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("uuuuMMdd");

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
      log.info(
          "CheckId: performing format validation for idDocumentType={}", request.idDocumentType());

      var providerReference = "CID-" + UUID.randomUUID();
      var metadata = new LinkedHashMap<String, Object>();

      // Extract and validate embedded data from SA ID number (YYMMDD SSSS C A Z)
      boolean formatValid = false;
      if (request.idNumber() != null && SA_ID_PATTERN.matcher(request.idNumber()).matches()) {
        var idNum = request.idNumber();

        // Parse YYMMDD as a real date with century inference
        int yy = Integer.parseInt(idNum.substring(0, 2));
        var centuryPrefix = yy > 25 ? "19" : "20";
        var fullDateStr = centuryPrefix + idNum.substring(0, 6); // e.g. "19900101"
        try {
          LocalDate.parse(fullDateStr, YYMMDD); // validates the date segment
          var birthDate =
              centuryPrefix
                  + idNum.substring(0, 2)
                  + "-"
                  + idNum.substring(2, 4)
                  + "-"
                  + idNum.substring(4, 6);

          // Validate citizenship digit at position 10 (only '0' or '1')
          var citizenDigit = idNum.charAt(10);
          if (citizenDigit == '0' || citizenDigit == '1') {
            var citizenship = citizenDigit == '0' ? "SA_CITIZEN" : "PERMANENT_RESIDENT";
            metadata.put("extracted_birth_date", birthDate);
            metadata.put("extracted_citizenship", citizenship);
            formatValid = true;
          }
        } catch (DateTimeParseException e) {
          // Invalid date segment — format_valid stays false
          log.debug("CheckId: invalid date segment in ID number: {}", fullDateStr);
        }
      }
      metadata.put("format_valid", String.valueOf(formatValid));

      // Always NEEDS_REVIEW — format validation is not identity verification (ADR-236)
      // verifiedAt is null because NEEDS_REVIEW means the provider has NOT confirmed verification
      return new KycVerificationResult(
          KycVerificationStatus.NEEDS_REVIEW,
          "checkid",
          providerReference,
          "FORMAT_VALIDATED",
          "Format validation complete — manual identity verification required",
          null,
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
