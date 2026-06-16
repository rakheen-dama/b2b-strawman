package io.b2mash.b2b.b2bstrawman.mcp.dto;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventMetadataResolver.EnrichedAuditEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * One audit event for {@code get_audit_events} (Epic 564B), projected from an {@link
 * EnrichedAuditEvent}. {@code summary} carries the registry-resolved human label; {@code actor} the
 * resolved display name. Events are returned newest-first ({@code occurredAt DESC}) — the ordering
 * is fixed by the audit query, not the caller.
 *
 * @param occurredAt when the event occurred
 * @param eventType machine event type (e.g. {@code task.created})
 * @param actor resolved actor display name
 * @param entityType the kind of entity the event is about
 * @param entityId the entity id
 * @param summary human-readable event label
 */
public record McpAuditEventItem(
    Instant occurredAt,
    String eventType,
    String actor,
    String entityType,
    UUID entityId,
    String summary) {

  /** Projects an enriched audit event into the MCP item. */
  public static McpAuditEventItem from(EnrichedAuditEvent e) {
    return new McpAuditEventItem(
        e.event().getOccurredAt(),
        e.event().getEventType(),
        e.actorDisplayName(),
        e.event().getEntityType(),
        e.event().getEntityId(),
        e.metadata() != null ? e.metadata().label() : null);
  }
}
