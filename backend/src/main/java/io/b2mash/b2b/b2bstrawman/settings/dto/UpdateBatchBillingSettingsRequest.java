package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Request to update batch-billing tuning (async threshold, email rate limit, default currency). */
public record UpdateBatchBillingSettingsRequest(
    @Min(value = 1, message = "billingBatchAsyncThreshold must be at least 1")
        @Max(value = 1000, message = "billingBatchAsyncThreshold must be at most 1000")
        Integer billingBatchAsyncThreshold,
    @Min(value = 1, message = "billingEmailRateLimit must be at least 1")
        @Max(value = 100, message = "billingEmailRateLimit must be at most 100")
        Integer billingEmailRateLimit,
    @Size(min = 3, max = 3, message = "defaultBillingRunCurrency must be exactly 3 characters")
        String defaultBillingRunCurrency) {}
