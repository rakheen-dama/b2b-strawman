package io.b2mash.b2b.b2bstrawman.document;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleGuard;
import io.b2mash.b2b.b2bstrawman.compliance.LifecycleAction;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentDeletedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentVisibilityChangedEvent;
import io.b2mash.b2b.b2bstrawman.event.DocumentUploadedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

  private static final Duration URL_EXPIRY = Duration.ofHours(1);

  private final DocumentRepository documentRepository;
  private final ProjectAccessService projectAccessService;
  private final CustomerRepository customerRepository;
  private final StorageService storageService;
  private final AuditService auditService;
  private final ApplicationEventPublisher eventPublisher;
  private final MemberRepository memberRepository;
  private final CustomerLifecycleGuard customerLifecycleGuard;

  public DocumentService(
      DocumentRepository documentRepository,
      ProjectAccessService projectAccessService,
      CustomerRepository customerRepository,
      StorageService storageService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      MemberRepository memberRepository,
      CustomerLifecycleGuard customerLifecycleGuard) {
    this.documentRepository = documentRepository;
    this.projectAccessService = projectAccessService;
    this.customerRepository = customerRepository;
    this.storageService = storageService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.memberRepository = memberRepository;
    this.customerLifecycleGuard = customerLifecycleGuard;
  }

  /**
   * List PROJECT-scoped documents for a project. Explicitly filters scope='PROJECT' so that
   * ORG-scoped or CUSTOMER-scoped documents are not returned through project document listing.
   */
  @Transactional(readOnly = true)
  public List<Document> listDocuments(UUID projectId, UUID memberId, String orgRole) {
    projectAccessService.requireViewAccess(projectId, memberId, orgRole);
    return documentRepository.findProjectScopedByProjectId(projectId);
  }

  /** List ORG-scoped documents. Any authenticated org member can list. */
  @Transactional(readOnly = true)
  public List<Document> listOrgDocuments() {
    return documentRepository.findByScope(Document.Scope.ORG);
  }

  /** List CUSTOMER-scoped documents for a specific customer. */
  @Transactional(readOnly = true)
  public List<Document> listCustomerDocuments(UUID customerId) {
    return documentRepository.findByScopeAndCustomerId(Document.Scope.CUSTOMER, customerId);
  }

  @Transactional
  public UploadInitResult initiateUpload(
      UUID projectId,
      String fileName,
      String contentType,
      long size,
      String orgId,
      UUID memberId,
      String orgRole) {
    projectAccessService.requireViewAccess(projectId, memberId, orgRole);

    var document =
        documentRepository.save(new Document(projectId, fileName, contentType, size, memberId));

    String s3Key =
        S3PresignedUrlService.buildKey(orgId, projectId.toString(), document.getId().toString());
    var presigned = storageService.generateUploadUrl(s3Key, contentType, URL_EXPIRY);

    document.assignS3Key(s3Key);
    documentRepository.save(document);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("document.created")
            .entityType("document")
            .entityId(document.getId())
            .details(
                Map.of(
                    "scope", "PROJECT", "file_name", fileName, "project_id", projectId.toString()))
            .build());

    return new UploadInitResult(document.getId(), presigned.url(), URL_EXPIRY.toSeconds());
  }

  /** Initiate an ORG-scoped document upload. S3 key: org/{orgId}/org-docs/{docId}. */
  @Transactional
  public UploadInitResult initiateOrgUpload(
      String fileName, String contentType, long size, String orgId, UUID memberId) {
    var document =
        new Document(
            Document.Scope.ORG,
            null,
            null,
            fileName,
            contentType,
            size,
            memberId,
            Document.Visibility.INTERNAL);
    document = documentRepository.save(document);

    String s3Key = S3PresignedUrlService.buildOrgKey(orgId, document.getId().toString());
    var presigned = storageService.generateUploadUrl(s3Key, contentType, URL_EXPIRY);

    document.assignS3Key(s3Key);
    documentRepository.save(document);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("document.created")
            .entityType("document")
            .entityId(document.getId())
            .details(Map.of("scope", "ORG", "file_name", fileName))
            .build());

    return new UploadInitResult(document.getId(), presigned.url(), URL_EXPIRY.toSeconds());
  }

  /**
   * Initiate a CUSTOMER-scoped document upload. S3 key: org/{orgId}/customer/{customerId}/{docId}.
   * Validates that the customer exists.
   */
  @Transactional
  public UploadInitResult initiateCustomerUpload(
      UUID customerId,
      String fileName,
      String contentType,
      long size,
      String orgId,
      UUID memberId) {
    var customer =
        customerRepository
            .findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

    // Check lifecycle guard before creating customer document
    customerLifecycleGuard.requireActionPermitted(customer, LifecycleAction.CREATE_DOCUMENT);

    var document =
        new Document(
            Document.Scope.CUSTOMER,
            null,
            customerId,
            fileName,
            contentType,
            size,
            memberId,
            Document.Visibility.INTERNAL);
    document = documentRepository.save(document);

    String s3Key =
        S3PresignedUrlService.buildCustomerKey(
            orgId, customerId.toString(), document.getId().toString());
    var presigned = storageService.generateUploadUrl(s3Key, contentType, URL_EXPIRY);

    document.assignS3Key(s3Key);
    documentRepository.save(document);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("document.created")
            .entityType("document")
            .entityId(document.getId())
            .details(Map.of("scope", "CUSTOMER", "file_name", fileName))
            .build());

    return new UploadInitResult(document.getId(), presigned.url(), URL_EXPIRY.toSeconds());
  }

  /** Toggle document visibility between INTERNAL and SHARED. */
  @Transactional
  public Document toggleVisibility(UUID documentId, String visibility) {
    if (!Document.Visibility.INTERNAL.equals(visibility)
        && !Document.Visibility.SHARED.equals(visibility)) {
      throw new InvalidStateException(
          "Invalid visibility",
          "Visibility must be '"
              + Document.Visibility.INTERNAL
              + "' or '"
              + Document.Visibility.SHARED
              + "'");
    }
    var document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

    // Capture old visibility before mutation
    String oldVisibility = document.getVisibility();

    document.setVisibility(visibility);
    document = documentRepository.save(document);

    auditService.log(
        AuditEventBuilder.builder()
            .eventType("document.visibility_changed")
            .entityType("document")
            .entityId(document.getId())
            .details(Map.of("visibility", Map.of("from", oldVisibility, "to", visibility)))
            .build());

    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
    eventPublisher.publishEvent(
        new DocumentVisibilityChangedEvent(
            document.getId(), document.getVisibility(), oldVisibility, orgId, tenantId));

    return document;
  }

  /**
   * Confirm upload — scope-aware. For PROJECT-scoped documents, checks project access. For ORG and
   * CUSTOMER scoped documents, tenant isolation is provided by the dedicated schema (search_path).
   */
  @Transactional
  public Document confirmUpload(UUID documentId, UUID memberId, String orgRole) {
    var document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    requireDocumentAccess(document, memberId, orgRole);
    if (document.getStatus() != Document.Status.UPLOADED) {
      document.confirmUpload();
      document = documentRepository.save(document);

      var uploadDetails = new HashMap<String, Object>();
      uploadDetails.put("file_name", document.getFileName());
      if (document.isProjectScoped() && document.getProjectId() != null) {
        uploadDetails.put("project_id", document.getProjectId().toString());
      }
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("document.uploaded")
              .entityType("document")
              .entityId(document.getId())
              .details(uploadDetails)
              .build());

      // Only publish notification event for PROJECT-scoped documents (notifications are
      // project-scoped)
      String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
      String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
      if (document.isProjectScoped() && document.getProjectId() != null) {
        String actorName = resolveActorName(memberId);
        eventPublisher.publishEvent(
            new DocumentUploadedEvent(
                "document.uploaded",
                "document",
                document.getId(),
                document.getProjectId(),
                memberId,
                actorName,
                tenantId,
                orgId,
                Instant.now(),
                Map.of("file_name", document.getFileName()),
                document.getFileName()));
      }

      // Publish portal event for ALL document types (portal may display any scope)
      eventPublisher.publishEvent(
          new DocumentCreatedEvent(
              document.getId(),
              document.getProjectId(),
              document.getCustomerId(),
              document.getFileName(),
              document.getScope(),
              document.getVisibility(),
              document.getS3Key(),
              document.getSize(),
              document.getContentType(),
              orgId,
              tenantId));

      return document;
    }
    return document;
  }

  /**
   * Cancel upload — scope-aware. For PROJECT-scoped documents, checks project access. For ORG and
   * CUSTOMER scoped documents, tenant isolation is provided by the dedicated schema (search_path).
   */
  @Transactional
  public void cancelUpload(UUID documentId, UUID memberId, String orgRole) {
    var document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    requireDocumentAccess(document, memberId, orgRole);
    if (document.getStatus() != Document.Status.PENDING) {
      throw new ResourceConflictException(
          "Document not pending", "Only pending documents can be cancelled");
    }
    documentRepository.delete(document);

    var deleteDetails = new HashMap<String, Object>();
    deleteDetails.put("file_name", document.getFileName());
    if (document.isProjectScoped() && document.getProjectId() != null) {
      deleteDetails.put("project_id", document.getProjectId().toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("document.deleted")
            .entityType("document")
            .entityId(document.getId())
            .details(deleteDetails)
            .build());

    String tenantId = RequestScopes.TENANT_ID.isBound() ? RequestScopes.TENANT_ID.get() : null;
    String orgId = RequestScopes.ORG_ID.isBound() ? RequestScopes.ORG_ID.get() : null;
    eventPublisher.publishEvent(new DocumentDeletedEvent(document.getId(), orgId, tenantId));
  }

  @Transactional
  public PresignDownloadResult getPresignedDownloadUrl(
      UUID documentId, UUID memberId, String orgRole) {
    var document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    requireDocumentAccess(document, memberId, orgRole);
    if (document.getStatus() != Document.Status.UPLOADED) {
      throw new InvalidStateException(
          "Document not uploaded", "Document has not been uploaded yet");
    }
    var presigned = storageService.generateDownloadUrl(document.getS3Key(), URL_EXPIRY);

    var accessDetails = new HashMap<String, Object>();
    accessDetails.put("scope", document.getScope());
    accessDetails.put("file_name", document.getFileName());
    if (document.isProjectScoped() && document.getProjectId() != null) {
      accessDetails.put("project_id", document.getProjectId().toString());
    }
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("document.accessed")
            .entityType("document")
            .entityId(document.getId())
            .details(accessDetails)
            .build());

    // Security audit for customer-scoped documents
    if (Document.Scope.CUSTOMER.equals(document.getScope())) {
      auditService.log(
          AuditEventBuilder.builder()
              .eventType("security.document_accessed")
              .entityType("document")
              .entityId(document.getId())
              .details(
                  Map.of(
                      "document_id", document.getId().toString(),
                      "scope", "CUSTOMER",
                      "customer_id", document.getCustomerId().toString(),
                      "file_name", document.getFileName()))
              .build());
    }

    return new PresignDownloadResult(presigned.url(), URL_EXPIRY.toSeconds());
  }

  /**
   * Scope-aware access check. PROJECT-scoped documents check project membership. ORG and CUSTOMER
   * scoped documents rely on dedicated schema isolation (search_path restricts to current tenant).
   */
  private void requireDocumentAccess(Document document, UUID memberId, String orgRole) {
    if (document.isProjectScoped()) {
      Objects.requireNonNull(document.getProjectId(), "PROJECT-scoped document missing projectId");
      projectAccessService.requireViewAccess(document.getProjectId(), memberId, orgRole);
    }
    // ORG and CUSTOMER scoped: tenant isolation is sufficient — any org member can access
  }

  private String resolveActorName(UUID memberId) {
    return memberRepository.findById(memberId).map(m -> m.getName()).orElse("Unknown");
  }

  public record UploadInitResult(UUID documentId, String presignedUrl, long expiresInSeconds) {}

  public record PresignDownloadResult(String url, long expiresInSeconds) {}
}
