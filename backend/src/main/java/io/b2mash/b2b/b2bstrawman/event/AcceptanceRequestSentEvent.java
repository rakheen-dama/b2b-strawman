package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when an acceptance request is created and the request email is sent successfully.
 * Carries enough context for downstream consumers (notifications, audit, activity feed).
 */
public record AcceptanceRequestSentEvent(
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
    UUID generatedDocumentId,
    UUID portalContactId,
    UUID customerId,
    String documentTitle,
    String documentFileName,
    String requestToken,
    Instant expiresAt,
    String contactName,
    String contactEmail)
    implements DomainEvent {}
