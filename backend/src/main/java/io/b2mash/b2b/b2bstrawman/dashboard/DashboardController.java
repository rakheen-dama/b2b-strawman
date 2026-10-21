package io.b2mash.b2b.b2bstrawman.dashboard;

import io.b2mash.b2b.b2bstrawman.dashboard.dto.CrossMatterActivityItem;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.FicaOutstandingResponse;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.KpiResponse;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.MatterHealth;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.MatterHealthDetail;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.MemberHoursEntry;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.PersonalDashboard;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TaskSummary;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.TrustPositionResponse;
import io.b2mash.b2b.b2bstrawman.dashboard.dto.UnbilledWipResponse;
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
  @GetMapping("/api/matters/{matterId}/health")
  public ResponseEntity<MatterHealthDetail> getProjectHealth(
      @PathVariable UUID matterId, ActorContext actor) {
    return ResponseEntity.ok(
        dashboardService.getProjectHealth(matterId, RequestScopes.TENANT_ID.get(), actor));
  }

  /**
   * Returns task counts broken down by status for a project. Requires view access to the project.
   */
  @GetMapping("/api/matters/{matterId}/task-summary")
  public ResponseEntity<TaskSummary> getTaskSummary(
      @PathVariable UUID matterId, ActorContext actor) {
    return ResponseEntity.ok(
        dashboardService.getTaskSummary(matterId, RequestScopes.TENANT_ID.get(), actor));
  }

  /**
   * Returns per-member hour breakdown for a project within a date range. Requires view access to
   * the project. Both date parameters are required.
   */
  @GetMapping("/api/matters/{matterId}/member-hours")
  public ResponseEntity<List<MemberHoursEntry>> getProjectMemberHours(
      @PathVariable UUID matterId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      ActorContext actor) {
    return ResponseEntity.ok(
        dashboardService.getProjectMemberHours(
            matterId, RequestScopes.TENANT_ID.get(), from, to, actor));
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
  @GetMapping("/api/dashboard/matter-health")
  public ResponseEntity<List<MatterHealth>> getProjectHealthList(ActorContext actor) {
    return ResponseEntity.ok(
        dashboardService.getProjectHealthList(RequestScopes.TENANT_ID.get(), actor));
  }

  /**
   * Returns recent cross-project activity events. Admin/owner sees all events; regular members see
   * only events from projects they belong to. Limit is optional (default 10, clamped to 1-50).
   */
  @GetMapping("/api/dashboard/activity")
  public ResponseEntity<List<CrossMatterActivityItem>> getCrossProjectActivity(
      @RequestParam(defaultValue = "10") int limit, ActorContext actor) {
    return ResponseEntity.ok(
        dashboardService.getCrossProjectActivity(RequestScopes.TENANT_ID.get(), actor, limit));
  }

  // --- Legal-native home reads (Phase 87, Epic 614A) ---

  /**
   * Returns unbilled WIP (billable, not yet invoiced time) by fee earner. Callers without
   * FINANCIAL_VISIBILITY (and not admin/owner) receive their own row only, hours without values.
   */
  @GetMapping("/api/dashboard/unbilled-wip")
  public ResponseEntity<UnbilledWipResponse> getUnbilledWip(ActorContext actor) {
    return ResponseEntity.ok(dashboardService.getUnbilledWip(actor));
  }

  /**
   * Returns ONBOARDING/ACTIVE clients whose FICA status is not DONE, longest-outstanding first.
   * Limit is optional (default 10, clamped to 1-50).
   */
  @GetMapping("/api/dashboard/fica-outstanding")
  @RequiresCapability("CUSTOMER_MANAGEMENT")
  public ResponseEntity<FicaOutstandingResponse> getFicaOutstanding(
      @RequestParam(defaultValue = "10") int limit) {
    return ResponseEntity.ok(dashboardService.getFicaOutstanding(limit));
  }

  /**
   * Returns the trust position for the primary (or requested) trust account: cashbook balance vs
   * client-ledger total, latest reconciliation and pending approvals. Composes the existing trust
   * reads, so it reveals nothing they do not.
   */
  @GetMapping("/api/dashboard/trust-position")
  @RequiresCapability("VIEW_TRUST")
  public ResponseEntity<TrustPositionResponse> getTrustPosition(
      @RequestParam(required = false) UUID accountId) {
    return ResponseEntity.ok(dashboardService.getTrustPosition(accountId));
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
}
