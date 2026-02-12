package io.b2mash.b2b.b2bstrawman.audit;

import java.time.Instant;
import java.util.UUID;

/**
 * Query filter record for {@link AuditService#findEvents}. All fields are nullable -- null means
 * "no filter on this field".
 *
 * @param entityType filter by entity kind (e.g., "task", "document")
 * @param entityId filter by specific entity
 * @param actorId filter by acting member
 * @param eventType filter by event type (prefix match -- "task." matches task.created,
 *     task.updated)
 * @param from start of time range (inclusive)
 * @param to end of time range (exclusive)
 */
public record AuditEventFilter(
    String entityType, UUID entityId, UUID actorId, String eventType, Instant from, Instant to) {}
