package io.b2mash.b2b.b2bstrawman.audit;

import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.OrgSchemaMappingRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/audit-events")
public class InternalAuditController {

  private static final Logger log = LoggerFactory.getLogger(InternalAuditController.class);

  private final AuditService auditService;
  private final OrgSchemaMappingRepository mappingRepository;

  public InternalAuditController(
      AuditService auditService, OrgSchemaMappingRepository mappingRepository) {
    this.auditService = auditService;
    this.mappingRepository = mappingRepository;
  }

  @GetMapping
  public ResponseEntity<Page<InternalAuditEventResponse>> listAuditEvents(
      @RequestParam String orgId,
      @RequestParam(required = false) String entityType,
      @RequestParam(required = false) UUID entityId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size)
      throws Exception {

    String schema = resolveSchema(orgId);
    var filter = new AuditEventFilter(entityType, entityId, actorId, eventType, from, to);
    var pageable =
        PageRequest.of(page, Math.min(size, 200), Sort.by(Sort.Direction.DESC, "occurredAt"));

    Page<AuditEvent> events =
        ScopedValue.where(RequestScopes.TENANT_ID, schema)
            .where(RequestScopes.ORG_ID, orgId)
            .call(() -> auditService.findEvents(filter, pageable));

    return ResponseEntity.ok(events.map(InternalAuditEventResponse::from));
  }

  @GetMapping("/stats")
  public ResponseEntity<AuditStatsResponse> getStats(@RequestParam String orgId) throws Exception {

    String schema = resolveSchema(orgId);

    List<AuditEventRepository.EventTypeCount> counts =
        ScopedValue.where(RequestScopes.TENANT_ID, schema)
            .where(RequestScopes.ORG_ID, orgId)
            .call(() -> auditService.countEventsByType());

    long totalEvents =
        counts.stream().mapToLong(AuditEventRepository.EventTypeCount::getCount).sum();
    List<EventTypeStat> stats =
        counts.stream().map(c -> new EventTypeStat(c.getEventType(), c.getCount())).toList();

    return ResponseEntity.ok(new AuditStatsResponse(stats, totalEvents));
  }

  // --- Schema Resolution ---

  private String resolveSchema(String orgId) {
    return mappingRepository
        .findByClerkOrgId(orgId)
        .orElseThrow(
            () ->
                ResourceNotFoundException.withDetail(
                    "Organization not found", "No tenant provisioned for orgId: " + orgId))
        .getSchemaName();
  }

  // --- DTOs ---

  public record InternalAuditEventResponse(
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
      Instant occurredAt) {

    public static InternalAuditEventResponse from(AuditEvent event) {
      return new InternalAuditEventResponse(
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
          event.getOccurredAt());
    }
  }

  public record AuditStatsResponse(List<EventTypeStat> eventTypeCounts, long totalEvents) {}

  public record EventTypeStat(String eventType, long count) {}
}
