package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalCommentService;
import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalReadModelService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.portal.PortalContactRepository;
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
@RequestMapping("/portal/projects/{projectId}/comments")
public class PortalCommentController {

  private final PortalReadModelService portalReadModelService;
  private final PortalCommentService portalCommentService;
  private final PortalContactRepository portalContactRepository;

  public PortalCommentController(
      PortalReadModelService portalReadModelService,
      PortalCommentService portalCommentService,
      PortalContactRepository portalContactRepository) {
    this.portalReadModelService = portalReadModelService;
    this.portalCommentService = portalCommentService;
    this.portalContactRepository = portalContactRepository;
  }

  /** Lists comments for a portal project. Returns 404 if the project is not linked to customer. */
  @GetMapping
  public ResponseEntity<List<PortalCommentResponse>> listProjectComments(
      @PathVariable UUID projectId) {
    UUID customerId = RequestScopes.requireCustomerId();
    String orgId = RequestScopes.requireOrgId();
    var comments = portalReadModelService.listProjectComments(projectId, customerId, orgId);

    var response =
        comments.stream()
            .map(c -> new PortalCommentResponse(c.id(), c.authorName(), c.content(), c.createdAt()))
            .toList();

    return ResponseEntity.ok(response);
  }

  /** Posts a new comment on a portal project. Returns 404 if project is not linked to customer. */
  @PostMapping
  public ResponseEntity<PortalCommentResponse> postComment(
      @PathVariable UUID projectId, @Valid @RequestBody CreateCommentRequest request) {
    UUID customerId = RequestScopes.requireCustomerId();
    String orgId = RequestScopes.requireOrgId();

    // Verify project belongs to customer (throws 404 if not)
    portalReadModelService.getProjectDetail(projectId, customerId, orgId);

    // Resolve contact display name (TENANT_ID already bound by CustomerAuthFilter)
    String authorName = "Portal User";
    UUID authorId = customerId; // fallback
    if (RequestScopes.PORTAL_CONTACT_ID.isBound()) {
      authorId = RequestScopes.PORTAL_CONTACT_ID.get();
      var contact = portalContactRepository.findById(authorId).orElse(null);
      if (contact != null && contact.getDisplayName() != null) {
        authorName = contact.getDisplayName();
      }
    }

    // Delegate to service (transactional: save, read-model sync, audit, event)
    var comment =
        portalCommentService.createPortalComment(
            projectId, authorId, authorName, request.content(), orgId);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            new PortalCommentResponse(
                comment.getId(), authorName, request.content(), comment.getCreatedAt()));
  }

  public record PortalCommentResponse(
      UUID id, String authorName, String content, Instant createdAt) {}

  public record CreateCommentRequest(@NotBlank @Size(max = 2000) String content) {}
}
