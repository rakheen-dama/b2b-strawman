package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published by {@code PrescriptionTrackerService} when a prescription tracker is interrupted.
 * Consumed AFTER_COMMIT by {@code DeadlinePortalSyncService}, which deletes the portal deadline row
 * (Phase 89 633B, ADR-363, B4).
 *
 * <p>{@code reason} is the literal {@code INTERRUPTED} — never the firm's interruption reason
 * (B10). The {@code details} map carries {@code reason} (same literal) and {@code project_id}
 * (ADR-349).
 */
public record PrescriptionDeadlineClearedEvent(
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
