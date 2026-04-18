package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body payload for {@code POST /api/matters/{projectId}/closure/close} (Phase 67, Epic 489B).
 *
 * <p>Semantic validation — override flag, justification length (&gt;=20 non-whitespace chars when
 * override=true) — happens in {@code MatterClosureService}.
 */
public record ClosureRequest(
    @NotNull(message = "reason is required") ClosureReason reason,
    @Size(max = 5000, message = "notes must not exceed 5000 characters") String notes,
    boolean generateClosureLetter,
    boolean override,
    @Size(max = 5000, message = "overrideJustification must not exceed 5000 characters")
        String overrideJustification) {}
