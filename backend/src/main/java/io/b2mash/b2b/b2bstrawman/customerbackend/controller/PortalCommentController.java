package io.b2mash.b2b.b2bstrawman.customerbackend.controller;

import io.b2mash.b2b.b2bstrawman.customerbackend.service.PortalReadModelService;
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
 * Portal comment endpoints. Lists comments for a project linked to the authenticated customer. All
 * endpoints are read-only and require a valid portal JWT (enforced by CustomerAuthFilter).
 */
@RestController
@RequestMapping("/portal/projects/{projectId}/comments")
public class PortalCommentController {

  private final PortalReadModelService portalReadModelService;

  public PortalCommentController(PortalReadModelService portalReadModelService) {
    this.portalReadModelService = portalReadModelService;
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

  public record PortalCommentResponse(
      UUID id, String authorName, String content, Instant createdAt) {}
}
