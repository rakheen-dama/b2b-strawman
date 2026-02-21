package io.b2mash.b2b.b2bstrawman.document;

import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.exception.MissingOrganizationContextException;
import io.b2mash.b2b.b2bstrawman.member.Member;
import io.b2mash.b2b.b2bstrawman.member.MemberRepository;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.security.ClerkJwtUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
  private final MemberRepository memberRepository;

  public DocumentController(DocumentService documentService, MemberRepository memberRepository) {
    this.documentService = documentService;
    this.memberRepository = memberRepository;
  }

  // --- PROJECT-scoped upload-init (existing) ---

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

  // --- ORG-scoped upload-init ---

  @PostMapping("/api/documents/upload-init")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<UploadInitResponse> initiateOrgUpload(
      @Valid @RequestBody UploadInitRequest request, JwtAuthenticationToken auth) {
    String orgId = ClerkJwtUtils.extractOrgId(auth.getToken());
    if (orgId == null) {
      throw new MissingOrganizationContextException();
    }
    UUID memberId = RequestScopes.requireMemberId();

    var result =
        documentService.initiateOrgUpload(
            request.fileName(), request.contentType(), request.size(), orgId, memberId);
    return ResponseEntity.status(201)
        .body(
            new UploadInitResponse(
                result.documentId(), result.presignedUrl(), result.expiresInSeconds()));
  }

  // --- CUSTOMER-scoped upload-init ---

  @PostMapping("/api/customers/{customerId}/documents/upload-init")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<UploadInitResponse> initiateCustomerUpload(
      @PathVariable UUID customerId,
      @Valid @RequestBody UploadInitRequest request,
      JwtAuthenticationToken auth) {
    String orgId = ClerkJwtUtils.extractOrgId(auth.getToken());
    if (orgId == null) {
      throw new MissingOrganizationContextException();
    }
    UUID memberId = RequestScopes.requireMemberId();

    var result =
        documentService.initiateCustomerUpload(
            customerId, request.fileName(), request.contentType(), request.size(), orgId, memberId);
    return ResponseEntity.status(201)
        .body(
            new UploadInitResponse(
                result.documentId(), result.presignedUrl(), result.expiresInSeconds()));
  }

  // --- Document listing by scope ---

  @GetMapping("/api/documents")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<DocumentResponse>> listDocumentsByScope(
      @RequestParam String scope, @RequestParam(required = false) UUID customerId) {
    var documents =
        switch (scope.toUpperCase()) {
          case "ORG" -> documentService.listOrgDocuments();
          case "CUSTOMER" -> {
            if (customerId == null) {
              throw new InvalidStateException(
                  "Missing customerId", "customerId is required when scope is CUSTOMER");
            }
            yield documentService.listCustomerDocuments(customerId);
          }
          default ->
              throw new InvalidStateException("Invalid scope", "scope must be 'ORG' or 'CUSTOMER'");
        };
    var memberNames = resolveNames(documents);
    var response = documents.stream().map(d -> DocumentResponse.from(d, memberNames)).toList();
    return ResponseEntity.ok(response);
  }

  // --- Confirm, cancel, project listing, download (existing) ---

  @PostMapping("/api/documents/{documentId}/confirm")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<DocumentResponse> confirmUpload(@PathVariable UUID documentId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var document = documentService.confirmUpload(documentId, memberId, orgRole);
    var memberNames = resolveNames(List.of(document));
    return ResponseEntity.ok(DocumentResponse.from(document, memberNames));
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
  public ResponseEntity<List<DocumentResponse>> listDocuments(@PathVariable UUID projectId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    var documents = documentService.listDocuments(projectId, memberId, orgRole);
    var memberNames = resolveNames(documents);
    var response = documents.stream().map(d -> DocumentResponse.from(d, memberNames)).toList();
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

  // --- Visibility toggle ---

  @PatchMapping("/api/documents/{documentId}/visibility")
  @PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<DocumentResponse> toggleVisibility(
      @PathVariable UUID documentId, @Valid @RequestBody VisibilityRequest request) {
    var document = documentService.toggleVisibility(documentId, request.visibility());
    var memberNames = resolveNames(List.of(document));
    return ResponseEntity.ok(DocumentResponse.from(document, memberNames));
  }

  private Map<UUID, String> resolveNames(List<Document> documents) {
    var ids =
        documents.stream()
            .map(Document::getUploadedBy)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    if (ids.isEmpty()) return Map.of();
    return memberRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(Member::getId, Member::getName, (a, b) -> a));
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

  public record UploadInitResponse(UUID documentId, String presignedUrl, long expiresInSeconds) {}

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
