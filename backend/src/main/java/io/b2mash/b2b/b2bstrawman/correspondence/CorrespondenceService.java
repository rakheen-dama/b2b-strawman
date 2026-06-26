package io.b2mash.b2b.b2bstrawman.correspondence;

import io.b2mash.b2b.b2bstrawman.correspondence.dto.CorrespondenceListResponse;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceCommand;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceResult;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.mcp.McpPagination;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence foundation for inbound correspondence. {@link #fileInbound} is idempotent on {@code
 * messageId}: a re-file returns the existing record's id and never re-links it. Linkage (at least
 * one of customer/matter) is validated at the service level; the table CHECK is the
 * defence-in-depth backstop.
 */
@Service
public class CorrespondenceService {

  private final CorrespondenceRepository correspondenceRepository;
  private final ProjectAccessService projectAccessService;
  private final CustomerRepository customerRepository;

  public CorrespondenceService(
      CorrespondenceRepository correspondenceRepository,
      ProjectAccessService projectAccessService,
      CustomerRepository customerRepository) {
    this.correspondenceRepository = correspondenceRepository;
    this.projectAccessService = projectAccessService;
    this.customerRepository = customerRepository;
  }

  /**
   * File an inbound correspondence. Idempotent on {@code messageId}: a re-file returns the existing
   * id without persisting a second row or mutating the existing record.
   */
  @Transactional
  public FileCorrespondenceResult fileInbound(FileCorrespondenceCommand cmd, ActorContext actor) {
    // Idempotency FIRST: a replay of an already-filed messageId must return the existing id even if
    // this replay's linkage args are now invalid (e.g. both-null). Validating linkage before the
    // lookup would throw on a replay and break the idempotent-on-messageId contract.
    var existing = correspondenceRepository.findByMessageId(cmd.messageId());
    if (existing.isPresent()) {
      return FileCorrespondenceResult.idempotent(existing.get().getId());
    }

    validateLinkage(cmd.customerId(), cmd.matterId());

    var correspondence =
        new Correspondence(
            cmd.customerId(),
            cmd.matterId(),
            cmd.subject(),
            cmd.bodyText(),
            cmd.bodyHtml(),
            cmd.fromAddress(),
            cmd.toAddresses(),
            cmd.ccAddresses(),
            cmd.sentAt(),
            cmd.receivedAt(),
            cmd.threadKey(),
            cmd.messageId(),
            cmd.source(),
            actor.memberId());

    try {
      // saveAndFlush forces the INSERT now so the unique(message_id) violation fires inside this
      // try/catch (plain save() defers to commit, outside the catch — see
      // PortalNotificationPreferenceService.getOrCreate for the codebase precedent).
      var saved = correspondenceRepository.saveAndFlush(correspondence);
      return FileCorrespondenceResult.created(saved.getId());
    } catch (DataIntegrityViolationException race) {
      // Race backstop: a concurrent insert won the unique(message_id). Re-read the winner.
      return correspondenceRepository
          .findByMessageId(cmd.messageId())
          .map(winner -> FileCorrespondenceResult.idempotent(winner.getId()))
          .orElseThrow(() -> race);
    }
  }

  /**
   * Resolve a correspondence's {@link CorrespondenceScope} by id (Phase 81, {@code
   * attach_document}). Returns only the customer/project ids the caller needs to choose CUSTOMER vs
   * PROJECT upload scope — the JPA entity never crosses the MCP/service boundary. Tenant-isolated
   * via {@code search_path}. Throws {@link ResourceNotFoundException} when the id is unknown in
   * this tenant, which the CONFIRM phase relies on to reject a fabricated or wrong-tenant {@code
   * correspondenceId} before any stamp is applied.
   */
  @Transactional(readOnly = true)
  public CorrespondenceScope requireScopeById(UUID id) {
    return correspondenceRepository
        .findById(id)
        .map(CorrespondenceScope::of)
        .orElseThrow(() -> new ResourceNotFoundException("Correspondence", id));
  }

  /**
   * View-access-gated, page-cap-clamped project correspondence list for the in-app REST endpoint.
   * Enforces the SAME project view-access as the documents-list endpoints (throws {@link
   * ResourceNotFoundException} → 404 when the caller cannot view the matter, security-by-obscurity)
   * — NOT MCP capabilities. Clamps the requested page size to {@link
   * McpPagination#DEFAULT_MAX_SIZE} so the LLM/UI never pulls an unbounded blob.
   */
  @Transactional(readOnly = true)
  public Page<CorrespondenceListResponse> listForProject(
      UUID projectId, ActorContext actor, Pageable pageable) {
    projectAccessService.requireViewAccess(projectId, actor);
    return listByProject(projectId, clamp(pageable));
  }

  /**
   * Page-cap-clamped customer correspondence list for the in-app REST endpoint. Customer
   * view-access for a read is existence-in-tenant + authenticated (search_path isolation guarantees
   * a cross-tenant id is invisible) — there is no per-customer {@code requireViewAccess}, so no
   * {@code ActorContext} is needed here. Throws {@link ResourceNotFoundException} → 404 when the
   * customer is unknown in this tenant.
   */
  @Transactional(readOnly = true)
  public Page<CorrespondenceListResponse> listForCustomer(UUID customerId, Pageable pageable) {
    customerRepository
        .findById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    return listByCustomer(customerId, clamp(pageable));
  }

  private Pageable clamp(Pageable pageable) {
    if (pageable.getSort().isSorted()) {
      throw new InvalidStateException(
          "Invalid correspondence sort", "Correspondence lists support newest-first ordering only");
    }
    int size = McpPagination.clampSize(pageable.getPageSize(), McpPagination.DEFAULT_MAX_SIZE);
    return PageRequest.of(pageable.getPageNumber(), size);
  }

  @Transactional(readOnly = true)
  public Page<CorrespondenceListResponse> listByProject(UUID projectId, Pageable pageable) {
    return correspondenceRepository.findByProjectId(projectId, pageable).map(this::toListResponse);
  }

  @Transactional(readOnly = true)
  public Page<CorrespondenceListResponse> listByCustomer(UUID customerId, Pageable pageable) {
    return correspondenceRepository
        .findByCustomerId(customerId, pageable)
        .map(this::toListResponse);
  }

  private CorrespondenceListResponse toListResponse(Correspondence c) {
    return new CorrespondenceListResponse(
        c.getId(),
        c.getSubject(),
        c.getFromAddress(),
        c.getReceivedAt(),
        correspondenceRepository.countAttachments(c.getId()),
        c.getDirection());
  }

  private void validateLinkage(UUID customerId, UUID projectId) {
    if (customerId == null && projectId == null) {
      throw new InvalidStateException(
          "Invalid correspondence linkage",
          "At least one of customerId or projectId must be provided");
    }
  }
}
