package io.b2mash.b2b.b2bstrawman.crm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for {@code POST /api/deals/{id}/transition} (Phase 80, slice 575A). The target stage
 * drives the transition: an OPEN stage moves/re-opens the deal, a WON stage wins it, a LOST stage
 * loses it (then {@code lostReason} is required).
 *
 * @param targetStageId the stage to transition into (required)
 * @param probabilityOverride explicit probability override 0..100 applied on OPEN→OPEN moves
 *     (nullable; ignored for win/lose/reopen)
 * @param lostReason reason for loss — required (non-blank) when the target is a LOST stage
 */
public record TransitionRequest(
    @NotNull(message = "targetStageId is required") UUID targetStageId,
    @Min(value = 0, message = "probabilityOverride must be between 0 and 100")
        @Max(value = 100, message = "probabilityOverride must be between 0 and 100")
        Integer probabilityOverride,
    String lostReason) {}
