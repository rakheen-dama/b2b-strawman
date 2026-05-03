package io.b2mash.b2b.b2bstrawman.audit;

import java.time.Instant;
import java.util.Set;
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
 * @param severities optional set of registry-resolved severities to restrict the result to. {@code
 *     null} or empty Set means "no severity filter" (current behaviour). Per ADR-261 severity is
 *     derived at read time via {@link AuditEventTypeRegistry#resolve(String)} -- the implementation
 *     walks the registry to compute the eventType set passed to the DB filter (architecture
 *     §12.3.5).
 */
public record AuditEventFilter(
    String entityType,
    UUID entityId,
    UUID actorId,
    String eventType,
    Instant from,
    Instant to,
    Set<AuditSeverity> severities) {}
