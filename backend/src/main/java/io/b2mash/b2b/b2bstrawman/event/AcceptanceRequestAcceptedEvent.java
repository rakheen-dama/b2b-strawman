package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Published when a portal contact accepts a document. */
public record AcceptanceRequestAcceptedEvent(
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
    UUID requestId,
    UUID sentByMemberId,
    String documentFileName,
    String contactName)
    implements DomainEvent {}
