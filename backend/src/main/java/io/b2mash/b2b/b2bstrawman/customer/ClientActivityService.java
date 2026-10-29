package io.b2mash.b2b.b2bstrawman.customer;

import io.b2mash.b2b.b2bstrawman.audit.AuditEvent;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventMetadataResolver;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository;
import io.b2mash.b2b.b2bstrawman.audit.AuditEventResponse;
import io.b2mash.b2b.b2bstrawman.exception.EntityNoun;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The client Activity feed (Phase 88 625C, U6, G-B2): the client's own audit rows, rows on the
 * entities it owns (FICA checklist instances + items, information requests + items, portal
 * contacts) and rows whose publisher named it in {@code details.customer_id} — see {@link
 * AuditEventRepository#findClientActivity}. Rows carry the same read-time enrichment (label /
 * severity / group / actor display name) and the same raw details as the firm audit trail, behind
 * the same {@code TEAM_OVERSIGHT} gate — no more is exposed than {@code GET /api/audit-events}
 * already returns to that caller.
 */
@Service
public class ClientActivityService {

  /** Same cap as the firm audit-trail endpoints (AuditEventController). */
  static final int MAX_PAGE_SIZE = 200;

  private final CustomerRepository customerRepository;
  private final AuditEventRepository auditEventRepository;
  private final AuditEventMetadataResolver auditEventMetadataResolver;

  public ClientActivityService(
      CustomerRepository customerRepository,
      AuditEventRepository auditEventRepository,
      AuditEventMetadataResolver auditEventMetadataResolver) {
    this.customerRepository = customerRepository;
    this.auditEventRepository = auditEventRepository;
    this.auditEventMetadataResolver = auditEventMetadataResolver;
  }

  @Transactional(readOnly = true)
  public Page<AuditEventResponse> getClientActivity(UUID clientId, int page, int size) {
    if (!customerRepository.existsById(clientId)) {
      throw new ResourceNotFoundException(EntityNoun.CLIENT, clientId);
    }
    // Sort-free: the ordering (occurred_at DESC, id DESC) is baked into the native query.
    var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    Page<AuditEvent> events = auditEventRepository.findClientActivity(clientId, pageable);
    var rows =
        auditEventMetadataResolver.enrich(events.getContent()).stream()
            .map(AuditEventResponse::from)
            .toList();
    return new PageImpl<>(rows, pageable, events.getTotalElements());
  }
}
