package io.b2mash.b2b.b2bstrawman.dashboard;

import io.b2mash.b2b.b2bstrawman.dashboard.dto.KpiResponse;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.ProjectHealth;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.ProjectHealthDetail;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TaskSummary;
import io.b2mash.b2b.b2bstrawman.exception.InvalidStateException;
import io.b2mash.b2b.b2bstrawman.member.ProjectAccessService;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for project-scoped and org-level dashboard endpoints. */
@RestController
public class DashboardController {

  private final DashboardService dashboardService;
  private final ProjectAccessService projectAccessService;

  public DashboardController(
      DashboardService dashboardService, ProjectAccessService projectAccessService) {
    this.dashboardService = dashboardService;
    this.projectAccessService = projectAccessService;
  }

  // --- Project-scoped endpoints ---

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

  // --- Org-level endpoints ---

  /**
   * Returns org-level KPIs with trend and previous-period comparison. Financial fields
   * (billablePercent, averageMarginPercent) are null for non-admin members.
   */
  @GetMapping("/api/dashboard/kpis")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<KpiResponse> getCompanyKpis(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    if (from.isAfter(to)) {
      throw new InvalidStateException(
          "Invalid Date Range", "'from' date must not be after 'to' date");
    }

    String tenantId = RequestScopes.TENANT_ID.get();
    String orgRole = RequestScopes.getOrgRole();

    var kpis = dashboardService.getCompanyKpis(tenantId, orgRole, from, to);
    return ResponseEntity.ok(kpis);
  }

  /**
   * Returns the project health list for accessible projects. Sorted by severity (CRITICAL first),
   * then by completion percent ascending.
   */
  @GetMapping("/api/dashboard/project-health")
  @PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")
  public ResponseEntity<List<ProjectHealth>> getProjectHealthList() {
    String tenantId = RequestScopes.TENANT_ID.get();
    UUID memberId = RequestScopes.requireMemberId();
    String orgRole = RequestScopes.getOrgRole();

    var healthList = dashboardService.getProjectHealthList(tenantId, memberId, orgRole);
    return ResponseEntity.ok(healthList);
  }
}
