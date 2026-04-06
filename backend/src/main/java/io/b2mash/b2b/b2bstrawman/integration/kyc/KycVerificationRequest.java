package io.b2mash.b2b.b2bstrawman.integration.kyc;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for a KYC identity verification check.
 *
 * @param idNumber the identity document number (e.g. SA ID number)
 * @param fullName the full legal name of the person being verified
 * @param dateOfBirth optional date of birth (ISO format YYYY-MM-DD)
 * @param idDocumentType document type: "SA_ID", "SMART_ID", or "PASSPORT"
 */
public record KycVerificationRequest(
    @NotBlank(message = "ID number is required") String idNumber,
    @NotBlank(message = "Full name is required") String fullName,
    String dateOfBirth,
    String idDocumentType) {}
