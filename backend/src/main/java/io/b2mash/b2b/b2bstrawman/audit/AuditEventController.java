package io.b2mash.b2b.b2bstrawman.audit;

import io.b2mash.b2b.b2bstrawman.audit.export.AuditExportService;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * HTTP read surfaces for the firm-wide audit log. Per architecture §12.3.1 / §12.3.5, all routes
 * require the {@code TEAM_OVERSIGHT} capability and project read-time enrichment (label / severity
 * / group / actor display name) onto each row. Enrichment lives in {@link
 * AuditService#findEventsEnriched}; this controller stays a pure HTTP adapter per Backend
 * Controller Discipline.
 */
@RestController
public class AuditEventController {

  /** Default lookback when {@code from} is omitted on the facet endpoints. */
  private static final long DEFAULT_FACET_LOOKBACK_DAYS = 30;

  private final AuditService auditService;
  private final AuditEventTypeRegistry auditEventTypeRegistry;
  private final AuditExportService auditExportService;

  public AuditEventController(
      AuditService auditService,
      AuditEventTypeRegistry auditEventTypeRegistry,
      AuditExportService auditExportService) {
    this.auditService = auditService;
    this.auditEventTypeRegistry = auditEventTypeRegistry;
    this.auditExportService = auditExportService;
  }

  @GetMapping("/api/audit-events")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<Page<AuditEventResponse>> listAuditEvents(
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) UUID entityId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) Set<AuditSeverity> severities,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {

    var filter =
        new AuditEventFilter(entityType, entityId, actorId, eventType, from, to, severities);
    var pageable = PageRequest.of(page, Math.min(size, 200));
    return ResponseEntity.ok(
        auditService.findEventsEnriched(filter, pageable).map(AuditEventResponse::from));
  }

  @GetMapping("/api/audit-events/export.csv")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<StreamingResponseBody> exportAuditEventsCsv(
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) UUID entityId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) Set<AuditSeverity> severities) {
    return auditExportService.streamCsv(
        new AuditEventFilter(entityType, entityId, actorId, eventType, from, to, severities));
  }

  @GetMapping("/api/audit-events/export.pdf")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<?> exportAuditEventsPdf(
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) UUID entityId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) Set<AuditSeverity> severities) {
    return auditExportService.streamPdf(
        new AuditEventFilter(entityType, entityId, actorId, eventType, from, to, severities));
  }

  @GetMapping("/api/audit-events/metadata")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<List<AuditEventTypeMetadata>> getMetadata() {
    return ResponseEntity.ok(auditEventTypeRegistry.entries());
  }

  @GetMapping("/api/audit-events/{entityType}/{entityId}")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<Page<AuditEventResponse>> listAuditEventsByEntity(
      @PathVariable String entityType,
      @PathVariable UUID entityId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {

    var filter = new AuditEventFilter(entityType, entityId, actorId, eventType, from, to, null);
    var pageable = PageRequest.of(page, Math.min(size, 200));
    return ResponseEntity.ok(
        auditService.findEventsEnriched(filter, pageable).map(AuditEventResponse::from));
  }

  @GetMapping("/api/audit-events/facets/actors")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<List<ActorFacet>> listActorFacets(
      @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
    var range = defaultedRange(from, to);
    return ResponseEntity.ok(auditService.facets(range.from(), range.to()).actors());
  }

  @GetMapping("/api/audit-events/facets/event-types")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<List<EventTypeFacet>> listEventTypeFacets(
      @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
    var range = defaultedRange(from, to);
    return ResponseEntity.ok(auditService.facets(range.from(), range.to()).eventTypes());
  }

  @GetMapping("/api/audit-events/facets/entity-types")
  @RequiresCapability("TEAM_OVERSIGHT")
  public ResponseEntity<List<EntityTypeFacet>> listEntityTypeFacets(
      @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
    var range = defaultedRange(from, to);
    return ResponseEntity.ok(auditService.facets(range.from(), range.to()).entityTypes());
  }

  /**
   * Defaults missing range bounds: {@code from} → now − 30d, {@code to} → now. Pure parameter
   * binding — no business logic.
   */
  private static Range defaultedRange(Instant from, Instant to) {
    var now = Instant.now();
    var resolvedTo = to != null ? to : now;
    var resolvedFrom =
        from != null ? from : now.minus(DEFAULT_FACET_LOOKBACK_DAYS, ChronoUnit.DAYS);
    return new Range(resolvedFrom, resolvedTo);
  }

  private record Range(Instant from, Instant to) {}
}
