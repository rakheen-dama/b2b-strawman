package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Published by {@code CourtCalendarService} when a court date becomes, or remains, a live portal
 * deadline: create; update of type / date / court / reference on a SCHEDULED row; the new row of a
 * postpone; an outcome recorded on a SCHEDULED row ({@code status = HEARD}). Consumed AFTER_COMMIT
 * by {@code DeadlinePortalSyncService} (Phase 89 633B, ADR-363).
 *
 * <p>Deliberately carries NO judge / magistrate, outcome or free-text description — neither as a
 * component nor in {@code details} (B10, the sanitised-copy rule by construction). The {@code
 * details} map carries {@code date_type}, {@code scheduled_date}, {@code court_name}, {@code
 * court_reference}, {@code status} and {@code project_id} (ADR-349).
 *
 * @param status {@code SCHEDULED} or {@code HEARD} — never {@code POSTPONED} from the publishers
 */
public record CourtDateScheduledEvent(
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
    String dateType,
    LocalDate scheduledDate,
    String courtName,
    String courtReference,
    String status)
    implements DomainEvent {}
