package io.b2mash.b2b.b2bstrawman.settings.dto;

import jakarta.validation.constraints.Positive;

/** Request to update compliance thresholds (dormancy, data-request deadline). */
public record UpdateComplianceSettingsRequest(
    @Positive(message = "dormancyThresholdDays must be positive") Integer dormancyThresholdDays,
    @Positive(message = "dataRequestDeadlineDays must be positive")
        Integer dataRequestDeadlineDays) {}
