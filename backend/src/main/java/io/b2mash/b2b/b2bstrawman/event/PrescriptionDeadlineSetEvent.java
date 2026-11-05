package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Published by {@code PrescriptionTrackerService} when a prescription tracker is created or
 * updated. Consumed AFTER_COMMIT by {@code DeadlinePortalSyncService} (Phase 89 633B, ADR-363).
 *
 * <p>Deliberately carries NO cause-of-action date, notes or interruption data — neither as a
 * component nor in {@code details} (B10). The {@code details} map carries {@code
 * prescription_type}, {@code prescription_date} and {@code project_id} (ADR-349).
 */
public record PrescriptionDeadlineSetEvent(
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
    String prescriptionType,
    LocalDate prescriptionDate)
    implements DomainEvent {}
