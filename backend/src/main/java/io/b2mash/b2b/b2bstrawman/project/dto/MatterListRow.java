package io.b2mash.b2b.b2bstrawman.project.dto;

import io.b2mash.b2b.b2bstrawman.project.ProjectPriority;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One row of {@code GET /api/matters} (Phase 87, Epic 615A, arch §12.2.6) — a server-side list
 * projection composed from batched aggregates; owns no table. The detail endpoint keeps {@link
 * MatterResponse}.
 *
 * <p>{@code unbilledValue} (time value in the org currency + approved unbilled disbursements) is
 * null without FINANCIAL_VISIBILITY; {@code trustBalance} is client-level (§12.14 #12) and null
 * without VIEW_TRUST, with trust accounting off, or when the matter has no client. Minutes and
 * counts are never redacted. {@code description} is carried (no extra query) so list consumers that
 * render it keep working. {@code nextDiaryKind} is a {@code DiaryKind} name. Nulls are serialized
 * explicitly so the table can render "—".
 */
public record MatterListRow(
    UUID id,
    String name,
    String description,
    String referenceNumber,
    String status,
    String workType,
    ProjectPriority priority,
    LocalDate dueDate,
    Instant createdAt,
    UUID clientId,
    String clientName,
    UUID feeEarnerId,
    String feeEarnerName,
    String matterRole,
    int unbilledMinutes,
    BigDecimal unbilledValue,
    String currency,
    BigDecimal trustBalance,
    LocalDate nextDiaryDate,
    String nextDiaryKind,
    int openTaskCount,
    List<TagResponse> tags,
    Map<String, Object> customFields) {}
