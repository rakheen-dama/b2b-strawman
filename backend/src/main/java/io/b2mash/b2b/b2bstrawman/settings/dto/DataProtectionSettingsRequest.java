package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request to update data-protection settings (jurisdiction, retention policy, information officer,
 * and the firm's matter-retention period in years — Phase 88 U17, ADR-354). A null component leaves
 * the stored value unchanged.
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
        String informationOfficerEmail,
    @Min(value = 1, message = "legalMatterRetentionYears must be at least 1")
        @Max(value = 30, message = "legalMatterRetentionYears must be at most 30")
        Integer legalMatterRetentionYears) {}
