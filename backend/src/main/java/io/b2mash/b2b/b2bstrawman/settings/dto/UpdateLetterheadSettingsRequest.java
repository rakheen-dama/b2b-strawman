package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Request to replace the firm letterhead group (Phase 88 U8, ADR-353). <strong>Full-replace
 * semantics</strong> (same as {@code PATCH /api/settings/tax}): every component is written on every
 * call — a {@code null}, absent or blank component CLEARS the stored value — so the Firm › General
 * form can clear, e.g., the banking block and the fee note then hides its "Banking details" label.
 * The compact constructor trims every component and normalises blank to null BEFORE bean validation
 * runs, so {@code @Email} / {@code @Size} see the stored value: {@code " "} clears, {@code "
 * info@x.law "} stores {@code "info@x.law"}, and lengths are checked after trimming. Max lengths
 * mirror the V7 columns; {@code paymentReferenceNote} ({@code text} column) is capped at 500
 * characters.
 */
public record UpdateLetterheadSettingsRequest(
    @Size(max = 255, message = "addressLine1 must be at most 255 characters") String addressLine1,
    @Size(max = 255, message = "addressLine2 must be at most 255 characters") String addressLine2,
    @Size(max = 255, message = "addressLine3 must be at most 255 characters") String addressLine3,
    @Size(max = 100, message = "city must be at most 100 characters") String city,
    @Size(max = 20, message = "postalCode must be at most 20 characters") String postalCode,
    @Size(max = 100, message = "country must be at most 100 characters") String country,
    @Size(max = 50, message = "phone must be at most 50 characters") String phone,
    @Email(message = "email must be a valid email")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,
    @Size(max = 255, message = "website must be at most 255 characters") String website,
    @Size(max = 100, message = "bankName must be at most 100 characters") String bankName,
    @Size(max = 20, message = "bankBranchCode must be at most 20 characters") String bankBranchCode,
    @Size(max = 50, message = "bankAccountNumber must be at most 50 characters")
        String bankAccountNumber,
    @Size(max = 30, message = "bankAccountType must be at most 30 characters")
        String bankAccountType,
    @Size(max = 500, message = "paymentReferenceNote must be at most 500 characters")
        String paymentReferenceNote,
    @Size(max = 255, message = "signatoryName must be at most 255 characters") String signatoryName,
    @Size(max = 255, message = "signatoryDesignation must be at most 255 characters")
        String signatoryDesignation) {

  public UpdateLetterheadSettingsRequest {
    addressLine1 = trimToNull(addressLine1);
    addressLine2 = trimToNull(addressLine2);
    addressLine3 = trimToNull(addressLine3);
    city = trimToNull(city);
    postalCode = trimToNull(postalCode);
    country = trimToNull(country);
    phone = trimToNull(phone);
    email = trimToNull(email);
    website = trimToNull(website);
    bankName = trimToNull(bankName);
    bankBranchCode = trimToNull(bankBranchCode);
    bankAccountNumber = trimToNull(bankAccountNumber);
    bankAccountType = trimToNull(bankAccountType);
    paymentReferenceNote = trimToNull(paymentReferenceNote);
    signatoryName = trimToNull(signatoryName);
    signatoryDesignation = trimToNull(signatoryDesignation);
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    var trimmed = value.strip();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
