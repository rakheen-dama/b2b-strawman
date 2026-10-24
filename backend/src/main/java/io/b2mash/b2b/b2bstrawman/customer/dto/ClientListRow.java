package io.b2mash.b2b.b2bstrawman.customer.dto;

import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One row of {@code GET /api/clients} (Phase 87, Epic 615B, arch §12.2.6) — a server-side list
 * projection composed from batched aggregates; owns no table. The detail endpoint keeps {@link
 * ClientDtos.ClientResponse}.
 *
 * <p>{@code outstandingFeeNoteTotal} (SENT or PARTIALLY_PAID fee notes with a due date at their
 * balance due — the debtor-book definition, {@code CollectionsSql}) is null without
 * FINANCIAL_VISIBILITY (owner/admin always see it); {@code trustBalance} (client-level SUM of
 * ledger cards) is null without VIEW_TRUST or with trust accounting off. Counts and {@code
 * currency} are never redacted. {@code completenessPercent} is null when no required field applies
 * to the client (N/A). {@code lifecycleStatusChangedAt} is carried (no extra query) for list
 * consumers that read it. Nulls are serialized explicitly so the table can render "—".
 */
public record ClientListRow(
    UUID id,
    String name,
    String email,
    String phone,
    CustomerType clientType,
    LifecycleStatus lifecycleStatus,
    String status,
    Instant createdAt,
    Instant lifecycleStatusChangedAt,
    String ficaStatus,
    Instant ficaLastVerifiedAt,
    BigDecimal outstandingFeeNoteTotal,
    int outstandingFeeNoteCount,
    String currency,
    BigDecimal trustBalance,
    int openMatterCount,
    Integer completenessPercent,
    List<TagResponse> tags,
    Map<String, Object> customFields) {}
