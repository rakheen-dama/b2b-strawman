package io.b2mash.b2b.b2bstrawman.verticals.legal.statement.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload for Statement of Account endpoints (architecture §67.4.3). Carries the
 * persisted-document handle, an optional inline HTML preview (populated on POST + GET-by-id), the
 * presigned PDF URL, the matter reference block, and the numeric summary block.
 */
public record StatementResponse(
    UUID id,
    UUID templateId,
    Instant generatedAt,
    String htmlPreview,
    String pdfUrl,
    MatterRef matter,
    StatementSummary summary) {

  public record MatterRef(UUID projectId, String name) {}
}
