package io.b2mash.b2b.b2bstrawman.document;

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
  private final S3PresignedUrlService s3Service;

  public DocumentService(
      DocumentRepository documentRepository,
      ProjectRepository projectRepository,
      S3PresignedUrlService s3Service) {
    this.documentRepository = documentRepository;
    this.projectRepository = projectRepository;
    this.s3Service = s3Service;
  }

  @Transactional(readOnly = true)
  public Optional<List<Document>> listDocuments(UUID projectId) {
    if (!projectRepository.existsById(projectId)) {
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
      String uploadedBy) {
    if (!projectRepository.existsById(projectId)) {
      return Optional.empty();
    }

    var document =
        documentRepository.save(new Document(projectId, fileName, contentType, size, uploadedBy));

    var presigned =
        s3Service.generateUploadUrl(
            orgId, projectId.toString(), document.getId().toString(), contentType);

    document.assignS3Key(presigned.s3Key());
    documentRepository.save(document);

    return Optional.of(
        new UploadInitResult(document.getId(), presigned.url(), presigned.expiresInSeconds()));
  }

  @Transactional
  public Optional<Document> confirmUpload(UUID documentId) {
    return documentRepository
        .findById(documentId)
        .map(
            document -> {
              if (document.getStatus() != Document.Status.UPLOADED) {
                document.confirmUpload();
                return documentRepository.save(document);
              }
              return document;
            });
  }

  @Transactional
  public Optional<CancelResult> cancelUpload(UUID documentId) {
    return documentRepository
        .findById(documentId)
        .map(
            document -> {
              if (document.getStatus() != Document.Status.PENDING) {
                return CancelResult.NOT_PENDING;
              }
              documentRepository.delete(document);
              return CancelResult.DELETED;
            });
  }

  @Transactional(readOnly = true)
  public Optional<PresignDownloadResult> getPresignedDownloadUrl(UUID documentId) {
    return documentRepository
        .findById(documentId)
        .map(
            document -> {
              if (document.getStatus() != Document.Status.UPLOADED) {
                return PresignDownloadResult.notUploaded();
              }
              var presigned = s3Service.generateDownloadUrl(document.getS3Key());
              return PresignDownloadResult.success(presigned.url(), presigned.expiresInSeconds());
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
