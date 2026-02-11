package io.b2mash.b2b.b2bstrawman.document;

import io.b2mash.b2b.b2bstrawman.customer.CustomerRepository;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

  private final DocumentRepository documentRepository;
  private final ProjectAccessService projectAccessService;
  private final CustomerRepository customerRepository;
  private final S3PresignedUrlService s3Service;

  public DocumentService(
      DocumentRepository documentRepository,
      ProjectAccessService projectAccessService,
      CustomerRepository customerRepository,
      S3PresignedUrlService s3Service) {
    this.documentRepository = documentRepository;
    this.projectAccessService = projectAccessService;
    this.customerRepository = customerRepository;
    this.s3Service = s3Service;
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

    var presigned =
        s3Service.generateUploadUrl(
            orgId, projectId.toString(), document.getId().toString(), contentType);

    document.assignS3Key(presigned.s3Key());
    documentRepository.save(document);

    return new UploadInitResult(document.getId(), presigned.url(), presigned.expiresInSeconds());
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

    var presigned = s3Service.generateOrgUploadUrl(orgId, document.getId().toString(), contentType);

    document.assignS3Key(presigned.s3Key());
    documentRepository.save(document);

    return new UploadInitResult(document.getId(), presigned.url(), presigned.expiresInSeconds());
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
    customerRepository
        .findOneById(customerId)
        .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

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

    var presigned =
        s3Service.generateCustomerUploadUrl(
            orgId, customerId.toString(), document.getId().toString(), contentType);

    document.assignS3Key(presigned.s3Key());
    documentRepository.save(document);

    return new UploadInitResult(document.getId(), presigned.url(), presigned.expiresInSeconds());
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
            .findOneById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    document.setVisibility(visibility);
    return documentRepository.save(document);
  }

  /**
   * Confirm upload — scope-aware. For PROJECT-scoped documents, checks project access. For ORG and
   * CUSTOMER scoped documents, the document is already tenant-isolated via Hibernate @Filter.
   */
  @Transactional
  public Document confirmUpload(UUID documentId, UUID memberId, String orgRole) {
    var document =
        documentRepository
            .findOneById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    requireDocumentAccess(document, memberId, orgRole);
    if (document.getStatus() != Document.Status.UPLOADED) {
      document.confirmUpload();
      return documentRepository.save(document);
    }
    return document;
  }

  /**
   * Cancel upload — scope-aware. For PROJECT-scoped documents, checks project access. For ORG and
   * CUSTOMER scoped documents, the document is already tenant-isolated via Hibernate @Filter.
   */
  @Transactional
  public void cancelUpload(UUID documentId, UUID memberId, String orgRole) {
    var document =
        documentRepository
            .findOneById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    requireDocumentAccess(document, memberId, orgRole);
    if (document.getStatus() != Document.Status.PENDING) {
      throw new ResourceConflictException(
          "Document not pending", "Only pending documents can be cancelled");
    }
    documentRepository.delete(document);
  }

  @Transactional(readOnly = true)
  public PresignDownloadResult getPresignedDownloadUrl(
      UUID documentId, UUID memberId, String orgRole) {
    var document =
        documentRepository
            .findOneById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    requireDocumentAccess(document, memberId, orgRole);
    if (document.getStatus() != Document.Status.UPLOADED) {
      throw new InvalidStateException(
          "Document not uploaded", "Document has not been uploaded yet");
    }
    var presigned = s3Service.generateDownloadUrl(document.getS3Key());
    return new PresignDownloadResult(presigned.url(), presigned.expiresInSeconds());
  }

  /**
   * Scope-aware access check. PROJECT-scoped documents check project membership. ORG and CUSTOMER
   * scoped documents rely on tenant isolation (Hibernate @Filter already restricts to current
   * tenant).
   */
  private void requireDocumentAccess(Document document, UUID memberId, String orgRole) {
    if (document.isProjectScoped()) {
      Objects.requireNonNull(document.getProjectId(), "PROJECT-scoped document missing projectId");
      projectAccessService.requireViewAccess(document.getProjectId(), memberId, orgRole);
    }
    // ORG and CUSTOMER scoped: tenant isolation is sufficient — any org member can access
  }

  public record UploadInitResult(UUID documentId, String presignedUrl, long expiresInSeconds) {}

  public record PresignDownloadResult(String url, long expiresInSeconds) {}
}
