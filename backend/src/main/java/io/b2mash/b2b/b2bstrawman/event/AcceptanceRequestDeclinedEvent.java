package io.b2mash.b2b.b2bstrawman.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published when a portal contact declines a document (Phase 88 U23). Named after its {@code
 * AcceptanceRequest*Event} siblings (OQ #7; the architecture's {@code AcceptanceDeclinedEvent}).
 * {@code projectId} is the resolved matter (nullable); {@code declineReason} is null when the
 * contact gave none.
 */
public record AcceptanceRequestDeclinedEvent(
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
    String documentName,
    String contactName,
    String declineReason)
    implements DomainEvent {}
