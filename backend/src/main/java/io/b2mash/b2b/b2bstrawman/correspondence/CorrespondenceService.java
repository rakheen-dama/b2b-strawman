package io.b2mash.b2b.b2bstrawman.correspondence;

import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceCommand;
import io.b2mash.b2b.b2bstrawman.correspondence.dto.FileCorrespondenceResult;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
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
  private final DocumentRepository documentRepository;

  public CorrespondenceService(
      CorrespondenceRepository correspondenceRepository, DocumentRepository documentRepository) {
    this.correspondenceRepository = correspondenceRepository;
    this.documentRepository = documentRepository;
  }

  /**
   * File an inbound correspondence. Idempotent on {@code messageId}: a re-file returns the existing
   * id without persisting a second row or mutating the existing record.
   */
  @Transactional
  public FileCorrespondenceResult fileInbound(FileCorrespondenceCommand cmd, ActorContext actor) {
    validateLinkage(cmd.customerId(), cmd.matterId());

    var existing = correspondenceRepository.findByMessageId(cmd.messageId());
    if (existing.isPresent()) {
      return FileCorrespondenceResult.idempotent(existing.get().getId());
    }

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
      var saved = correspondenceRepository.save(correspondence);
      return FileCorrespondenceResult.created(saved.getId());
    } catch (DataIntegrityViolationException race) {
      // Race backstop: a concurrent insert won the unique(message_id). Re-read the winner.
      return correspondenceRepository
          .findByMessageId(cmd.messageId())
          .map(winner -> FileCorrespondenceResult.idempotent(winner.getId()))
          .orElseThrow(() -> race);
    }
  }

  @Transactional(readOnly = true)
  public Page<Correspondence> listByProject(UUID projectId, Pageable pageable) {
    return correspondenceRepository.findByProjectId(projectId, pageable);
  }

  @Transactional(readOnly = true)
  public Page<Correspondence> listByCustomer(UUID customerId, Pageable pageable) {
    return correspondenceRepository.findByCustomerId(customerId, pageable);
  }

  /** Number of {@code Document}s attached to a correspondence (via {@code correspondence_id}). */
  @Transactional(readOnly = true)
  public long attachmentCount(UUID correspondenceId) {
    return documentRepository.countByCorrespondenceId(correspondenceId);
  }

  private void validateLinkage(UUID customerId, UUID projectId) {
    if (customerId == null && projectId == null) {
      throw new InvalidStateException(
          "Invalid correspondence linkage",
          "At least one of customerId or projectId must be provided");
    }
  }
}
