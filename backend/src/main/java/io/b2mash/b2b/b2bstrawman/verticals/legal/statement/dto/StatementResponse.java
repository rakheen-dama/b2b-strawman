package io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response payload for Statement of Account endpoints (architecture §67.4.3). Carries the
 * persisted-document handle, an optional inline HTML preview (populated on POST + GET-by-id), the
 * presigned PDF URL, the matter reference block, the numeric summary block, and the statement
 * period (Phase 89 C6) — read from the generation snapshot on list / getById, null for a document
 * without a snapshot period.
 */
public record StatementResponse(
    UUID id,
    UUID templateId,
    Instant generatedAt,
    String htmlPreview,
    String pdfUrl,
    MatterRef matter,
    StatementSummary summary,
    LocalDate periodStart,
    LocalDate periodEnd) {

  public record MatterRef(UUID matterId, String name) {}
}
