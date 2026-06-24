import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { CAPABILITIES } from "@/lib/capabilities";
import { ApiError, handleApiError } from "@/lib/api";
import { Loader2 } from "lucide-react";
import { ProvisioningPendingRefresh } from "./provisioning-pending-refresh";
import { DashboardHeader } from "./dashboard-header";
import { MetricsStrip } from "@/components/dashboard/metrics-strip";
import { ProjectHealthWidget } from "@/components/dashboard/project-health-widget";
import { TeamWorkloadWidget } from "@/components/dashboard/team-workload-widget";
import { RecentActivityWidget } from "@/components/dashboard/recent-activity-widget";
import { AdminStatsColumn } from "@/components/dashboard/admin-stats-column";
import { MyWeekColumn } from "@/components/dashboard/my-week-column";
import { DeadlineWidget } from "@/components/dashboard/deadline-widget";
import { SensitiveEventsWidget } from "@/components/dashboard/sensitive-events-widget";
import { TeamUtilizationWidget } from "@/components/dashboard/team-utilization-widget";
import { PipelineSummaryWidget } from "@/components/dashboard/pipeline-summary-widget";
import { UpcomingCourtDatesWidget } from "@/components/legal/upcoming-court-dates-widget";
import { ModuleGate } from "@/components/module-gate";
import {
  fetchDashboardKpis,
  fetchProjectHealth,
  fetchTeamWorkload,
  fetchDashboardActivity,
  fetchAggregatedCompleteness,
  fetchInformationRequestSummary,
  fetchAutomationSummary,
} from "@/lib/actions/dashboard";
import { getTeamCapacityGrid, type TeamCapacityGrid } from "@/lib/api/capacity";
import {
  listAuditEvents,
  listFacetEventTypes,
  type AuditEventResponse,
  type EventTypeFacet,
} from "@/lib/api/audit-events";
import {
  resolveDateRange,
  getCurrentMonday,
  formatDate as formatDateUtil,
  addWeeks,
} from "@/lib/date-utils";
import { GettingStartedCard } from "@/components/dashboard/getting-started-card";
import { ReturnToHandler } from "@/components/auth/return-to-handler";

