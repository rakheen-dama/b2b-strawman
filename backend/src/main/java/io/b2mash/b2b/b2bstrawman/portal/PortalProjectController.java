package io.b2mash.b2b.b2bstrawman.portal;

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
 * Portal project endpoints. Lists projects linked to the authenticated customer. All endpoints are
 * read-only and require a valid portal JWT (enforced by CustomerAuthFilter).
 */
@RestController
@RequestMapping("/portal/projects")
public class PortalProjectController {

  private final PortalQueryService portalQueryService;
  private final PortalReadModelService portalReadModelService;

  public PortalProjectController(
      PortalQueryService portalQueryService, PortalReadModelService portalReadModelService) {
    this.portalQueryService = portalQueryService;
    this.portalReadModelService = portalReadModelService;
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

  /** Returns project detail for a specific project linked to the authenticated customer. */
  @GetMapping("/{id}")
  public ResponseEntity<PortalProjectDetailResponse> getProjectDetail(@PathVariable UUID id) {
    UUID customerId = RequestScopes.requireCustomerId();
    String orgId = RequestScopes.requireOrgId();
    var project = portalReadModelService.getProjectDetail(id, customerId, orgId);

    return ResponseEntity.ok(
        new PortalProjectDetailResponse(
            project.id(),
            project.name(),
            project.status(),
            project.description(),
            project.documentCount(),
            project.commentCount(),
            project.createdAt()));
  }

  /** Lists tasks for a specific project linked to the authenticated customer. */
  @GetMapping("/{projectId}/tasks")
  public ResponseEntity<List<PortalTaskResponse>> listTasks(@PathVariable UUID projectId) {
    UUID customerId = RequestScopes.requireCustomerId();
    String orgId = RequestScopes.requireOrgId();
    var tasks = portalReadModelService.listTasks(orgId, customerId, projectId);

    var response =
        tasks.stream()
            .map(
                t ->
                    new PortalTaskResponse(
                        t.id(), t.name(), t.status(), t.assigneeName(), t.sortOrder()))
            .toList();

    return ResponseEntity.ok(response);
  }

  public record PortalProjectResponse(
      UUID id, String name, String description, long documentCount, Instant createdAt) {}

  public record PortalProjectDetailResponse(
      UUID id,
      String name,
      String status,
      String description,
      int documentCount,
      int commentCount,
      Instant createdAt) {}

  public record PortalTaskResponse(
      UUID id, String name, String status, String assigneeName, int sortOrder) {}
}
