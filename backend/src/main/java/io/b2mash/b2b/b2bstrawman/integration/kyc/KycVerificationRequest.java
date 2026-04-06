package io.b2mash.b2b.b2bstrawman.integration.kyc;

/**
 * Request payload for a KYC identity verification check.
 *
 * @param idNumber the identity document number (e.g. SA ID number)
 * @param fullName the full legal name of the person being verified
 * @param dateOfBirth optional date of birth (ISO format YYYY-MM-DD)
 * @param idDocumentType document type: "SA_ID", "SMART_ID", or "PASSPORT"
 */
public record KycVerificationRequest(
    String idNumber, String fullName, String dateOfBirth, String idDocumentType) {}
