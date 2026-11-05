package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published by {@code CourtCalendarService} when a court date is cancelled, and for the ORIGINAL
 * row of a postpone. Consumed AFTER_COMMIT by {@code DeadlinePortalSyncService}, which deletes the
 * portal deadline row (Phase 89 633B, ADR-363, B4).
 *
 * <p>{@code reason} is the status literal ({@code CANCELLED} or {@code POSTPONED}) — never the
 * user's free text, which the firm stores in the court date's {@code outcome} field (B10). The
 * {@code details} map carries {@code reason} (same literal) and {@code project_id} (ADR-349).
 */
public record CourtDateCancelledEvent(
    String eventType,
    String entityType,
    UUID entityId,
    UUID projectId,
    UUID actorMemberId,
    String actorName,
    String tenantId,
    String orgId,
    Instant occurredAt,
    Map<String, Object> details,
    UUID customerId,
    String reason)
    implements DomainEvent {}
