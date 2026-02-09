package io.b2mash.b2b.b2bstrawman.document;

import io.b2mash.b2b.b2bstrawman.exception.MissingOrganizationContextException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.security.ClerkJwtUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
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
  public ResponseEntity<UploadInitResponse> initiateUpload(
      @PathVariable UUID projectId,
      @Valid @RequestBody UploadInitRequest request,
      JwtAuthenticationToken auth) {
    String orgId = ClerkJwtUtils.extractOrgId(auth.getToken());
    if (orgId == null) {
      throw new MissingOrganizationContextException();
    }
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var result =
        documentService.initiateUpload(
            projectId,
            request.fileName(),
            request.contentType(),
            request.size(),
            orgId,
            memberId,
            orgRole);
    return ResponseEntity.status(201)
        .body(
            new UploadInitResponse(
                result.documentId(), result.presignedUrl(), result.expiresInSeconds()));
  }

  @PostMapping("/api/documents/{documentId}/confirm")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<DocumentResponse> confirmUpload(@PathVariable UUID documentId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var document = documentService.confirmUpload(documentId, memberId, orgRole);
    return ResponseEntity.ok(DocumentResponse.from(document));
  }

  @DeleteMapping("/api/documents/{documentId}/cancel")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<Void> cancelUpload(@PathVariable UUID documentId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    documentService.cancelUpload(documentId, memberId, orgRole);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/projects/{projectId}/documents")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<java.util.List<DocumentResponse>> listDocuments(
      @PathVariable UUID projectId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var documents = documentService.listDocuments(projectId, memberId, orgRole);
    var response = documents.stream().map(DocumentResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  @GetMapping("/api/documents/{documentId}/presign-download")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<PresignDownloadResponse> presignDownload(@PathVariable UUID documentId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var result = documentService.getPresignedDownloadUrl(documentId, memberId, orgRole);
    return ResponseEntity.ok(new PresignDownloadResponse(result.url(), result.expiresInSeconds()));
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
      UUID uploadedBy,
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
