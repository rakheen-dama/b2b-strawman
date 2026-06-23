package io.b2mash.b2b.b2bstrawman.dashboard;

import io.b2mash.b2b.b2bstrawman.dashboard.dto.CrossProjectActivityItem;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.KpiResponse;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.MemberHoursEntry;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.PersonalDashboard;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.PipelineSummaryResponse;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.ProjectHealth;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.ProjectHealthDetail;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TaskSummary;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TeamWorkloadEntry;
import io.b2mash.b2b.b2bstrawman.multitenancy.ActorContext;
import io.b2mash.b2b.b2bstrawman.multitenancy.RequestScopes;
import io.b2mash.b2b.b2bstrawman.orgrole.RequiresCapability;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for project-scoped and org-level dashboard endpoints. */
@RestController
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  // --- Project-scoped endpoints ---

  /**
   * Returns the health status for a project, including health reasons and underlying metrics.
   * Requires view access to the project.
   */
  @GetMapping("/api/projects/{projectId}/health")
  public ResponseEntity<ProjectHealthDetail> getProjectHealth(
      @PathVariable UUID projectId, ActorContext actor) {
    return ResponseEntity.ok(
        dashboardService.getProjectHealth(projectId, RequestScopes.TENANT_ID.get(), actor));
  }

  /**
   * Returns task counts broken down by status for a project. Requires view access to the project.
   */
  @GetMapping("/api/projects/{projectId}/task-summary")
  public ResponseEntity<TaskSummary> getTaskSummary(
      @PathVariable UUID projectId, ActorContext actor) {
    return ResponseEntity.ok(
        dashboardService.getTaskSummary(projectId, RequestScopes.TENANT_ID.get(), actor));
  }

  /**
   * Returns per-member hour breakdown for a project within a date range. Requires view access to
   * the project. Both date parameters are required.
   */
  @GetMapping("/api/projects/{projectId}/member-hours")
  public ResponseEntity<List<MemberHoursEntry>> getProjectMemberHours(
      @PathVariable UUID projectId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      ActorContext actor) {
    return ResponseEntity.ok(
        dashboardService.getProjectMemberHours(
            projectId, RequestScopes.TENANT_ID.get(), from, to, actor));
  }

  // --- Org-level endpoints ---

  /**
   * Returns org-level KPIs with trend and previous-period comparison. Financial fields
   * (billablePercent, averageMarginPercent) are null for non-admin members.
   */
  @GetMapping("/api/dashboard/kpis")
  public ResponseEntity<KpiResponse> getCompanyKpis(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(
        dashboardService.getCompanyKpis(
            RequestScopes.TENANT_ID.get(), RequestScopes.getOrgRole(), from, to));
  }

  /**
   * Returns the project health list for accessible projects. Sorted by severity (CRITICAL first),
   * then by completion percent ascending.
   */
  @GetMapping("/api/dashboard/project-health")
  public ResponseEntity<List<ProjectHealth>> getProjectHealthList(ActorContext actor) {
    return ResponseEntity.ok(
        dashboardService.getProjectHealthList(RequestScopes.TENANT_ID.get(), actor));
  }

  /**
   * Returns team workload data aggregating hours per member across projects. Admin/owner sees all
   * members; regular members see only their own entry. Both date parameters are required.
   */
  @GetMapping("/api/dashboard/team-workload")
  public ResponseEntity<List<TeamWorkloadEntry>> getTeamWorkload(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      ActorContext actor) {
    return ResponseEntity.ok(
        dashboardService.getTeamWorkload(RequestScopes.TENANT_ID.get(), actor, from, to));
  }

  /**
   * Returns recent cross-project activity events. Admin/owner sees all events; regular members see
   * only events from projects they belong to. Limit is optional (default 10, clamped to 1-50).
   */
  @GetMapping("/api/dashboard/activity")
  public ResponseEntity<List<CrossProjectActivityItem>> getCrossProjectActivity(
      @RequestParam(defaultValue = "10") int limit, ActorContext actor) {
    return ResponseEntity.ok(
        dashboardService.getCrossProjectActivity(RequestScopes.TENANT_ID.get(), actor, limit));
  }

  // --- Personal dashboard endpoint (Epic 79A) ---

  /**
   * Returns a personal dashboard for the authenticated member with utilization, project breakdown,
   * overdue tasks, upcoming deadlines, and trend data. Self-scoped: no ProjectAccessService needed
   * because queries filter by member_id (ADR-023).
   */
  @GetMapping("/api/dashboard/personal")
  public ResponseEntity<PersonalDashboard> getPersonalDashboard(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(
        dashboardService.getPersonalDashboard(
            RequestScopes.requireMemberId(), RequestScopes.TENANT_ID.get(), from, to));
  }

  // --- Pipeline summary endpoint (Epic 578A) ---

  /**
   * Returns the CRM pipeline summary: open weighted value, per-stage breakdown, win rate over a
   * date window (trailing 90 days by default), average deal size, and average days-to-close. Gated
   * on {@code VIEW_DEALS} (default-on for Owner/Admin). All parameters are optional; {@code
   * ownerId} scopes the aggregation to a single deal owner. Admin/owner actors may scope to any
   * owner (or all owners when {@code ownerId} is null); regular members are restricted to their own
   * pipeline regardless of the requested {@code ownerId}.
   */
  @GetMapping("/api/dashboard/pipeline-summary")
  @RequiresCapability("VIEW_DEALS")
  public ResponseEntity<PipelineSummaryResponse> getPipelineSummary(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(required = false) UUID ownerId,
      ActorContext actor) {
    return ResponseEntity.ok(dashboardService.getPipelineSummary(from, to, ownerId, actor));
  }
}
