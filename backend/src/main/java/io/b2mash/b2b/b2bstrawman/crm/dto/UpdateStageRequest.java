package io.b2mash.b2b.b2bstrawman.crm.dto;

import io.b2mash.b2b.b2bstrawman.crm.StageType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Update-stage request (Phase 80, slice 578B). Mirrors {@code PipelineStageService.updateStage} —
 * name, default probability, and stage type are all set in one edit. Reordering and archiving have
 * dedicated endpoints.
 *
 * @param name stage name (required, max 80 chars)
 * @param defaultProbabilityPct default probability for OPEN deals in this stage (0–100)
 * @param stageType OPEN / WON / LOST (required)
 */
public record UpdateStageRequest(
    @NotBlank(message = "name is required")
        @Size(max = 80, message = "name must not exceed 80 characters")
        String name,
    @Min(value = 0, message = "defaultProbabilityPct must be between 0 and 100")
        @Max(value = 100, message = "defaultProbabilityPct must be between 0 and 100")
        int defaultProbabilityPct,
    @NotNull(message = "stageType is required") StageType stageType) {}
