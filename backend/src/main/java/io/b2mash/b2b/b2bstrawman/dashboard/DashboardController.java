package io.b2mash.b2b.b2bstrawman.dashboard;

import io.b2mash.b2b.b2bstrawman.dashboard.dto.ProjectHealthDetail;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TaskSummary;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for project-scoped dashboard endpoints. */
@RestController
public class DashboardController {

  private final DashboardService dashboardService;
  private final ProjectAccessService projectAccessService;

  public DashboardController(
      DashboardService dashboardService, ProjectAccessService projectAccessService) {
    this.dashboardService = dashboardService;
    this.projectAccessService = projectAccessService;
  }

  /**
   * Returns the health status for a project, including health reasons and underlying metrics.
   * Requires view access to the project.
   */
  @GetMapping("/api/projects/{projectId}/health")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<ProjectHealthDetail> getProjectHealth(@PathVariable UUID projectId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    String tenantId = RequestScopes.TENANT_ID.get();

    projectAccessService.requireViewAccess(projectId, memberId, orgRole);

    var health = dashboardService.getProjectHealth(projectId, tenantId);
    return ResponseEntity.ok(health);
  }

  /**
   * Returns task counts broken down by status for a project. Requires view access to the project.
   */
  @GetMapping("/api/projects/{projectId}/task-summary")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<TaskSummary> getTaskSummary(@PathVariable UUID projectId) {
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();
    String tenantId = RequestScopes.TENANT_ID.get();

    projectAccessService.requireViewAccess(projectId, memberId, orgRole);

    var summary = dashboardService.getTaskSummary(projectId, tenantId);
    return ResponseEntity.ok(summary);
  }
}
