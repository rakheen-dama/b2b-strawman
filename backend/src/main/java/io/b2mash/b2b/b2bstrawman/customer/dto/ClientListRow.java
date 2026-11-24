package io.b2mash.b2b.b2bstrawman.customer.dto;

import io.b2mash.b2b.b2bstrawman.customer.CustomerType;
import io.b2mash.b2b.b2bstrawman.customer.LifecycleStatus;
import io.b2mash.b2b.b2bstrawman.tag.dto.TagResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
 *
 * <p>FICA (Phase 93 655B, §17.3.5): {@code ficaStatus} is one of NOT_STARTED, IN_PROGRESS, DONE,
 * ATTESTED, ATTESTED_REVIEW; {@code ficaReviewReasons} (never null) says why an {@code
 * ATTESTED_REVIEW} client is due for review. {@code ficaRiskRating}, {@code ficaNextReviewDate} and
 * {@code ficaRecordsLocation} come from the client's active FICA attestation and are null when it
 * has none ({@code ficaNextReviewDate} is also null when the rating is {@code UNRATED}) — read in
 * the same batch as the status, no extra query. Those four are attestation detail: a caller without
 * CUSTOMER_MANAGEMENT gets {@code ficaReviewReasons} empty and the other three null (D-148); {@code
 * ficaStatus} is never redacted.
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
    List<String> ficaReviewReasons,
    String ficaRiskRating,
    LocalDate ficaNextReviewDate,
    String ficaRecordsLocation,
    BigDecimal outstandingFeeNoteTotal,
    int outstandingFeeNoteCount,
    String currency,
    BigDecimal trustBalance,
    int openMatterCount,
    Integer completenessPercent,
    List<TagResponse> tags,
    Map<String, Object> customFields) {}
