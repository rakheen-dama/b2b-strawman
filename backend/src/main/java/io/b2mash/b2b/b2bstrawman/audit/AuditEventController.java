package io.b2mash.b2b.b2bstrawman.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditEventController {

  private final AuditService auditService;

  public AuditEventController(AuditService auditService) {
    this.auditService = auditService;
  }

  @GetMapping("/api/audit-events")
  @PreAuthorize("hasAnyRole('ORG_OWNER', 'ORG_ADMIN')")
  public ResponseEntity<Page<AuditEventResponse>> listAuditEvents(
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) UUID entityId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {

    var filter = new AuditEventFilter(entityType, entityId, actorId, eventType, from, to);
    var pageable =
        PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "occurredAt"));
    var events = auditService.findEvents(filter, pageable);

    return ResponseEntity.ok(events.map(AuditEventResponse::from));
  }

  @GetMapping("/api/audit-events/{entityType}/{entityId}")
  @PreAuthorize("hasAnyRole('ORG_OWNER', 'ORG_ADMIN')")
  public ResponseEntity<Page<AuditEventResponse>> listAuditEventsByEntity(
      @PathVariable String entityType,
      @PathVariable UUID entityId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {

    var filter = new AuditEventFilter(entityType, entityId, actorId, eventType, from, to);
    var pageable =
        PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "occurredAt"));
    var events = auditService.findEvents(filter, pageable);

    return ResponseEntity.ok(events.map(AuditEventResponse::from));
  }

  // --- DTO ---

  public record AuditEventResponse(
      UUID id,
      String eventType,
      String entityType,
      UUID entityId,
      UUID actorId,
      String actorType,
      String source,
      Map<String, Object> details,
      Instant occurredAt) {

    public static AuditEventResponse from(AuditEvent event) {
      return new AuditEventResponse(
          event.getId(),
          event.getEventType(),
          event.getEntityType(),
          event.getEntityId(),
          event.getActorId(),
          event.getActorType(),
          event.getSource(),
          event.getDetails(),
          event.getOccurredAt());
    }
  }
}
