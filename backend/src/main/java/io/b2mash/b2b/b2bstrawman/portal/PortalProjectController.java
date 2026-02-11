package io.b2mash.b2b.b2bstrawman.portal;

import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal project endpoints. Lists projects linked to the authenticated customer. All endpoints are
 * read-only and require a valid portal JWT (enforced by CustomerAuthFilter).
 */
@RestController
@RequestMapping("/portal/projects")
public class PortalProjectController {

  private final PortalQueryService portalQueryService;

  public PortalProjectController(PortalQueryService portalQueryService) {
    this.portalQueryService = portalQueryService;
  }

  /** Lists projects linked to the authenticated customer. */
  @GetMapping
  public ResponseEntity<List<PortalProjectResponse>> listProjects() {
    UUID customerId = RequestScopes.requireCustomerId();
    var projects = portalQueryService.listCustomerProjects(customerId);

    var response =
        projects.stream()
            .map(
                p ->
                    new PortalProjectResponse(
                        p.getId(),
                        p.getName(),
                        p.getDescription(),
                        portalQueryService.countSharedProjectDocuments(p.getId()),
                        p.getCreatedAt()))
            .toList();

    return ResponseEntity.ok(response);
  }

  public record PortalProjectResponse(
      UUID id, String name, String description, long documentCount, Instant createdAt) {}
}
