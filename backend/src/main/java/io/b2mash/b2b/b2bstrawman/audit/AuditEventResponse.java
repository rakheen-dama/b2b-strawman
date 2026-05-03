package io.b2mash.b2b.b2bstrawman.audit;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventMetadataResolver.EnrichedAuditEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP DTO for audit-event read endpoints. Carries the persisted row fields plus four read-time
 * enrichment fields ({@code label}, {@code severity}, {@code group}, {@code actorDisplayName})
 * resolved per ADR-261 / architecture §12.3.4.
 */
public record AuditEventResponse(
    UUID id,
    String eventType,
    String entityType,
    UUID entityId,
    UUID actorId,
    String actorType,
    String source,
    String ipAddress,
    String userAgent,
    Map<String, Object> details,
    Instant occurredAt,
    String label,
    AuditSeverity severity,
    AuditEventGroup group,
    String actorDisplayName) {

  /**
   * Builds a response from an {@link EnrichedAuditEvent} — pulls {@code label}/{@code severity}/
   * {@code group} from the registry metadata and {@code actorDisplayName} from the service's batch
   * lookup. Pure record-to-record conversion, safe to use from a controller.
   */
  public static AuditEventResponse from(EnrichedAuditEvent enriched) {
    var event = enriched.event();
    var metadata = enriched.metadata();
    return new AuditEventResponse(
        event.getId(),
        event.getEventType(),
        event.getEntityType(),
        event.getEntityId(),
        event.getActorId(),
        event.getActorType(),
        event.getSource(),
        event.getIpAddress(),
        event.getUserAgent(),
        event.getDetails(),
        event.getOccurredAt(),
        metadata.label(),
        metadata.severity(),
        metadata.group(),
        enriched.actorDisplayName());
  }
}
