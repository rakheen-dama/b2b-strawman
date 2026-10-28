package io.b2mash.b2b.b2bstrawman.verticals.legal.closure.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response returned by {@code POST /api/matters/{matterId}/closure/close}.
 *
 * <p>{@code retentionEndsAt} is the persisted {@code projects.retention_ends_on} stamped by the
 * close: the UTC close date + the firm's retention years at close time (ADR-354). It is the same
 * value {@code GET /api/matters/{id}} returns as {@code retentionEndsOn}.
 *
 * <p>{@code closureLetterDocumentId} (nullable) — set when the request had {@code
 * generateClosureLetter=true} and the letter rendered successfully.
 *
 * <p>{@code statementOfAccountDocumentId} (nullable, GAP-L-93) — set when the request had {@code
 * generateStatementOfAccount=true} and the SoA rendered successfully. Null on either suppression
 * (caller did not request) or best-effort failure (mirrors closure-letter shape).
 */
public record CloseMatterResponse(
    UUID matterId,
    String status,
    Instant closedAt,
    UUID closureLogId,
    UUID closureLetterDocumentId,
    UUID statementOfAccountDocumentId,
    LocalDate retentionEndsAt) {}
