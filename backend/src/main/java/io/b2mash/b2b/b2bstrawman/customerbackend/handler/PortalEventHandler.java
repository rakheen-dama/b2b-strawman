package io.b2mash.b2b.b2bstrawman.customerbackend.handler;

import io.b2mash.b2b.b2bstrawman.comment.CommentRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerProjectLinkedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerProjectUnlinkedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentDeletedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentVisibilityChangedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.InvoiceSyncEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.PortalTaskCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.PortalTaskDeletedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.PortalTaskUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.TimeEntryAggregatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.CommentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentDeletedEvent;
import io.b2mash.b2b.b2bstrawman.event.CommentVisibilityChangedEvent;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceLineRepository;
import io.b2mash.b2b.b2bstrawman.invoice.InvoiceRepository;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to {@link io.b2mash.b2b.b2bstrawman.customerbackend.event.PortalDomainEvent} subclasses
 * and projects data into the portal read-model schema. All handler methods run AFTER_COMMIT,
 * ensuring:
 *
 * <ol>
 *   <li>Projections are only created for committed domain changes.
 *   <li>Projection failures do not affect the originating domain transaction.
 * </ol>
 *
 * <p><b>Cross-schema query pattern:</b> Each handler that queries tenant-schema data (e.g., loading
 * a Project or Document entity) wraps those queries in {@code
 * ScopedValue.where(RequestScopes.TENANT_ID, event.getTenantId())} so that the correct dedicated
 * schema is selected via {@code search_path}. Portal read-model writes use {@link
 * PortalReadModelRepository} which operates on a separate DataSource and does not require tenant
 * scope binding.
 */
@Component
public class PortalEventHandler {

  private static final Logger log = LoggerFactory.getLogger(PortalEventHandler.class);

  private final PortalReadModelRepository readModelRepo;
  private final ProjectRepository projectRepository;
  private final DocumentRepository documentRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final InvoiceRepository invoiceRepository;
  private final InvoiceLineRepository invoiceLineRepository;
  private final CommentRepository commentRepository;
  private final MemberRepository memberRepository;

  public PortalEventHandler(
      PortalReadModelRepository readModelRepo,
      ProjectRepository projectRepository,
      DocumentRepository documentRepository,
      CustomerProjectRepository customerProjectRepository,
      InvoiceRepository invoiceRepository,
      InvoiceLineRepository invoiceLineRepository,
      CommentRepository commentRepository,
      MemberRepository memberRepository) {
    this.readModelRepo = readModelRepo;
    this.projectRepository = projectRepository;
    this.documentRepository = documentRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.invoiceRepository = invoiceRepository;
    this.invoiceLineRepository = invoiceLineRepository;
    this.commentRepository = commentRepository;
    this.memberRepository = memberRepository;
  }