export default async function OrgDashboardPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ from?: string; to?: string }>;
}) {
  const { slug } = await params;
  const resolvedSearchParams = await searchParams;
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;
  const { from, to } = resolveDateRange(resolvedSearchParams);

  let kpis;
  let projectHealth;
  let teamWorkload;
  let activity;
  let aggregatedCompleteness;
  let requestSummary;
  let automationSummary;

  try {
    [
      kpis,
      projectHealth,
      teamWorkload,
      activity,
      aggregatedCompleteness,
      requestSummary,
      automationSummary,
    ] = await Promise.all([
      fetchDashboardKpis(from, to),
      fetchProjectHealth(),
      fetchTeamWorkload(from, to),
      fetchDashboardActivity(15),
      isAdmin ? fetchAggregatedCompleteness() : Promise.resolve(null),
      isAdmin ? fetchInformationRequestSummary() : Promise.resolve(null),
      isAdmin ? fetchAutomationSummary() : Promise.resolve(null),
    ]);
  } catch (error) {
    // 403 = tenant not provisioned yet.
    // 500 + "Member context not available" = provisioned but member not synced yet.
    // Both are transient states during the webhook processing window after org creation.
    const isProvisioning =
      error instanceof ApiError &&
      (error.status === 403 ||
        (error.status === 500 && error.detail?.title === "Member context not available"));
    if (isProvisioning) {
      return (
        <div className="flex min-h-[40vh] flex-col items-center justify-center gap-4">
          <Loader2 className="text-muted-foreground size-8 animate-spin" />
          <div className="text-center">
            <h2 className="text-lg font-semibold">Setting up your workspace</h2>
            <p className="text-muted-foreground mt-1 text-sm">
              This usually takes just a few seconds.
            </p>
          </div>
          <ProvisioningPendingRefresh />
        </div>
      );
    }
    handleApiError(error);
  }

  // Sensitive events (Epic 509A) — last 7 days. Server-side capability gate
  // (PR #1284 follow-up): the previous version fetched WARNING+CRITICAL audit
  // events for every viewer and only hid client-side, which leaked Admin-only
  // events to Members in the same tenant via the RSC payload. We now resolve
  // TEAM_OVERSIGHT server-side and skip the fetch entirely when absent.
  const canSeeSensitive =
    isAdmin || (caps.capabilities ?? []).includes(CAPABILITIES.TEAM_OVERSIGHT);
  const SENSITIVE_LOOKBACK_MS = 7 * 24 * 60 * 60 * 1000;
  const SENSITIVE_VIEW_ALL_LOOKBACK_DAYS = 30;
  // Compute the View-all "from" timestamp ONCE in this server component and
  // pass to the widget — eliminates the SSR/CSR hydration mismatch caused by
  // calling Date.now() at client-render time inside <Link href=...>.
  const sensitiveTo = new Date();
  const sensitiveFrom = new Date(sensitiveTo.getTime() - SENSITIVE_LOOKBACK_MS);
  const sensitiveFromIso = sensitiveFrom.toISOString();
  const sensitiveToIso = sensitiveTo.toISOString();
  const sensitiveViewAllFromIso = new Date(
    sensitiveTo.getTime() - SENSITIVE_VIEW_ALL_LOOKBACK_DAYS * 24 * 60 * 60 * 1000
  ).toISOString();
  let sensitiveFacets: EventTypeFacet[] = [];
  let sensitiveRecent: AuditEventResponse[] | null = null;
  if (canSeeSensitive) {
    const [facets, recentPage] = await Promise.all([
      listFacetEventTypes({ from: sensitiveFromIso, to: sensitiveToIso }).catch(
        () => [] as EventTypeFacet[]
      ),
      listAuditEvents({
        severities: ["WARNING", "CRITICAL"],
        from: sensitiveFromIso,
        to: sensitiveToIso,
        size: 5,
      }).catch(() => null),
    ]);
    sensitiveFacets = facets;
    sensitiveRecent = recentPage ? recentPage.content : null;
  }

  // Capacity data for dashboard widgets
  const monday = getCurrentMonday();
  const weekEnd = addWeeks(monday, 1);
  const weekStartStr = formatDateUtil(monday);
  const weekEndStr = formatDateUtil(weekEnd);

  let capacityGrid: TeamCapacityGrid | null = null;

  // Fetch capacity grid (used by MetricsStrip for team utilization)
  capacityGrid = await getTeamCapacityGrid(weekStartStr, weekEndStr).catch(() => null);

  return (
    <div className="space-y-6">
      {/* Post-login return-to read-back. This is the real landing surface after
          the `/dashboard` → `/org/{slug}/dashboard` redirect chain, so the
          persisted `kazi.returnTo` is consumed + forwarded here (569A.3). */}
      <ReturnToHandler />

      <GettingStartedCard activeProjectCount={kpis?.activeProjectCount} />

      <DashboardHeader from={from} to={to} />

      <MetricsStrip
        kpis={kpis ?? null}
        capacityData={capacityGrid}
        projectHealth={projectHealth ?? null}
      />

      {/* Consulting-ZA profile-gated widget (self-gates via useProfile) */}
      <TeamUtilizationWidget slug={slug} />

      {/* Sales pipeline summary (admin/owner-scoped; self-gates via useCapabilities,
          mirroring TeamUtilizationWidget above — no outer server-side guard) */}
      <PipelineSummaryWidget slug={slug} />

      {/* Hero two-panel layout */}
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-5">
        <div className="lg:col-span-3">
          <ProjectHealthWidget projects={projectHealth ?? null} orgSlug={slug} />
        </div>
        <div className="lg:col-span-2">
          <TeamWorkloadWidget data={teamWorkload ?? null} isAdmin={isAdmin} />
        </div>
      </div>

      {/* Sensitive events row (Epic 509A) — gated server-side AND client-side. */}
      {canSeeSensitive && (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <SensitiveEventsWidget
            orgSlug={slug}
            facets={sensitiveFacets}
            recent={sensitiveRecent}
            viewAllFromIso={sensitiveViewAllFromIso}
          />
        </div>
      )}

      {/* Secondary three-column layout */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <RecentActivityWidget items={activity ?? null} orgSlug={slug} />
        <ModuleGate module="regulatory_deadlines">
          <DeadlineWidget orgSlug={slug} />
        </ModuleGate>
        {isAdmin ? (
          <AdminStatsColumn
            aggregatedCompleteness={aggregatedCompleteness ?? null}
            requestSummary={requestSummary ?? null}
            automationSummary={automationSummary ?? null}
            orgSlug={slug}
          />
        ) : (
          <MyWeekColumn kpis={kpis ?? null} activity={activity ?? null} />
        )}
      </div>

      {/* Legal widgets row */}
      <ModuleGate module="court_calendar">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
          <UpcomingCourtDatesWidget orgSlug={slug} />
        </div>
      </ModuleGate>
    </div>
  );
}
