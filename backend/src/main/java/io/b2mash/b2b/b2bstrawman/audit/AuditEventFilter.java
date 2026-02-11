package io.b2mash.b2b.b2bstrawman.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Query filter for searching audit events. All fields are nullable — a null field means "no filter
 * on this dimension". Used by {@code AuditService.findEvents()} and the repository's {@code
 * findByFilter()} JPQL query.
 */
public record AuditEventFilter(
    String entityType, UUID entityId, UUID actorId, String eventType, Instant from, Instant to) {}
