package io.b2mash.b2b.b2bstrawman.document;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceConflictException;
import io.b2mash.b2b.b2bstrawman.exception.ResourceNotFoundException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

  private final DocumentRepository documentRepository;
  private final ProjectAccessService projectAccessService;
  private final S3PresignedUrlService s3Service;

  public DocumentService(
      DocumentRepository documentRepository,
      ProjectAccessService projectAccessService,
      S3PresignedUrlService s3Service) {
    this.documentRepository = documentRepository;
    this.projectAccessService = projectAccessService;
    this.s3Service = s3Service;
  }

  @Transactional(readOnly = true)
  public List<Document> listDocuments(UUID projectId, UUID memberId, String orgRole) {
    projectAccessService.requireViewAccess(projectId, memberId, orgRole);
    return documentRepository.findByProjectId(projectId);
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

  @Transactional
  public Document confirmUpload(UUID documentId, UUID memberId, String orgRole) {
    var document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    projectAccessService.requireViewAccess(document.getProjectId(), memberId, orgRole);
    if (document.getStatus() != Document.Status.UPLOADED) {
      document.confirmUpload();
      return documentRepository.save(document);
    }
    return document;
  }

  @Transactional
  public void cancelUpload(UUID documentId, UUID memberId, String orgRole) {
    var document =
        documentRepository
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    projectAccessService.requireViewAccess(document.getProjectId(), memberId, orgRole);
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
            .findById(documentId)
            .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    projectAccessService.requireViewAccess(document.getProjectId(), memberId, orgRole);
    if (document.getStatus() != Document.Status.UPLOADED) {
      throw new InvalidStateException(
          "Document not uploaded", "Document has not been uploaded yet");
    }
    var presigned = s3Service.generateDownloadUrl(document.getS3Key());
    return new PresignDownloadResult(presigned.url(), presigned.expiresInSeconds());
  }

  public record UploadInitResult(UUID documentId, String presignedUrl, long expiresInSeconds) {}

  public record PresignDownloadResult(String url, long expiresInSeconds) {}
}
