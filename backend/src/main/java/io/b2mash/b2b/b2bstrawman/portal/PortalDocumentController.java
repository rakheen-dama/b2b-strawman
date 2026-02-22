package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.document.Document;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal document endpoints. Provides read-only access to SHARED-visibility documents for the
 * authenticated customer. All endpoints require a valid portal JWT (enforced by
 * CustomerAuthFilter).
 */
@RestController
@RequestMapping("/portal")
public class PortalDocumentController {

  private final PortalQueryService portalQueryService;

  public PortalDocumentController(PortalQueryService portalQueryService) {
    this.portalQueryService = portalQueryService;
  }

  /** Lists SHARED documents for a project linked to the customer. */
  @GetMapping("/projects/{projectId}/documents")
  public ResponseEntity<List<PortalDocumentResponse>> listProjectDocuments(
      @PathVariable UUID projectId) {
    UUID customerId = RequestScopes.requireCustomerId();
    var documents = portalQueryService.listProjectDocuments(projectId, customerId);
    var response = documents.stream().map(PortalDocumentResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  /**
   * Lists all SHARED documents visible to the customer (org-scoped SHARED + customer-scoped SHARED
   * for this customer).
   */
  @GetMapping("/documents")
  public ResponseEntity<List<PortalDocumentResponse>> listCustomerDocuments() {
    UUID customerId = RequestScopes.requireCustomerId();
    var documents = portalQueryService.listCustomerDocuments(customerId);
    var response = documents.stream().map(PortalDocumentResponse::from).toList();
    return ResponseEntity.ok(response);
  }

  /** Gets a presigned download URL for a SHARED document accessible to the customer. */
  @GetMapping("/documents/{documentId}/presign-download")
  public ResponseEntity<PortalPresignDownloadResponse> presignDownload(
      @PathVariable UUID documentId) {
    UUID customerId = RequestScopes.requireCustomerId();
    var downloadResult = portalQueryService.getPresignedDownloadUrl(documentId, customerId);
    return ResponseEntity.ok(
        new PortalPresignDownloadResponse(downloadResult.url(), downloadResult.expiresInSeconds()));
  }

  // --- DTOs ---

  public record PortalDocumentResponse(
      UUID id,
      String fileName,
      String contentType,
      long size,
      String scope,
      String status,
      Instant createdAt) {

    public static PortalDocumentResponse from(Document document) {
      return new PortalDocumentResponse(
          document.getId(),
          document.getFileName(),
          document.getContentType(),
          document.getSize(),
          document.getScope(),
          document.getStatus().name(),
          document.getCreatedAt());
    }
  }

  public record PortalPresignDownloadResponse(String presignedUrl, long expiresInSeconds) {}
}
