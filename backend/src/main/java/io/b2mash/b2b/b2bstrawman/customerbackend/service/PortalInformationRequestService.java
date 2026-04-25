package io.b2mash.b2b.b2bstrawman.customerbackend.service;

import io.b2mash.b2b.b2bstrawman.audit.AuditEventBuilder;
import io.b2mash.b2b.b2bstrawman.audit.AuditService;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalRequestItemView;
import io.b2mash.b2b.b2bstrawman.customerbackend.model.PortalRequestView;
import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.document.DocumentRepository;
import io.b2mash.b2b.b2bstrawman.event.RequestItemSubmittedEvent;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequestRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.ItemStatus;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestItemRepository;
import io.b2mash.b2b.b2bstrawman.informationrequest.RequestStatus;
import io.b2mash.b2b.b2bstrawman.integration.storage.StorageService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portal service for information request operations. Enables portal contacts to list their
 * requests, initiate file uploads, and submit responses.
 */
@Service
public class PortalInformationRequestService {

  private static final Logger log = LoggerFactory.getLogger(PortalInformationRequestService.class);
  private static final Duration UPLOAD_URL_EXPIRY = Duration.ofHours(1);
  private static final Set<ItemStatus> SUBMITTABLE_STATUSES =
      Set.of(ItemStatus.PENDING, ItemStatus.REJECTED);

  private final PortalReadModelService portalReadModelService;
  private final InformationRequestRepository requestRepository;
  private final RequestItemRepository itemRepository;
  private final DocumentRepository documentRepository;
  private final StorageService storageService;
  private final ApplicationEventPublisher eventPublisher;
  private final PortalContactRepository portalContactRepository;
  private final AuditService auditService;

  public PortalInformationRequestService(
      PortalReadModelService portalReadModelService,
      InformationRequestRepository requestRepository,
      RequestItemRepository itemRepository,
      DocumentRepository documentRepository,
      StorageService storageService,
      ApplicationEventPublisher eventPublisher,
      PortalContactRepository portalContactRepository,
      AuditService auditService) {
    this.portalReadModelService = portalReadModelService;
    this.requestRepository = requestRepository;
    this.itemRepository = itemRepository;
    this.documentRepository = documentRepository;
    this.storageService = storageService;
    this.eventPublisher = eventPublisher;
    this.portalContactRepository = portalContactRepository;
    this.auditService = auditService;
  }

  /** Lists all information requests for the given portal contact. */
  public List<PortalRequestView> listRequests(UUID portalContactId) {
    return portalReadModelService.findRequestsByPortalContactId(portalContactId);
  }

  /** Returns request detail with items, verifying portal contact ownership. */
  public RequestDetail getRequest(UUID requestId, UUID portalContactId) {
    var request = portalReadModelService.findRequestById(requestId, portalContactId);
    var items = portalReadModelService.findRequestItemsByRequestId(requestId, portalContactId);
    return new RequestDetail(request, items);
  }

