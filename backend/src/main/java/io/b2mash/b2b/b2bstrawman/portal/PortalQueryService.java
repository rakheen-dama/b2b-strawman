package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.customer.CustomerProjectRepository;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.project.Project;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Centralized read-only query service for portal endpoints. Enforces customer-scoped access: only
 * projects linked to the customer, and only SHARED-visibility documents.
 */
@Service
public class PortalQueryService {

  private static final Logger log = LoggerFactory.getLogger(PortalQueryService.class);
  private static final Duration URL_EXPIRY = Duration.ofHours(1);

  private final CustomerRepository customerRepository;
  private final CustomerProjectRepository customerProjectRepository;
  private final ProjectRepository projectRepository;
  private final DocumentRepository documentRepository;
  private final StorageService storageService;

  public PortalQueryService(
      CustomerRepository customerRepository,
      CustomerProjectRepository customerProjectRepository,
      ProjectRepository projectRepository,
      DocumentRepository documentRepository,
      StorageService storageService) {
    this.customerRepository = customerRepository;
    this.customerProjectRepository = customerProjectRepository;
    this.projectRepository = projectRepository;
    this.documentRepository = documentRepository;
    this.storageService = storageService;
  }

  /** Validates that the customer exists in the current tenant. */
  @Transactional(readOnly = true)
  public void requireCustomerExists(UUID customerId) {
    customerRepository
        .findById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
  }

  /** Lists projects linked to the given customer. */
  @Transactional(readOnly = true)
  public List<Project> listCustomerProjects(UUID customerId) {
    requireCustomerExists(customerId);
    var links = customerProjectRepository.findByCustomerId(customerId);
    return links.stream()
        .map(link -> projectRepository.findById(link.getProjectId()))
        .flatMap(java.util.Optional::stream)
        .toList();
  }

  /**
   * Lists SHARED documents for a specific project, verifying the customer is linked to it. Returns
   * project-scoped SHARED documents.
   */
  @Transactional(readOnly = true)
  public List<Document> listProjectDocuments(UUID projectId, UUID customerId) {
    requireCustomerExists(customerId);
    requireCustomerLinkedToProject(customerId, projectId);

    return documentRepository.findProjectScopedByProjectId(projectId).stream()
        .filter(doc -> Document.Visibility.SHARED.equals(doc.getVisibility()))
        .toList();
  }

  /**
   * Lists all SHARED documents visible to the customer: org-scoped SHARED docs + customer-scoped
   * SHARED docs for this customer.
   */
  @Transactional(readOnly = true)
  public List<Document> listCustomerDocuments(UUID customerId) {
    requireCustomerExists(customerId);

    // Use a set to deduplicate (by id), preserving insertion order
    Set<UUID> seenIds = new LinkedHashSet<>();
    List<Document> result = new ArrayList<>();

    // ORG-scoped SHARED documents
    for (Document doc : documentRepository.findByScope(Document.Scope.ORG)) {
      if (Document.Visibility.SHARED.equals(doc.getVisibility()) && seenIds.add(doc.getId())) {
        result.add(doc);
      }
    }

    // CUSTOMER-scoped SHARED documents for this customer
    for (Document doc :
        documentRepository.findByScopeAndCustomerId(Document.Scope.CUSTOMER, customerId)) {
      if (Document.Visibility.SHARED.equals(doc.getVisibility()) && seenIds.add(doc.getId())) {
        result.add(doc);
      }
    }

    return result;
  }

  /**
   * Gets a single document if it is SHARED and belongs to a project linked to the customer, or is
   * org/customer-scoped SHARED.
   */
  @Transactional(readOnly = true)
  public Document getDocument(UUID documentId, UUID customerId) {
    requireCustomerExists(customerId);
    var document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

    // Must be SHARED visibility
    if (!Document.Visibility.SHARED.equals(document.getVisibility())) {
      throw new ResourceNotFoundException("Document", documentId);
    }

    // Scope-based access check
    if (document.isProjectScoped()) {
      requireCustomerLinkedToProject(customerId, document.getProjectId());
    } else if (document.isCustomerScoped()) {
      // Customer-scoped doc: must belong to this customer
      if (!customerId.equals(document.getCustomerId())) {
        throw new ResourceNotFoundException("Document", documentId);
      }
    }
    // ORG-scoped SHARED documents are visible to all portal customers in the tenant

    return document;
  }

  /** Generates a presigned download URL for a portal-accessible document. */
  @Transactional(readOnly = true)
  public PortalPresignedDownloadResult getPresignedDownloadUrl(UUID documentId, UUID customerId) {
    var document = getDocument(documentId, customerId);
    if (document.getStatus() != Document.Status.UPLOADED) {
      throw new ResourceNotFoundException("Document", documentId);
    }
    var presigned = storageService.generateDownloadUrl(document.getS3Key(), URL_EXPIRY);
    return new PortalPresignedDownloadResult(presigned.url(), URL_EXPIRY.toSeconds());
  }

  /** Counts SHARED documents in a project that are visible to a portal customer. */
  @Transactional(readOnly = true)
  long countSharedProjectDocuments(UUID projectId) {
    return documentRepository.findProjectScopedByProjectId(projectId).stream()
        .filter(doc -> Document.Visibility.SHARED.equals(doc.getVisibility()))
        .count();
  }

  private void requireCustomerLinkedToProject(UUID customerId, UUID projectId) {
    if (!customerProjectRepository.existsByCustomerIdAndProjectId(customerId, projectId)) {
      throw new ResourceNotFoundException("Project", projectId);
    }
  }

  /** Result DTO for portal presigned download URLs. */
  public record PortalPresignedDownloadResult(String url, long expiresInSeconds) {}
}
