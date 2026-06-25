package io.b2mash.b2b.b2bstrawman.document;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.compliance.CustomerLifecycleGuard;
import io.b2mash.b2b.b2bstrawman.compliance.LifecycleAction;
import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentCreatedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentDeletedEvent;
import io.b2mash.b2b.b2bstrawman.customerbackend.event.DocumentVisibilityChangedEvent;
import io.b2mash.b2b.b2bstrawman.document.DocumentController.DocumentResponse;
import io.b2mash.b2b.b2bstrawman.event.DocumentUploadedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.member.MemberNameResolver;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.project.ProjectLifecycleGuard;
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
  private final MemberNameResolver memberNameResolver;
  private final CustomerLifecycleGuard customerLifecycleGuard;
  private final ProjectLifecycleGuard projectLifecycleGuard;

  public DocumentService(
      DocumentRepository documentRepository,
      ProjectAccessService projectAccessService,
      CustomerRepository customerRepository,
      StorageService storageService,
      AuditService auditService,
      ApplicationEventPublisher eventPublisher,
      MemberNameResolver memberNameResolver,
      CustomerLifecycleGuard customerLifecycleGuard,
      ProjectLifecycleGuard projectLifecycleGuard) {
    this.documentRepository = documentRepository;
    this.projectAccessService = projectAccessService;
    this.customerRepository = customerRepository;
    this.storageService = storageService;
    this.auditService = auditService;
    this.eventPublisher = eventPublisher;
    this.memberNameResolver = memberNameResolver;
    this.customerLifecycleGuard = customerLifecycleGuard;
    this.projectLifecycleGuard = projectLifecycleGuard;
  }

  /**
   * List PROJECT-scoped documents for a project. Explicitly filters scope='PROJECT' so that
   * ORG-scoped or CUSTOMER-scoped documents are not returned through project document listing.
   */
  @Transactional(readOnly = true)
  public List<Document> listDocuments(UUID projectId, ActorContext actor) {
    projectAccessService.requireViewAccess(projectId, actor);
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

  /**
   * Lists documents for {@code GET /api/documents} by scope as {@link DocumentResponse} DTOs,
   * resolving uploader names. {@code scope} must be {@code "ORG"} or {@code "CUSTOMER"}; {@code
   * customerId} is required for the CUSTOMER scope.
   *
   * <p>Moved verbatim from {@code DocumentController.listDocumentsByScope} for TD-009
   * thin-controller cleanup — behavior-preserving.
   */
  @Transactional(readOnly = true)
  public List<DocumentResponse> listDocumentsByScope(String scope, UUID customerId) {
    var documents =
        switch (scope.toUpperCase()) {
          case "ORG" -> listOrgDocuments();
          case "CUSTOMER" -> {
            if (customerId == null) {
              throw new InvalidStateException(
                  "Missing customerId", "customerId is required when scope is CUSTOMER");
            }
            yield listCustomerDocuments(customerId);
          }
          default ->
              throw new InvalidStateException("Invalid scope", "scope must be 'ORG' or 'CUSTOMER'");
        };
    return toResponses(documents);
  }

  /**
   * Lists PROJECT-scoped documents for a project as {@link DocumentResponse} DTOs, resolving
   * uploader names. Thin-controller delegation wrapper around {@link #listDocuments}.
   */
  @Transactional(readOnly = true)
  public List<DocumentResponse> listDocumentResponses(UUID projectId, ActorContext actor) {
    return toResponses(listDocuments(projectId, actor));
  }

  /** Maps documents to {@link DocumentResponse} DTOs with resolved uploader names. */
  private List<DocumentResponse> toResponses(List<Document> documents) {
    var memberNames = resolveUploaderNames(documents);
    return documents.stream().map(d -> DocumentResponse.from(d, memberNames)).toList();
  }

  /**
   * Initiate a PROJECT-scoped upload, resolving the org ID from the request scope. Thin-controller
   * entry point — delegates to {@link #initiateUpload(UUID, String, String, long, String,
   * ActorContext)}. {@code RequestScopes.requireOrgId()} throws {@link
   * io.b2mash.b2b.b2bstrawman.exception.MissingOrganizationContextException} when org context is
   * absent, matching the previous controller-level guard.
   */
  @Transactional
  public UploadInitResult initiateUpload(
      UUID projectId, String fileName, String contentType, long size, ActorContext actor) {
    return initiateUpload(
        projectId, fileName, contentType, size, RequestScopes.requireOrgId(), actor);
  }

  @Transactional
  public UploadInitResult initiateUpload(
      UUID projectId,
      String fileName,
      String contentType,
      long size,
      String orgId,
      ActorContext actor) {
    projectAccessService.requireViewAccess(projectId, actor);
    projectLifecycleGuard.requireNotReadOnly(projectId);

    var document =
        documentRepository.save(
            new Document(projectId, fileName, contentType, size, actor.memberId()));

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

  /**
   * Initiate an ORG-scoped upload, resolving org ID and member ID from the request scope.
   * Thin-controller entry point — delegates to {@link #initiateOrgUpload(String, String, long,
   * String, UUID)}.
   */
  @Transactional
  public UploadInitResult initiateOrgUpload(String fileName, String contentType, long size) {
    return initiateOrgUpload(
        fileName, contentType, size, RequestScopes.requireOrgId(), RequestScopes.requireMemberId());
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
   * Initiate a CUSTOMER-scoped upload, resolving org ID and member ID from the request scope.
   * Thin-controller entry point — delegates to {@link #initiateCustomerUpload(UUID, String, String,
   * long, String, UUID)}.
   */
  @Transactional
  public UploadInitResult initiateCustomerUpload(
      UUID customerId, String fileName, String contentType, long size) {
    return initiateCustomerUpload(
        customerId,
        fileName,
        contentType,
        size,
        RequestScopes.requireOrgId(),
        RequestScopes.requireMemberId());
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

  /**
   * Toggle document visibility between INTERNAL and SHARED — the firm's manual "share with portal"
   * action. {@link Document.Visibility#PORTAL} is reserved for system-auto-shared artefacts (see
   * {@link #markSystemAutoShared}) and is rejected here.
   */
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
    return applyVisibilityChange(documentId, visibility);
  }

  /**
   * Toggles visibility and returns the updated document as a {@link DocumentResponse} with resolved
   * uploader name. Thin-controller delegation wrapper around {@link #toggleVisibility}.
   */
  @Transactional
  public DocumentResponse toggleVisibilityResponse(UUID documentId, String visibility) {
    return toResponse(toggleVisibility(documentId, visibility));
  }

  /** Maps a single document to a {@link DocumentResponse} DTO with its resolved uploader name. */
  private DocumentResponse toResponse(Document document) {
    return toResponses(List.of(document)).getFirst();
  }

  /**
   * Marks a document as system-auto-shared (visibility = {@link Document.Visibility#PORTAL}).
   * Reserved for generated artefacts (closure-pack letters, statements of account) that the firm
   * implicitly shares as a side-effect of triggering a workflow. Distinct from {@link
   * #toggleVisibility} so audit + analytics can separate manual shares from system shares.
   */
  @Transactional
  public Document markSystemAutoShared(UUID documentId) {
    return applyVisibilityChange(documentId, Document.Visibility.PORTAL);
  }

  private Document applyVisibilityChange(UUID documentId, String visibility) {
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

    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
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
  public Document confirmUpload(UUID documentId, ActorContext actor) {
    var document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    requireDocumentAccess(document, actor);
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
      String tenantId = RequestScopes.getTenantIdOrNull();
      String orgId = RequestScopes.getOrgIdOrNull();
      if (document.isProjectScoped() && document.getProjectId() != null) {
        String actorName = resolveActorName(actor.memberId());
        eventPublisher.publishEvent(
            new DocumentUploadedEvent(
                "document.uploaded",
                "document",
                document.getId(),
                document.getProjectId(),
                actor.memberId(),
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
   * Confirms an upload and returns the document as a {@link DocumentResponse} with resolved
   * uploader name. Thin-controller delegation wrapper around {@link #confirmUpload}.
   */
  @Transactional
  public DocumentResponse confirmUploadResponse(UUID documentId, ActorContext actor) {
    return toResponse(confirmUpload(documentId, actor));
  }

  /**
   * Confirms an upload and stamps the inbound-correspondence linkage in the SAME transaction (Phase
   * 81, {@code attach_document} MCP write tool). {@link #confirmUpload} returns a managed entity
   * while this transaction is open, so the subsequent {@code setCorrespondenceId} / {@code
   * setSource} mutations are dirty-checked and persisted by the {@code save} flush. Calling the
   * stamp setters on the detached entity returned from {@link #confirmUpload} <em>outside</em> a
   * transaction would silently no-op — hence this atomic service method. Idempotent: a re-confirm
   * of an already-UPLOADED document re-applies the same stamp.
   *
   * <p>Reports whether this call performed a real state change ({@link
   * StampCorrespondenceResult#stateChanged()} is {@code true} when the document newly transitioned
   * to {@code UPLOADED} and/or the correspondence stamp was newly applied) so callers can suppress
   * duplicate state-change side effects (e.g. MCP audit events) on idempotent retries.
   */
  @Transactional
  public StampCorrespondenceResult confirmAndStampCorrespondence(
      UUID documentId, UUID correspondenceId, ActorContext actor) {
    var existing =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    boolean wasUploaded = existing.getStatus() == Document.Status.UPLOADED;
    boolean stampAlreadyApplied =
        wasUploaded
            && correspondenceId.equals(existing.getCorrespondenceId())
            && Document.Source.EMAIL_INGEST.equals(existing.getSource());

    Document document = confirmUpload(documentId, actor);
    document.setCorrespondenceId(correspondenceId);
    document.setSource(Document.Source.EMAIL_INGEST);
    Document saved = documentRepository.save(document);

    boolean stateChanged = !wasUploaded || !stampAlreadyApplied;
    return new StampCorrespondenceResult(saved, stateChanged);
  }

  /**
   * Cancel upload — scope-aware. For PROJECT-scoped documents, checks project access. For ORG and
   * CUSTOMER scoped documents, tenant isolation is provided by the dedicated schema (search_path).
   */
  @Transactional
  public void cancelUpload(UUID documentId, ActorContext actor) {
    var document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    requireDocumentAccess(document, actor);
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

    String tenantId = RequestScopes.getTenantIdOrNull();
    String orgId = RequestScopes.getOrgIdOrNull();
    eventPublisher.publishEvent(new DocumentDeletedEvent(document.getId(), orgId, tenantId));
  }

  @Transactional
  public PresignDownloadResult getPresignedDownloadUrl(UUID documentId, ActorContext actor) {
    var document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    requireDocumentAccess(document, actor);
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
  private void requireDocumentAccess(Document document, ActorContext actor) {
    if (document.isProjectScoped()) {
      Objects.requireNonNull(document.getProjectId(), "PROJECT-scoped document missing projectId");
      projectAccessService.requireViewAccess(document.getProjectId(), actor);
    }
    // ORG and CUSTOMER scoped: tenant isolation is sufficient — any org member can access
  }

  /** Resolve uploader names for a list of documents. */
  public Map<UUID, String> resolveUploaderNames(List<Document> documents) {
    var ids =
        documents.stream()
            .map(Document::getUploadedBy)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    return memberNameResolver.resolveNames(ids);
  }

  private String resolveActorName(UUID memberId) {
    return memberNameResolver.resolveName(memberId);
  }

  /**
   * Retrieve a document entity and its raw bytes from storage. Used by tools that need to process
   * document content (e.g., text extraction). Tenant isolation is provided by the schema
   * search_path; project-scoped documents additionally require project membership.
   */
  @Transactional(readOnly = true)
  public DocumentWithBytes getDocumentBytes(UUID documentId, ActorContext actor) {
    var document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    requireDocumentAccess(document, actor);
    byte[] bytes = storageService.download(document.getS3Key());
    return new DocumentWithBytes(document, bytes);
  }

  public record DocumentWithBytes(Document document, byte[] bytes) {}

  public record UploadInitResult(UUID documentId, String presignedUrl, long expiresInSeconds) {}

  public record PresignDownloadResult(String url, long expiresInSeconds) {}

  /**
   * Result of {@link #confirmAndStampCorrespondence}. {@code stateChanged} is {@code true} when the
   * call performed a real state transition (newly confirmed and/or newly stamped) and {@code false}
   * on an idempotent no-op retry of an already-confirmed, already-stamped document.
   */
  public record StampCorrespondenceResult(Document document, boolean stateChanged) {}
}
