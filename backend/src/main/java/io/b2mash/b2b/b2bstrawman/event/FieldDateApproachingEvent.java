package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event published when a custom field date on a customer or project is approaching. This is
 * a system-generated event (no human actor) — {@code actorMemberId} is null and {@code actorName}
 * is "system".
 *
 * <p>The {@code details} map carries: {@code field_name}, {@code field_label}, {@code field_value}
 * (ISO date), {@code days_until} (integer), and {@code entity_name}.
 */
public record FieldDateApproachingEvent(
    String eventType,
    String entityType,
    UUID entityId,
    UUID projectId,
    UUID actorMemberId,
    String actorName,
    String tenantId,
    String orgId,
    Instant occurredAt,
    Map<String, Object> details)
    implements DomainEvent {}
