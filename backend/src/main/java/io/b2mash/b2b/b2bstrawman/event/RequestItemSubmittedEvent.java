package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when a portal contact submits a response for a request item. This event is published
 * from the portal service (Epic 254B), not from InformationRequestService.
 */
public record RequestItemSubmittedEvent(
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
    UUID itemId,
    UUID customerId,
    UUID portalContactId)
    implements DomainEvent {}
