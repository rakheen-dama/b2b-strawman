package io.b2mash.b2b.b2bstrawman.correspondence;

import io.b2mash.b2b.b2bstrawman.correspondence.dto.CorrespondenceListResponse;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceCommand;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceResult;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
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

  public CorrespondenceService(CorrespondenceRepository correspondenceRepository) {
    this.correspondenceRepository = correspondenceRepository;
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
   * Resolve a correspondence by id for scope resolution (Phase 81, {@code attach_document}). The
   * caller reads {@code getCustomerId()} / {@code getProjectId()} to choose CUSTOMER vs PROJECT
   * upload scope. Tenant-isolated via {@code search_path}. Throws {@link ResourceNotFoundException}
   * when the id is unknown in this tenant.
   */
  @Transactional(readOnly = true)
  public Correspondence requireById(UUID id) {
    return correspondenceRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Correspondence", id));
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
