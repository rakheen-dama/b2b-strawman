package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Request to update the firm-wide default weekly capacity (hours per member). */
public record UpdateCapacitySettingsRequest(
    @NotNull(message = "defaultWeeklyCapacityHours is required")
        @Positive(message = "defaultWeeklyCapacityHours must be positive")
        BigDecimal defaultWeeklyCapacityHours) {}
