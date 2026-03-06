package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when an information request transitions from DRAFT to SENT. Carries enough context for
 * downstream consumers (portal sync, notifications, audit, activity feed).
 */
public record InformationRequestSentEvent(
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
    UUID customerId,
    UUID portalContactId)
    implements DomainEvent {}
