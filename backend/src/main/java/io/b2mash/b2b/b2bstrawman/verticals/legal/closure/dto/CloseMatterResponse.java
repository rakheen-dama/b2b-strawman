package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response returned by {@code POST /api/matters/{projectId}/closure/close}. {@code
 * retentionPolicyId} is intentionally optional — Phase 67 §67.3.5 reserves the field for a future
 * per-matter RetentionPolicy row. {@code retentionEndsAt} is computed as {@code closedAt +
 * orgSettings.effectiveLegalMatterRetentionYears}.
 */
public record CloseMatterResponse(
    UUID projectId,
    String status,
    Instant closedAt,
    UUID closureLogId,
    UUID closureLetterDocumentId,
    UUID retentionPolicyId,
    LocalDate retentionEndsAt) {}
