package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record TaskRecurrenceCreatedEvent(
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
    UUID assigneeMemberId,
    String taskTitle,
    LocalDate nextDueDate,
    UUID parentTaskId)
    implements DomainEvent {}
