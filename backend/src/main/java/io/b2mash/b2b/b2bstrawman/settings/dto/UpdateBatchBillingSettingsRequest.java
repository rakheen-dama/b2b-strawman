package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Request to update billing settings: batch-billing tuning (async threshold, email rate limit,
 * default currency) and the firm default payment terms in days (Phase 88 U3, ADR-347). A null
 * component leaves the stored value unchanged.
 */
public record UpdateBatchBillingSettingsRequest(
    @Min(value = 1, message = "billingBatchAsyncThreshold must be at least 1")
        @Max(value = 1000, message = "billingBatchAsyncThreshold must be at most 1000")
        Integer billingBatchAsyncThreshold,
    @Min(value = 1, message = "billingEmailRateLimit must be at least 1")
        @Max(value = 100, message = "billingEmailRateLimit must be at most 100")
        Integer billingEmailRateLimit,
    @Size(min = 3, max = 3, message = "defaultBillingRunCurrency must be exactly 3 characters")
        String defaultBillingRunCurrency,
    @Min(value = 0, message = "defaultPaymentTermsDays must be at least 0")
        @Max(value = 365, message = "defaultPaymentTermsDays must be at most 365")
        Integer defaultPaymentTermsDays) {}
