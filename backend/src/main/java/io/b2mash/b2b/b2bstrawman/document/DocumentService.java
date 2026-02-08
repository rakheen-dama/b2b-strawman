package io.b2mash.b2b.b2bstrawman.document;

import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.project.ProjectRepository;
import io.b2mash.b2b.b2bstrawman.s3.S3PresignedUrlService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentService {

  private final DocumentRepository documentRepository;
  private final ProjectRepository projectRepository;
  private final ProjectAccessService projectAccessService;
  private final S3PresignedUrlService s3Service;

  public DocumentService(
      DocumentRepository documentRepository,
      ProjectRepository projectRepository,
      ProjectAccessService projectAccessService,
      S3PresignedUrlService s3Service) {
    this.documentRepository = documentRepository;
    this.projectRepository = projectRepository;
    this.projectAccessService = projectAccessService;
    this.s3Service = s3Service;
  }

  @Transactional(readOnly = true)
  public Optional<List<Document>> listDocuments(UUID projectId, UUID memberId, String orgRole) {
    if (!projectRepository.existsById(projectId)) {
      return Optional.empty();
    }
    var access = projectAccessService.checkAccess(projectId, memberId, orgRole);
    if (!access.canView()) {
      return Optional.empty();
    }
    return Optional.of(documentRepository.findByProjectId(projectId));
  }

  @Transactional
  public Optional<UploadInitResult> initiateUpload(
      UUID projectId,
      String fileName,
      String contentType,
      long size,
      String orgId,
      UUID memberId,
      String orgRole) {
    if (!projectRepository.existsById(projectId)) {
      return Optional.empty();
    }
    var access = projectAccessService.checkAccess(projectId, memberId, orgRole);
    if (!access.canView()) {
      return Optional.empty();
    }

    var document =
        documentRepository.save(new Document(projectId, fileName, contentType, size, memberId));

    var presigned =
        s3Service.generateUploadUrl(
            orgId, projectId.toString(), document.getId().toString(), contentType);

    document.assignS3Key(presigned.s3Key());
    documentRepository.save(document);

    return Optional.of(
        new UploadInitResult(document.getId(), presigned.url(), presigned.expiresInSeconds()));
  }

  @Transactional
  public Optional<Document> confirmUpload(UUID documentId, UUID memberId, String orgRole) {
    return documentRepository
        .findById(documentId)
        .flatMap(
            document -> {
              var access =
                  projectAccessService.checkAccess(document.getProjectId(), memberId, orgRole);
              if (!access.canView()) {
                return Optional.empty();
              }
              if (document.getStatus() != Document.Status.UPLOADED) {
                document.confirmUpload();
                return Optional.of(documentRepository.save(document));
              }
              return Optional.of(document);
            });
  }

  @Transactional
  public Optional<CancelResult> cancelUpload(UUID documentId, UUID memberId, String orgRole) {
    return documentRepository
        .findById(documentId)
        .flatMap(
            document -> {
              var access =
                  projectAccessService.checkAccess(document.getProjectId(), memberId, orgRole);
              if (!access.canView()) {
                return Optional.empty();
              }
              if (document.getStatus() != Document.Status.PENDING) {
                return Optional.of(CancelResult.NOT_PENDING);
              }
              documentRepository.delete(document);
              return Optional.of(CancelResult.DELETED);
            });
  }

  @Transactional(readOnly = true)
  public Optional<PresignDownloadResult> getPresignedDownloadUrl(
      UUID documentId, UUID memberId, String orgRole) {
    return documentRepository
        .findById(documentId)
        .flatMap(
            document -> {
              var access =
                  projectAccessService.checkAccess(document.getProjectId(), memberId, orgRole);
              if (!access.canView()) {
                return Optional.empty();
              }
              if (document.getStatus() != Document.Status.UPLOADED) {
                return Optional.of(PresignDownloadResult.notUploaded());
              }
              var presigned = s3Service.generateDownloadUrl(document.getS3Key());
              return Optional.of(
                  PresignDownloadResult.success(presigned.url(), presigned.expiresInSeconds()));
            });
  }

  public enum CancelResult {
    DELETED,
    NOT_PENDING
  }

  public record UploadInitResult(UUID documentId, String presignedUrl, long expiresInSeconds) {}

  public record PresignDownloadResult(boolean uploaded, String url, long expiresInSeconds) {

    static PresignDownloadResult success(String url, long expiresInSeconds) {
      return new PresignDownloadResult(true, url, expiresInSeconds);
    }

    static PresignDownloadResult notUploaded() {
      return new PresignDownloadResult(false, null, 0);
    }
  }
}
