package io.b2mash.b2b.b2bstrawman.settings.dto;

/** Read model for the firm letterhead group (Phase 88 U8, ADR-353). Fields are null when unset. */
public record LetterheadSettingsResponse(
    String addressLine1,
    String addressLine2,
    String addressLine3,
    String city,
    String postalCode,
    String country,
    String phone,
    String email,
    String website,
    String bankName,
    String bankBranchCode,
    String bankAccountNumber,
    String bankAccountType,
    String paymentReferenceNote,
    String signatoryName,
    String signatoryDesignation) {

  /** All-null letterhead, used when the tenant has no {@code org_settings} row yet. */
  public static final LetterheadSettingsResponse EMPTY =
      new LetterheadSettingsResponse(
          null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
          null);
}
