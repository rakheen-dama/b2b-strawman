package io.b2mash.b2b.b2bstrawman.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocumentController {

  private final DocumentService documentService;

  public DocumentController(DocumentService documentService) {
    this.documentService = documentService;
  }

  @PostMapping("/api/projects/{projectId}/documents/upload-init")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<?> initiateUpload(
      @PathVariable UUID projectId,
      @Valid @RequestBody UploadInitRequest request,
      JwtAuthenticationToken auth) {
    @SuppressWarnings("unchecked")
    Map<String, Object> orgClaim = auth.getToken().getClaim("o");
    String orgId = orgClaim != null ? (String) orgClaim.get("id") : null;
    String uploadedBy = auth.getName();

    return documentService
        .initiateUpload(
            projectId, request.fileName(), request.contentType(), request.size(), orgId, uploadedBy)
        .map(
            result ->
                ResponseEntity.status(201)
                    .body(
                        new UploadInitResponse(
                            result.documentId(), result.presignedUrl(), result.expiresInSeconds())))
        .orElseGet(() -> ResponseEntity.of(projectNotFound(projectId)).build());
  }

  @PostMapping("/api/documents/{documentId}/confirm")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<?> confirmUpload(@PathVariable UUID documentId) {
    return documentService
        .confirmUpload(documentId)
        .map(document -> ResponseEntity.ok(DocumentResponse.from(document)))
        .orElseGet(() -> ResponseEntity.of(documentNotFound(documentId)).build());
  }

  @DeleteMapping("/api/documents/{documentId}/cancel")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<?> cancelUpload(@PathVariable UUID documentId) {
    return documentService
        .cancelUpload(documentId)
        .map(
            status ->
                switch (status) {
                  case DELETED -> ResponseEntity.noContent().build();
                  case NOT_PENDING -> {
                    var problem = ProblemDetail.forStatus(409);
                    problem.setTitle("Document not pending");
                    problem.setDetail("Only pending documents can be cancelled");
                    yield ResponseEntity.of(problem).build();
                  }
                })
        .orElseGet(() -> ResponseEntity.of(documentNotFound(documentId)).build());
  }

  @GetMapping("/api/projects/{projectId}/documents")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<?> listDocuments(@PathVariable UUID projectId) {
    return documentService
        .listDocuments(projectId)
        .map(
            documents -> {
              var response = documents.stream().map(DocumentResponse::from).toList();
              return ResponseEntity.ok(response);
            })
        .orElseGet(() -> ResponseEntity.of(projectNotFound(projectId)).build());
  }

  @GetMapping("/api/documents/{documentId}/presign-download")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<?> presignDownload(@PathVariable UUID documentId) {
    return documentService
        .getPresignedDownloadUrl(documentId)
        .map(
            result -> {
              if (!result.uploaded()) {
                var problem = ProblemDetail.forStatus(400);
                problem.setTitle("Document not uploaded");
                problem.setDetail("Document has not been uploaded yet");
                return ResponseEntity.of(problem).build();
              }
              return ResponseEntity.ok(
                  new PresignDownloadResponse(result.url(), result.expiresInSeconds()));
            })
        .orElseGet(() -> ResponseEntity.of(documentNotFound(documentId)).build());
  }

  private ProblemDetail projectNotFound(UUID projectId) {
    var problem = ProblemDetail.forStatus(404);
    problem.setTitle("Project not found");
    problem.setDetail("No project found with id " + projectId);
    return problem;
  }

  private ProblemDetail documentNotFound(UUID documentId) {
    var problem = ProblemDetail.forStatus(404);
    problem.setTitle("Document not found");
    problem.setDetail("No document found with id " + documentId);
    return problem;
  }

  public record UploadInitRequest(
      @NotBlank(message = "fileName is required")
          @Size(max = 500, message = "fileName must be at most 500 characters")
          String fileName,
      @NotBlank(message = "contentType is required")
          @Size(max = 100, message = "contentType must be at most 100 characters")
          String contentType,
      @Positive(message = "size must be positive") long size) {}

  public record UploadInitResponse(UUID documentId, String presignedUrl, long expiresInSeconds) {}

  public record PresignDownloadResponse(String presignedUrl, long expiresInSeconds) {}

  public record DocumentResponse(
      UUID id,
      UUID projectId,
      String fileName,
      String contentType,
      long size,
      String status,
      String uploadedBy,
      Instant uploadedAt,
      Instant createdAt) {

    public static DocumentResponse from(Document document) {
      return new DocumentResponse(
          document.getId(),
          document.getProjectId(),
          document.getFileName(),
          document.getContentType(),
          document.getSize(),
          document.getStatus().name(),
          document.getUploadedBy(),
          document.getUploadedAt(),
          document.getCreatedAt());
    }
  }
}
