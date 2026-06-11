package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request to update data-protection settings (jurisdiction, retention policy, information officer).
 */
public record DataProtectionSettingsRequest(
    @Size(max = 10, message = "dataProtectionJurisdiction must be at most 10 characters")
        String dataProtectionJurisdiction,
    Boolean retentionPolicyEnabled,
    @Min(value = 1, message = "defaultRetentionMonths must be positive")
        Integer defaultRetentionMonths,
    @Min(value = 12, message = "financialRetentionMonths must be at least 12")
        Integer financialRetentionMonths,
    @Size(max = 255, message = "informationOfficerName must be at most 255 characters")
        String informationOfficerName,
    @Email(message = "informationOfficerEmail must be a valid email")
        @Size(max = 255, message = "informationOfficerEmail must be at most 255 characters")
        String informationOfficerEmail) {}
