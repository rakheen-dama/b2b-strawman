package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TimeEntryChangedEvent(
    String eventType,
    String entityType,
    UUID entityId,
    UUID projectId,
    String action,
    UUID actorMemberId,
    String actorName,
    String tenantId,
    String orgId,
    Instant occurredAt,
    Map<String, Object> details)
    implements DomainEvent {}
