package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import jakarta.validation.constraints.Size;

/**
 * Body payload for {@code POST /api/matters/{projectId}/closure/reopen} (Phase 67, Epic 489B).
 * Length is re-validated in the service (&gt;=10 chars) to surface a domain-shaped {@link
 * io.b2mash.b2b.b2bstrawman.exception.InvalidStateException}.
 */
public record ReopenRequest(
    @Size(max = 5000, message = "notes must not exceed 5000 characters") String notes) {}
