package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

/**
 * Request to update compliance thresholds (dormancy, data-request deadline) and the FICA review
 * intervals by risk rating in months (Phase 93 655B, FD-7 — the firm's RMCP choice, 1–120). Every
 * field is optional; a null keeps the stored value.
 */
public record UpdateComplianceSettingsRequest(
    @Positive(message = "dormancyThresholdDays must be positive") Integer dormancyThresholdDays,
    @Positive(message = "dataRequestDeadlineDays must be positive") Integer dataRequestDeadlineDays,
    @Min(value = 1, message = "ficaReviewMonthsLow must be between 1 and 120")
        @Max(value = 120, message = "ficaReviewMonthsLow must be between 1 and 120")
        Integer ficaReviewMonthsLow,
    @Min(value = 1, message = "ficaReviewMonthsMedium must be between 1 and 120")
        @Max(value = 120, message = "ficaReviewMonthsMedium must be between 1 and 120")
        Integer ficaReviewMonthsMedium,
    @Min(value = 1, message = "ficaReviewMonthsHigh must be between 1 and 120")
        @Max(value = 120, message = "ficaReviewMonthsHigh must be between 1 and 120")
        Integer ficaReviewMonthsHigh) {}
