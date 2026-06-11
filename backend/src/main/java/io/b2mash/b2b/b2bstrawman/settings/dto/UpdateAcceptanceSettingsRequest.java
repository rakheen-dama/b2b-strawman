package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Request to update the proposal/acceptance expiry window. */
public record UpdateAcceptanceSettingsRequest(
    @Min(value = 1, message = "acceptanceExpiryDays must be at least 1")
        @Max(value = 365, message = "acceptanceExpiryDays must be at most 365")
        Integer acceptanceExpiryDays) {}
