package io.b2mash.b2b.b2bstrawman.audit;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read-time enrichment helper that combines {@link AuditEventTypeRegistry} (severity / group /
 * label resolution) with {@link AuditService#resolveActorDisplay(java.util.UUID, String)} (actor
 * display-name fallback) into a single call.
 *
 * <p>Intended for use by the firm-audit view and any future single-event read paths. Per ADR-261,
 * severity and group are <strong>derived at read time</strong> — calling {@link
 * #enrich(AuditEvent)} on the same row tomorrow may yield a different metadata if the catalogue has
 * changed.
 */
@Service
public class AuditEventMetadataResolver {

  private final AuditEventTypeRegistry registry;
  private final AuditService auditService;

  public AuditEventMetadataResolver(AuditEventTypeRegistry registry, AuditService auditService) {
    this.registry = registry;
    this.auditService = auditService;
  }

  /**
   * Enriches a single audit event with its registry-resolved metadata and a human-readable actor
   * display name. The display-name resolution depends on both {@link AuditEvent#getActorId()} and
   * {@link AuditEvent#getActorType()} per architecture §12.3.4.
   *
   * @param event the row to enrich (not null)
   * @return an {@link EnrichedAuditEvent} carrying the original event plus the derived metadata and
   *     actor display name
   */
  public EnrichedAuditEvent enrich(AuditEvent event) {
    var metadata = registry.resolve(event.getEventType());
    var actorDisplayName =
        auditService.resolveActorDisplay(event.getActorId(), event.getActorType());
    return new EnrichedAuditEvent(event, metadata, actorDisplayName);
  }

  /**
   * Enriches a batch of audit events. Internally fires a single LEFT-JOIN-style member lookup via
   * {@link AuditService#resolveActorDisplayNames(java.util.Collection)} so N events do not produce
   * N+1 queries.
   *
   * @param events the rows to enrich (each non-null; may be empty)
   * @return the enriched events in input order
   */
  public List<EnrichedAuditEvent> enrich(List<AuditEvent> events) {
    if (events == null || events.isEmpty()) {
      return List.of();
    }
    // Deduplicate actor IDs before the bulk lookup — N events with K distinct actors should issue
    // one IN-clause of size K, not size N (the Set in DatabaseAuditService dedupes too, but doing
    // it here keeps the contract obvious at the call site).
    var actorIds =
        events.stream()
            .map(AuditEvent::getActorId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    var nameLookup = auditService.resolveActorDisplayNames(actorIds);
    return events.stream()
        .map(
            event -> {
              var metadata = registry.resolve(event.getEventType());
              var actorDisplayName =
                  resolveDisplay(event.getActorId(), event.getActorType(), nameLookup);
              return new EnrichedAuditEvent(event, metadata, actorDisplayName);
            })
        .toList();
  }

  private static String resolveDisplay(
      java.util.UUID actorId, String actorType, java.util.Map<java.util.UUID, String> nameLookup) {
    if ("USER".equals(actorType)) {
      if (actorId == null) {
        return "System";
      }
      var name = nameLookup.get(actorId);
      return name != null ? name : "Former member (" + actorId + ")";
    }
    // Non-USER types share their labels with DatabaseAuditService.resolveActorDisplay so the two
    // paths cannot drift when a new actor type is added.
    return DatabaseAuditService.staticActorLabel(actorType);
  }

  /**
   * Read-time enrichment payload pairing a raw {@link AuditEvent} with its derived metadata and
   * actor display name. Severity / group / label come from {@link AuditEventTypeRegistry}; the
   * display name follows the architecture §12.3.4 fallback table.
   */
  public record EnrichedAuditEvent(
      AuditEvent event, AuditEventTypeMetadata metadata, String actorDisplayName) {}
}
