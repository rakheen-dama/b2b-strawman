package io.b2mash.b2b.b2bstrawman.audit;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventMetadataResolver.EnrichedAuditEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP DTO for audit-event read endpoints. Carries the persisted row fields plus four read-time
 * enrichment fields ({@code label}, {@code severity}, {@code group}, {@code actorDisplayName})
 * resolved per ADR-261 / architecture §12.3.4.
 *
 * <p>The {@link #from(AuditEvent)} factory is retained for callers that have not adopted the
 * enriched read path; new callers should prefer {@link #from(EnrichedAuditEvent)} so the response
 * includes the registry-derived metadata and resolved actor display name.
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
   * Legacy unenriched factory — preserved for backwards compatibility. The four enrichment fields
   * are returned as {@code null}. Prefer {@link #from(EnrichedAuditEvent)} on new call sites.
   */
  public static AuditEventResponse from(AuditEvent event) {
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
        null,
        null,
        null,
        null);
  }

  /**
   * Enriched factory — pulls {@code label}/{@code severity}/{@code group} from the registry
   * metadata and {@code actorDisplayName} from the resolver's batch lookup.
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
