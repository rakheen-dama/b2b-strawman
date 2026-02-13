package io.b2mash.b2b.b2bstrawman.customerbackend.handler;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerProjectLinkedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerProjectUnlinkedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.CustomerUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentDeletedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentVisibilityChangedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.ProjectUpdatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.TimeEntryAggregatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.repository.PortalReadModelRepository;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
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
 * ScopedValue.where(RequestScopes.TENANT_ID, event.getTenantId())} so that Hibernate
 * {@code @Filter} and RLS work correctly. Portal read-model writes use {@link
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

  public PortalEventHandler(
      PortalReadModelRepository readModelRepo,
      ProjectRepository projectRepository,
      DocumentRepository documentRepository,
      CustomerProjectRepository customerProjectRepository) {
    this.readModelRepo = readModelRepo;
    this.projectRepository = projectRepository;
    this.documentRepository = documentRepository;
    this.customerProjectRepository = customerProjectRepository;
  }

  // ── Customer-project events ────────────────────────────────────────

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCustomerProjectLinked(CustomerProjectLinkedEvent event) {
    handleInTenantScope(
        event.getTenantId(),
        event.getOrgId(),
        () -> {
          try {
            var projectOpt = projectRepository.findOneById(event.getProjectId());
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
              var docOpt = documentRepository.findOneById(event.getDocumentId());
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

  // ── Private helpers ────────────────────────────────────────────────

  /**
   * Binds tenant and org ScopedValues so that Hibernate {@code @Filter}, RLS, and {@code
   * TenantAwareEntityListener} work correctly in the handler's new transaction. ORG_ID is required
   * for shared-schema (Starter tier) tenants where {@code TenantFilterTransactionManager} enables
   * the Hibernate filter and {@code TenantAwareEntityListener} sets {@code tenant_id} on new
   * entities.
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
