package io.b2mash.b2b.b2bstrawman.document;

import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DocumentController {

  private final DocumentService documentService;

  public DocumentController(DocumentService documentService) {
    this.documentService = documentService;
  }

  // --- PROJECT-scoped upload-init (existing) ---

  @PostMapping("/api/projects/{projectId}/documents/upload-init")
  public ResponseEntity<UploadInitResponse> initiateUpload(
      @PathVariable UUID projectId,
      @Valid @RequestBody UploadInitRequest request,
      ActorContext actor) {
    var result =
        documentService.initiateUpload(
            projectId, request.fileName(), request.contentType(), request.size(), actor);
    return ResponseEntity.status(201).body(UploadInitResponse.from(result));
  }

  // --- ORG-scoped upload-init ---

  @PostMapping("/api/documents/upload-init")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<UploadInitResponse> initiateOrgUpload(
      @Valid @RequestBody UploadInitRequest request) {
    var result =
        documentService.initiateOrgUpload(
            request.fileName(), request.contentType(), request.size());
    return ResponseEntity.status(201).body(UploadInitResponse.from(result));
  }

  // --- CUSTOMER-scoped upload-init ---

  @PostMapping("/api/customers/{customerId}/documents/upload-init")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<UploadInitResponse> initiateCustomerUpload(
      @PathVariable UUID customerId, @Valid @RequestBody UploadInitRequest request) {
    var result =
        documentService.initiateCustomerUpload(
            customerId, request.fileName(), request.contentType(), request.size());
    return ResponseEntity.status(201).body(UploadInitResponse.from(result));
  }

  // --- Document listing by scope ---

  @GetMapping("/api/documents")
  public ResponseEntity<List<DocumentResponse>> listDocumentsByScope(
      @RequestParam String scope, @RequestParam(required = false) UUID customerId) {
    return ResponseEntity.ok(documentService.listDocumentsByScope(scope, customerId));
  }

  // --- Confirm, cancel, project listing, download (existing) ---

  @PostMapping("/api/documents/{documentId}/confirm")
  public ResponseEntity<DocumentResponse> confirmUpload(
      @PathVariable UUID documentId, ActorContext actor) {
    return ResponseEntity.ok(documentService.confirmUploadResponse(documentId, actor));
  }

  @DeleteMapping("/api/documents/{documentId}/cancel")
  public ResponseEntity<Void> cancelUpload(@PathVariable UUID documentId, ActorContext actor) {
    documentService.cancelUpload(documentId, actor);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/projects/{projectId}/documents")
  public ResponseEntity<List<DocumentResponse>> listDocuments(
      @PathVariable UUID projectId, ActorContext actor) {
    return ResponseEntity.ok(documentService.listDocumentResponses(projectId, actor));
  }

  @GetMapping("/api/documents/{documentId}/presign-download")
  public ResponseEntity<PresignDownloadResponse> presignDownload(
      @PathVariable UUID documentId, ActorContext actor) {
    var result = documentService.getPresignedDownloadUrl(documentId, actor);
    return ResponseEntity.ok(new PresignDownloadResponse(result.url(), result.expiresInSeconds()));
  }

  // --- Visibility toggle ---

  @PatchMapping("/api/documents/{documentId}/visibility")
  @RequiresCapability("PROJECT_MANAGEMENT")
  public ResponseEntity<DocumentResponse> toggleVisibility(
      @PathVariable UUID documentId, @Valid @RequestBody VisibilityRequest request) {
    return ResponseEntity.ok(
        documentService.toggleVisibilityResponse(documentId, request.visibility()));
  }

  // --- DTOs ---

  public record UploadInitRequest(
      @NotBlank(message = "fileName is required")
          @Size(max = 500, message = "fileName must be at most 500 characters")
          String fileName,
      @NotBlank(message = "contentType is required")
          @Size(max = 100, message = "contentType must be at most 100 characters")
          String contentType,
      @Positive(message = "size must be positive") long size) {}

  public record UploadInitResponse(UUID documentId, String presignedUrl, long expiresInSeconds) {
    public static UploadInitResponse from(DocumentService.UploadInitResult result) {
      return new UploadInitResponse(
          result.documentId(), result.presignedUrl(), result.expiresInSeconds());
    }
  }

  public record PresignDownloadResponse(String presignedUrl, long expiresInSeconds) {}

  public record VisibilityRequest(
      @NotBlank(message = "visibility is required") String visibility) {}

  public record DocumentResponse(
      UUID id,
      UUID projectId,
      String fileName,
      String contentType,
      long size,
      String status,
      String scope,
      UUID customerId,
      String visibility,
      UUID uploadedBy,
      String uploadedByName,
      Instant uploadedAt,
      Instant createdAt) {

    public static DocumentResponse from(Document document) {
      return from(document, Map.of());
    }

    public static DocumentResponse from(Document document, Map<UUID, String> memberNames) {
      return new DocumentResponse(
          document.getId(),
          document.getProjectId(),
          document.getFileName(),
          document.getContentType(),
          document.getSize(),
          document.getStatus().name(),
          document.getScope(),
          document.getCustomerId(),
          document.getVisibility(),
          document.getUploadedBy(),
          document.getUploadedBy() != null ? memberNames.get(document.getUploadedBy()) : null,
          document.getUploadedAt(),
          document.getCreatedAt());
    }
  }
}
