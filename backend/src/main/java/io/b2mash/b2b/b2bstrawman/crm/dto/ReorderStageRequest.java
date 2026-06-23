package io.b2mash.b2b.b2bstrawman.crm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Reorder-stage request (Phase 80, slice 578B). Mirrors {@code
 * PipelineStageService.reorderStage(stageId, newPosition)}.
 *
 * @param newPosition new ordinal position in the pipeline (required, >= 0)
 */
public record ReorderStageRequest(
    @NotNull(message = "newPosition is required")
        @Min(value = 0, message = "newPosition must be >= 0")
        Integer newPosition) {}
