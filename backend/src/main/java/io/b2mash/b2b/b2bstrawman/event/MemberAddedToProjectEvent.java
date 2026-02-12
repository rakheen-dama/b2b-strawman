package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record MemberAddedToProjectEvent(
    String eventType,
    String entityType,
    UUID entityId,
    UUID projectId,
    UUID actorMemberId,
    String actorName,
    String tenantId,
    Instant occurredAt,
    Map<String, Object> details,
    UUID addedMemberId,
    String projectName)
    implements DomainEvent {}
