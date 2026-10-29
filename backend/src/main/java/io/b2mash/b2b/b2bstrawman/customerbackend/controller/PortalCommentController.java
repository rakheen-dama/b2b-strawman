package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalCommentService;
import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalReadModelService;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal comment endpoints. Lists and creates comments for a project linked to the authenticated
 * customer. All endpoints require a valid portal JWT (enforced by CustomerAuthFilter).
 */
@RestController
@RequestMapping("/portal/matters/{matterId}/comments")
public class PortalCommentController {

  private final PortalReadModelService portalReadModelService;
  private final PortalCommentService portalCommentService;

  public PortalCommentController(
      PortalReadModelService portalReadModelService, PortalCommentService portalCommentService) {
    this.portalReadModelService = portalReadModelService;
    this.portalCommentService = portalCommentService;
  }

  /** Lists comments for a portal project. Returns 404 if the project is not linked to customer. */
  @GetMapping
  public ResponseEntity<List<PortalCommentResponse>> listProjectComments(
      @PathVariable UUID matterId) {
    UUID customerId = RequestScopes.requireCustomerId();
    String orgId = RequestScopes.requireOrgId();
    return ResponseEntity.ok(portalCommentService.listProjectComments(matterId, customerId, orgId));
  }

  /** Posts a new comment on a portal project. Returns 404 if project is not linked to customer. */
  @PostMapping
  public ResponseEntity<PortalCommentResponse> postComment(
      @PathVariable UUID matterId, @Valid @RequestBody CreateCommentRequest request) {
    UUID customerId = RequestScopes.requireCustomerId();
    String orgId = RequestScopes.requireOrgId();

    // Verify project belongs to customer (throws 404 if not) and is not archived
    var project = portalReadModelService.getProjectDetail(matterId, customerId, orgId);
    if ("ARCHIVED".equals(project.status())) {
      throw new InvalidStateException(
          "Matter is archived", "Matter is archived. No modifications allowed.");
    }

    // Resolve contact display name (TENANT_ID already bound by CustomerAuthFilter)
    String authorName = "Portal User";
    UUID authorId = customerId; // fallback
    if (RequestScopes.PORTAL_CONTACT_ID.isBound()) {
      authorId = RequestScopes.PORTAL_CONTACT_ID.get();
      authorName = portalCommentService.resolveContactDisplayName(authorId);
    }

    // Delegate to service (transactional: save, read-model sync, audit, event)
    var comment =
        portalCommentService.createPortalComment(
            matterId, authorId, authorName, request.content(), orgId);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            new PortalCommentResponse(
                comment.getId(), authorName, request.content(), comment.getCreatedAt(), List.of()));
  }

  /** Portal-authored comments carry no attachments, so POST returns an empty list. */
  public record PortalCommentResponse(
      UUID id,
      String authorName,
      String content,
      Instant createdAt,
      List<PortalCommentAttachmentResponse> attachments) {}

  /**
   * A chip; the client downloads via {@code GET /portal/documents/{documentId}/presign-download}.
   */
  public record PortalCommentAttachmentResponse(
      UUID documentId, String fileName, String contentType, long size) {}

  public record CreateCommentRequest(@NotBlank @Size(max = 2000) String content) {}
}
