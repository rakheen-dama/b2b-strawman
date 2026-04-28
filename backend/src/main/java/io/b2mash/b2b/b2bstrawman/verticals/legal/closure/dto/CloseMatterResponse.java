package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response returned by {@code POST /api/matters/{projectId}/closure/close}.
 *
 * <p>{@code retentionEndsAt} is computed as {@code closedAt +
 * orgSettings.effectiveLegalMatterRetentionYears}.
 *
 * <p>{@code closureLetterDocumentId} (nullable) — set when the request had {@code
 * generateClosureLetter=true} and the letter rendered successfully.
 *
 * <p>{@code statementOfAccountDocumentId} (nullable, GAP-L-93) — set when the request had {@code
 * generateStatementOfAccount=true} and the SoA rendered successfully. Null on either suppression
 * (caller did not request) or best-effort failure (mirrors closure-letter shape).
 */
public record CloseMatterResponse(
    UUID projectId,
    String status,
    Instant closedAt,
    UUID closureLogId,
    UUID closureLetterDocumentId,
    UUID statementOfAccountDocumentId,
    LocalDate retentionEndsAt) {}