  /** Initiates a file upload: creates a Document entity and returns a presigned S3 URL. */
  @Transactional
  public UploadInitResult initiateUpload(
      UUID requestId,
      UUID itemId,
      String fileName,
      String contentType,
      long size,
      UUID portalContactId) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));
    verifyOwnership(request.getPortalContactId(), portalContactId, requestId);

    var item =
        itemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("RequestItem", itemId));
    verifyItemBelongsToRequest(item.getRequestId(), requestId, itemId);

    if (!SUBMITTABLE_STATUSES.contains(item.getStatus())) {
      throw new InvalidStateException(
          "Invalid item state", "Cannot upload for item in status " + item.getStatus());
    }

    String orgId = RequestScopes.requireOrgId();

    // Determine scope and create document
    String scope;
    UUID projectId = request.getProjectId();
    UUID customerId = request.getCustomerId();

    if (projectId != null) {
      scope = Document.Scope.PROJECT;
    } else {
      scope = Document.Scope.CUSTOMER;
    }

    // uploadedBy must reference a member (FK constraint on members table), so we attribute the
    // document to the staff member who created the request rather than the portal contact.
    // This is intentional — portal contacts are not members.
    var document =
        new Document(
            scope,
            projectId,
            customerId,
            fileName,
            contentType,
            size,
            request.getCreatedBy(),
            Document.Visibility.SHARED);
    document = documentRepository.save(document);

    // Build S3 key based on scope
    String s3Key;
    if (Document.Scope.PROJECT.equals(scope)) {
      s3Key =
          S3PresignedUrlService.buildKey(orgId, projectId.toString(), document.getId().toString());
    } else {
      s3Key =
          S3PresignedUrlService.buildCustomerKey(
              orgId, customerId.toString(), document.getId().toString());
    }

    document.assignS3Key(s3Key);
    documentRepository.save(document);

    var presigned = storageService.generateUploadUrl(s3Key, contentType, UPLOAD_URL_EXPIRY);

    // Audit: portal.document.upload_initiated (PORTAL_CONTACT actor)
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("portal.document.upload_initiated")
            .entityType("document")
            .entityId(document.getId())
            .actorId(portalContactId)
            .actorType("PORTAL_CONTACT")
            .source("PORTAL")
            .details(
                Map.of(
                    "project_id",
                    projectId != null ? projectId.toString() : "",
                    "request_id",
                    requestId.toString(),
                    "item_id",
                    itemId.toString(),
                    "file_name",
                    fileName,
                    "content_type",
                    contentType,
                    "size_bytes",
                    String.valueOf(size)))
            .build());

    return new UploadInitResult(document.getId(), presigned.url(), presigned.expiresAt());
  }

  /**
   * Submits a response for a request item. Dispatches to file or text submission based on which
   * field is provided. At least one of documentId or textResponse must be non-null.
   */
  @Transactional
  public void submitResponse(
      UUID requestId, UUID itemId, UUID documentId, String textResponse, UUID portalContactId) {
    if (documentId != null) {
      submitItem(requestId, itemId, documentId, portalContactId);
    } else if (textResponse != null) {
      submitTextResponse(requestId, itemId, textResponse, portalContactId);
    } else {
      throw new InvalidStateException(
          "Invalid submission", "Either documentId or textResponse must be provided");
    }
  }

  /** Submits a file response for a request item. */
  @Transactional
  public void submitItem(UUID requestId, UUID itemId, UUID documentId, UUID portalContactId) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));
    verifyOwnership(request.getPortalContactId(), portalContactId, requestId);

    var item =
        itemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("RequestItem", itemId));
    verifyItemBelongsToRequest(item.getRequestId(), requestId, itemId);
    verifyItemSubmittable(item);

    // Verify document exists and belongs to this request's scope
    var document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    verifyDocumentScope(document, request);

    item.submit(documentId);
    itemRepository.save(item);

    autoTransitionToInProgress(request);
    publishItemSubmittedEvent(request, item, portalContactId);
    emitItemSubmittedAudit(request, item, portalContactId, documentId);
  }

  /** Submits a text response for a request item. */
  @Transactional
  public void submitTextResponse(UUID requestId, UUID itemId, String text, UUID portalContactId) {
    var request =
        requestRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("InformationRequest", requestId));
    verifyOwnership(request.getPortalContactId(), portalContactId, requestId);

    var item =
        itemRepository
            .findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("RequestItem", itemId));
    verifyItemBelongsToRequest(item.getRequestId(), requestId, itemId);
    verifyItemSubmittable(item);

    item.submitText(text);
    itemRepository.save(item);

    autoTransitionToInProgress(request);
    publishItemSubmittedEvent(request, item, portalContactId);
    emitItemSubmittedAudit(request, item, portalContactId, null);
  }

  // ── Private helpers ──────────────────────────────────────────────────

  private void verifyOwnership(UUID requestContactId, UUID portalContactId, UUID requestId) {
    if (!requestContactId.equals(portalContactId)) {
      throw new ResourceNotFoundException("InformationRequest", requestId);
    }
  }

  private void verifyItemBelongsToRequest(UUID itemRequestId, UUID requestId, UUID itemId) {
    if (!itemRequestId.equals(requestId)) {
      throw new ResourceNotFoundException("RequestItem", itemId);
    }
  }

  private void verifyDocumentScope(
      Document document, io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequest request) {
    boolean scopeMatch;
    if (request.getProjectId() != null) {
      scopeMatch =
          Document.Scope.PROJECT.equals(document.getScope())
              && request.getProjectId().equals(document.getProjectId());
    } else {
      scopeMatch =
          Document.Scope.CUSTOMER.equals(document.getScope())
              && request.getCustomerId().equals(document.getCustomerId());
    }
    if (!scopeMatch) {
      throw new InvalidStateException(
          "Document scope mismatch", "Document does not belong to this request's scope");
    }
  }

  private void verifyItemSubmittable(
      io.b2mash.b2b.b2bstrawman.informationrequest.RequestItem item) {
    if (!SUBMITTABLE_STATUSES.contains(item.getStatus())) {
      throw new InvalidStateException(
          "Invalid item state", "Cannot submit item in status " + item.getStatus());
    }
  }

  private void autoTransitionToInProgress(
      io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequest request) {
    if (request.getStatus() == RequestStatus.SENT) {
      request.markInProgress();
      requestRepository.save(request);
    }
  }

  /**
   * Emits a {@code portal.request_item.submitted} audit event with {@code actorType=PORTAL_CONTACT}
   * after a request item is submitted (file or text). The {@code details.project_id} field is
   * load-bearing — {@link io.b2mash.b2b.b2bstrawman.audit.AuditEventRepository#findByProjectId}
   * filters on it for the matter Activity feed.
   */
  private void emitItemSubmittedAudit(
      io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequest request,
      io.b2mash.b2b.b2bstrawman.informationrequest.RequestItem item,
      UUID portalContactId,
      UUID documentId) {
    auditService.log(
        AuditEventBuilder.builder()
            .eventType("portal.request_item.submitted")
            .entityType("request_item")
            .entityId(item.getId())
            .actorId(portalContactId)
            .actorType("PORTAL_CONTACT")
            .source("PORTAL")
            .details(
                Map.of(
                    "project_id",
                    request.getProjectId() != null ? request.getProjectId().toString() : "",
                    "request_id",
                    request.getId().toString(),
                    "request_number",
                    request.getRequestNumber() != null ? request.getRequestNumber() : "",
                    "item_name",
                    item.getName() != null ? item.getName() : "",
                    "response_type",
                    documentId != null ? "FILE" : "TEXT",
                    "document_id",
                    documentId != null ? documentId.toString() : ""))
            .build());
  }

  private void publishItemSubmittedEvent(
      io.b2mash.b2b.b2bstrawman.informationrequest.InformationRequest request,
      io.b2mash.b2b.b2bstrawman.informationrequest.RequestItem item,
      UUID portalContactId) {
    String actorName = "Portal User";
    var contact = portalContactRepository.findById(portalContactId).orElse(null);
    if (contact != null && contact.getDisplayName() != null) {
      actorName = contact.getDisplayName();
    }

    eventPublisher.publishEvent(
        new RequestItemSubmittedEvent(
            "information_request.item_submitted",
            "request_item",
            item.getId(),
            request.getProjectId(),
            null,
            actorName,
            RequestScopes.getTenantIdOrNull(),
            RequestScopes.getOrgIdOrNull(),
            Instant.now(),
            Map.of(
                "request_id", request.getId().toString(),
                "item_id", item.getId().toString(),
                "request_number", request.getRequestNumber()),
            request.getId(),
            item.getId(),
            request.getCustomerId(),
            portalContactId));
  }

  // ── Result records ───────────────────────────────────────────────────

  public record RequestDetail(PortalRequestView request, List<PortalRequestItemView> items) {}

  public record UploadInitResult(UUID documentId, String uploadUrl, Instant expiresAt) {}
}
