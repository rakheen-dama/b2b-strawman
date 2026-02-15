package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CustomerStatusChangedEvent(
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
    String customerName,
    String oldStatus,
    String newStatus)
    implements DomainEvent {}
