package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.Size;

/** Request to update tax-registration metadata and the tax-inclusive pricing flag. */
public record UpdateTaxSettingsRequest(
    @Size(max = 50, message = "taxRegistrationNumber must be at most 50 characters")
        String taxRegistrationNumber,
    @Size(max = 30, message = "taxRegistrationLabel must be at most 30 characters")
        String taxRegistrationLabel,
    @Size(max = 20, message = "taxLabel must be at most 20 characters") String taxLabel,
    boolean taxInclusive) {}