  // ── Customer-project events ────────────────────────────────────────

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCustomerProjectLinked(CustomerProjectLinkedEvent event) {
    handleInTenantScope(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            var projectOpt = projectRepository.findById(event.getProjectId());
            if (projectOpt.isEmpty()) {
              log.warn(
                  "Project not found for CustomerProjectLinkedEvent: projectId={}",
                  event.getProjectId());
              return;
            }
            var project = projectOpt.get();
            readModelRepo.upsertPortalProject(
                project.getId(),
                event.getCustomerId(),
                event.getOrgId(),
                project.getName(),
                "ACTIVE",
                project.getDescription(),
                project.getCreatedAt());
          } catch (Exception e) {
            log.warn(
                "Failed to project CustomerProjectLinkedEvent: customerId={}, projectId={}",
                event.getCustomerId(),
                event.getProjectId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCustomerProjectUnlinked(CustomerProjectUnlinkedEvent event) {
    handleInTenantScope(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            readModelRepo.deleteTasksByPortalProjectId(event.getProjectId(), event.getOrgId());
            readModelRepo.deletePortalProject(event.getProjectId(), event.getCustomerId());
          } catch (Exception e) {
            log.warn(
                "Failed to project CustomerProjectUnlinkedEvent: customerId={}, projectId={}",
                event.getCustomerId(),
                event.getProjectId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onProjectUpdated(ProjectUpdatedEvent event) {
    handleInTenantScope(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            var customerIds =
                readModelRepo.findCustomerIdsByProjectId(event.getProjectId(), event.getOrgId());
            var status = event.getStatus() != null ? event.getStatus() : "ACTIVE";
            for (var customerId : customerIds) {
              readModelRepo.updatePortalProjectDetails(
                  event.getProjectId(),
                  customerId,
                  event.getName(),
                  status,
                  event.getDescription());
            }
          } catch (Exception e) {
            log.warn(
                "Failed to project ProjectUpdatedEvent: projectId={}", event.getProjectId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCustomerUpdated(CustomerUpdatedEvent event) {
    handleInTenantScope(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            if (!"ARCHIVED".equals(event.getStatus())) {
              return;
            }
            var links = customerProjectRepository.findByCustomerId(event.getCustomerId());
            for (var link : links) {
              readModelRepo.deletePortalProject(link.getProjectId(), event.getCustomerId());
            }
          } catch (Exception e) {
            log.warn(
                "Failed to project CustomerUpdatedEvent: customerId={}", event.getCustomerId(), e);
          }
        });
  }

  // ── Document events ────────────────────────────────────────────────

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDocumentCreated(DocumentCreatedEvent event) {
    handleInTenantScope(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            if (!"SHARED".equals(event.getVisibility())) {
              return;
            }
            var customerIds =
                readModelRepo.findCustomerIdsByProjectId(event.getProjectId(), event.getOrgId());
            for (var customerId : customerIds) {
              readModelRepo.upsertPortalDocument(
                  event.getDocumentId(),
                  event.getOrgId(),
                  customerId,
                  event.getProjectId(),
                  event.getFileName(),
                  event.getContentType(),
                  event.getSize(),
                  event.getScope(),
                  event.getS3Key(),
                  event.getOccurredAt());
              readModelRepo.incrementDocumentCount(event.getProjectId(), customerId);
            }
          } catch (Exception e) {
            log.warn(
                "Failed to project DocumentCreatedEvent: documentId={}", event.getDocumentId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDocumentVisibilityChanged(DocumentVisibilityChangedEvent event) {
    handleInTenantScope(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            if ("SHARED".equals(event.getVisibility())) {
              // Changed TO SHARED -- project the document
              var docOpt = documentRepository.findById(event.getDocumentId());
              if (docOpt.isEmpty()) {
                log.warn(
                    "Document not found for visibility change to SHARED: documentId={}",
                    event.getDocumentId());
                return;
              }
              var doc = docOpt.get();
              var customerIds =
                  readModelRepo.findCustomerIdsByProjectId(doc.getProjectId(), event.getOrgId());
              for (var customerId : customerIds) {
                readModelRepo.upsertPortalDocument(
                    doc.getId(),
                    event.getOrgId(),
                    customerId,
                    doc.getProjectId(),
                    doc.getFileName(),
                    doc.getContentType(),
                    doc.getSize(),
                    doc.getScope(),
                    doc.getS3Key(),
                    doc.getUploadedAt());
                readModelRepo.incrementDocumentCount(doc.getProjectId(), customerId);
              }
            } else if ("SHARED".equals(event.getPreviousVisibility())) {
              // Changed FROM SHARED -- remove the document projection
              var portalDoc =
                  readModelRepo.findPortalDocumentById(event.getDocumentId(), event.getOrgId());
              if (portalDoc.isPresent()) {
                var projectId = portalDoc.get().portalProjectId();
                var customerIds =
                    readModelRepo.findCustomerIdsByProjectId(projectId, event.getOrgId());
                readModelRepo.deletePortalDocument(event.getDocumentId(), event.getOrgId());
                for (var customerId : customerIds) {
                  readModelRepo.decrementDocumentCount(projectId, customerId);
                }
              }
            }
          } catch (Exception e) {
            log.warn(
                "Failed to project DocumentVisibilityChangedEvent: documentId={}",
                event.getDocumentId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDocumentDeleted(DocumentDeletedEvent event) {
    handleInTenantScope(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            // Look up the portal document before deleting to get projectId for count decrement.
            // Must decrement for ALL linked customers, not just the one stored in this row,
            // since onDocumentCreated increments for every customer linked to the project.
            var portalDoc =
                readModelRepo.findPortalDocumentById(event.getDocumentId(), event.getOrgId());
            if (portalDoc.isPresent()) {
              var projectId = portalDoc.get().portalProjectId();
              var customerIds =
                  readModelRepo.findCustomerIdsByProjectId(projectId, event.getOrgId());
              readModelRepo.deletePortalDocument(event.getDocumentId(), event.getOrgId());
              for (var customerId : customerIds) {
                readModelRepo.decrementDocumentCount(projectId, customerId);
              }
            } else {
              // Document was not in portal (e.g., INTERNAL visibility) -- no-op
              readModelRepo.deletePortalDocument(event.getDocumentId(), event.getOrgId());
            }
          } catch (Exception e) {
            log.warn(
                "Failed to project DocumentDeletedEvent: documentId={}", event.getDocumentId(), e);
          }
        });
  }

  // ── Time entry events ──────────────────────────────────────────────

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTimeEntryAggregated(TimeEntryAggregatedEvent event) {
    handleInTenantScope(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            var customerIds =
                readModelRepo.findCustomerIdsByProjectId(event.getProjectId(), event.getOrgId());
            for (var customerId : customerIds) {
              readModelRepo.upsertPortalProjectSummary(
                  event.getProjectId(),
                  customerId,
                  event.getOrgId(),
                  event.getTotalHours(),
                  event.getBillableHours(),
                  event.getLastActivityAt());
            }
          } catch (Exception e) {
            log.warn(
                "Failed to project TimeEntryAggregatedEvent: projectId={}",
                event.getProjectId(),
                e);
          }
        });
  }

  // ── Invoice events ─────────────────────────────────────────────────

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onInvoiceSynced(InvoiceSyncEvent event) {
    handleInTenantScope(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            switch (event.getStatus()) {
              case "SENT" -> {
                // Upsert the invoice row
                readModelRepo.upsertPortalInvoice(
                    event.getInvoiceId(),
                    event.getOrgId(),
                    event.getCustomerId(),
                    event.getInvoiceNumber(),
                    event.getStatus(),
                    event.getIssueDate(),
                    event.getDueDate(),
                    event.getSubtotal(),
                    event.getTaxAmount(),
                    event.getTotal(),
                    event.getCurrency(),
                    event.getNotes(),
                    event.getPaymentUrl(),
                    event.getPaymentSessionId());

                // Remove stale line items before re-upserting (handles line item changes)
                readModelRepo.deletePortalInvoiceLinesByInvoice(event.getInvoiceId());

                // Upsert all line items from the tenant schema
                var lines =
                    invoiceLineRepository.findByInvoiceIdOrderBySortOrder(event.getInvoiceId());
                for (var line : lines) {
                  readModelRepo.upsertPortalInvoiceLine(
                      line.getId(),
                      event.getInvoiceId(),
                      line.getDescription(),
                      line.getQuantity(),
                      line.getUnitPrice(),
                      line.getAmount(),
                      line.getSortOrder());
                }
              }
              case "PAID" ->
                  readModelRepo.updatePortalInvoiceStatusAndPaidAt(
                      event.getInvoiceId(), event.getOrgId(), "PAID", Instant.now());
              case "VOID" ->
                  readModelRepo.deletePortalInvoice(event.getInvoiceId(), event.getOrgId());
              default -> log.warn("Unknown InvoiceSyncEvent status: {}", event.getStatus());
            }
          } catch (Exception e) {
            log.warn(
                "Failed to project InvoiceSyncEvent: invoiceId={}, status={}",
                event.getInvoiceId(),
                event.getStatus(),
                e);
          }
        });
  }

  // ── Task events ────────────────────────────────────────────────────

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTaskCreated(PortalTaskCreatedEvent event) {
    handleInTenantScope(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            readModelRepo.upsertPortalTask(
                event.getTaskId(),
                event.getOrgId(),
                event.getProjectId(),
                event.getName(),
                event.getStatus(),
                event.getAssigneeName(),
                event.getSortOrder());
          } catch (Exception e) {
            log.warn("Failed to project PortalTaskCreatedEvent: taskId={}", event.getTaskId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTaskUpdated(PortalTaskUpdatedEvent event) {
    handleInTenantScope(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            readModelRepo.upsertPortalTask(
                event.getTaskId(),
                event.getOrgId(),
                event.getProjectId(),
                event.getName(),
                event.getStatus(),
                event.getAssigneeName(),
                event.getSortOrder());
          } catch (Exception e) {
            log.warn("Failed to project PortalTaskUpdatedEvent: taskId={}", event.getTaskId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onTaskDeleted(PortalTaskDeletedEvent event) {
    handleInTenantScope(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            readModelRepo.deletePortalTask(event.getTaskId(), event.getOrgId());
          } catch (Exception e) {
            log.warn("Failed to project PortalTaskDeletedEvent: taskId={}", event.getTaskId(), e);
          }
        });
  }

  // ── Comment events ─────────────────────────────────────────────────

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommentCreated(CommentCreatedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            if (!"SHARED".equals(event.visibility())) {
              return;
            }
            var projectId = event.projectId();
            var customerIds = readModelRepo.findCustomerIdsByProjectId(projectId, event.orgId());
            var authorName = event.actorName() != null ? event.actorName() : "Unknown";
            var body = (String) event.details().get("body");
            for (var customerId : customerIds) {
              readModelRepo.upsertPortalComment(
                  event.entityId(), event.orgId(), projectId, authorName, body, event.occurredAt());
              readModelRepo.incrementCommentCount(projectId, customerId);
            }
          } catch (Exception e) {
            log.warn("Failed to project CommentCreatedEvent: commentId={}", event.entityId(), e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommentVisibilityChanged(CommentVisibilityChangedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            if ("SHARED".equals(event.newVisibility())) {
              // INTERNAL -> SHARED: project the comment
              var commentOpt = commentRepository.findById(event.entityId());
              if (commentOpt.isEmpty()) {
                log.warn(
                    "Comment not found for visibility change to SHARED: commentId={}",
                    event.entityId());
                return;
              }
              var comment = commentOpt.get();
              var projectId = comment.getProjectId();
              var customerIds = readModelRepo.findCustomerIdsByProjectId(projectId, event.orgId());
              // Resolve the original comment author's name, not the visibility changer's
              var authorName =
                  memberRepository
                      .findById(comment.getAuthorMemberId())
                      .map(Member::getName)
                      .orElse("Unknown");
              for (var customerId : customerIds) {
                readModelRepo.upsertPortalComment(
                    comment.getId(),
                    event.orgId(),
                    projectId,
                    authorName,
                    comment.getBody(),
                    comment.getCreatedAt());
                readModelRepo.incrementCommentCount(projectId, customerId);
              }
            } else if ("SHARED".equals(event.oldVisibility())) {
              // SHARED -> INTERNAL: remove the comment projection
              var projectId = event.projectId();
              var customerIds = readModelRepo.findCustomerIdsByProjectId(projectId, event.orgId());
              readModelRepo.deletePortalComment(event.entityId(), event.orgId());
              for (var customerId : customerIds) {
                readModelRepo.decrementCommentCount(projectId, customerId);
              }
            }
          } catch (Exception e) {
            log.warn(
                "Failed to project CommentVisibilityChangedEvent: commentId={}",
                event.entityId(),
                e);
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommentDeleted(CommentDeletedEvent event) {
    handleInTenantScope(
        event.tenantId(),
        event.orgId(),
        () -> {
          try {
            var projectId = event.projectId();
            // Only decrement count if the comment was actually in the portal read model
            // (INTERNAL comments are never projected, so deleting them should not affect counts)
            boolean existed = readModelRepo.portalCommentExists(event.entityId(), event.orgId());
            readModelRepo.deletePortalComment(event.entityId(), event.orgId());
            if (existed) {
              var customerIds = readModelRepo.findCustomerIdsByProjectId(projectId, event.orgId());
              for (var customerId : customerIds) {
                readModelRepo.decrementCommentCount(projectId, customerId);
              }
            }
          } catch (Exception e) {
            log.warn("Failed to project CommentDeletedEvent: commentId={}", event.entityId(), e);
          }
        });
  }

  // ── Private helpers ────────────────────────────────────────────────

  /**
   * Binds tenant and org ScopedValues so that the correct dedicated schema is selected via {@code
   * search_path} in the handler's new transaction.
   */
  private void handleInTenantScope(String tenantId, String orgId, Runnable action) {
    if (tenantId != null) {
      var carrier = ScopedValue.where(RequestScopes.TENANT_ID, tenantId);
      if (orgId != null) {
        carrier = carrier.where(RequestScopes.ORG_ID, orgId);
      }
      carrier.run(action);
    } else {
      log.warn("Event received with null tenantId — running without tenant scope binding");
      action.run();
    }
  }
}
