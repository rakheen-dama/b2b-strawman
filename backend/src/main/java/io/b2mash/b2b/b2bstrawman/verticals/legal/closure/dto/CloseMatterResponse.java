package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response returned by {@code POST /api/matters/{projectId}/closure/close}.
 *
 * <p>{@code retentionEndsAt} is computed as {@code closedAt +
 * orgSettings.effectiveLegalMatterRetentionYears}. A persisted per-matter retention row (with its
 * own {@code retentionPolicyId}) is NOT written in 489B — see TODO(489C) in {@code
 * MatterClosureService}; do not re-add the field here until that row actually exists and the UI
 * consumes it.
 */
public record CloseMatterResponse(
    UUID projectId,
    String status,
    Instant closedAt,
    UUID closureLogId,
    UUID closureLetterDocumentId,
    LocalDate retentionEndsAt) {}
