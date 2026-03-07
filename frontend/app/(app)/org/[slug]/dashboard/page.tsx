import { getAuthContext, getCurrentUserEmail } from "@/lib/auth";
import { api, ApiError, handleApiError } from "@/lib/api";
import { Loader2 } from "lucide-react";
import { ProvisioningPendingRefresh } from "./provisioning-pending-refresh";
import { DashboardHeader } from "./dashboard-header";
import { KpiCardRow } from "@/components/dashboard/kpi-card-row";
import { ProjectHealthWidget } from "@/components/dashboard/project-health-widget";
import { TeamWorkloadWidget } from "@/components/dashboard/team-workload-widget";
import { RecentActivityWidget } from "@/components/dashboard/recent-activity-widget";
import { IncompleteProfilesWidget } from "@/components/dashboard/incomplete-profiles-widget";
import { InformationRequestsWidget } from "@/components/dashboard/information-requests-widget";
import { AutomationsWidget } from "@/components/automations/automations-widget";
import { TeamCapacityWidget } from "@/components/dashboard/team-capacity-widget";
import { MyScheduleWidget } from "@/components/dashboard/my-schedule-widget";
import {
  fetchDashboardKpis,
  fetchProjectHealth,
  fetchTeamWorkload,
  fetchDashboardActivity,
  fetchAggregatedCompleteness,
  fetchInformationRequestSummary,
  fetchAutomationSummary,
} from "@/lib/actions/dashboard";
import {
  getTeamCapacityGrid,
  listAllocations,
  listLeaveForMember,
  type TeamCapacityGrid,
  type AllocationResponse,
  type LeaveBlockResponse,
} from "@/lib/api/capacity";
import { resolveDateRange, getCurrentMonday, formatDate as formatDateUtil, addWeeks } from "@/lib/date-utils";
import type { OrgMember } from "@/lib/types";

export default async function OrgDashboardPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ from?: string; to?: string }>;
}) {
  const { slug } = await params;
  const resolvedSearchParams = await searchParams;
  const { orgRole } = await getAuthContext();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";
  const { from, to } = resolveDateRange(resolvedSearchParams);

  let kpis;
  let projectHealth;
  let teamWorkload;
  let activity;
  let aggregatedCompleteness;
  let requestSummary;
  let automationSummary;

  try {
    [kpis, projectHealth, teamWorkload, activity, aggregatedCompleteness, requestSummary, automationSummary] = await Promise.all([
      fetchDashboardKpis(from, to),
      fetchProjectHealth(),
      fetchTeamWorkload(from, to),
      fetchDashboardActivity(10),
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
        (error.status === 500 &&
          error.detail?.title === "Member context not available"));
    if (isProvisioning) {
      return (
        <div className="flex min-h-[40vh] flex-col items-center justify-center gap-4">
          <Loader2 className="text-muted-foreground size-8 animate-spin" />
          <div className="text-center">
            <h2 className="text-lg font-semibold">
              Setting up your workspace
            </h2>
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

  // Capacity data for dashboard widgets
  const monday = getCurrentMonday();
  const weekEnd = addWeeks(monday, 1);
  const weekStartStr = formatDateUtil(monday);
  const weekEndStr = formatDateUtil(weekEnd);

  let capacityGrid: TeamCapacityGrid | null = null;
  let myAllocations: AllocationResponse[] | null = null;
  let myLeave: LeaveBlockResponse[] | null = null;
  let myWeeklyCapacity = 40;

  // Fetch capacity grid, email, and members in parallel (all independent)
  const [gridResult, emailResult, membersResult] = await Promise.all([
    getTeamCapacityGrid(weekStartStr, weekEndStr).catch(() => null),
    getCurrentUserEmail().catch(() => null),
    api.get<OrgMember[]>("/api/members").catch(() => null as OrgMember[] | null),
  ]);
  capacityGrid = gridResult;

  // Resolve current member for personal schedule widget
  if (emailResult && membersResult) {
    const match = membersResult.find((m) => m.email === emailResult);
    if (match) {
      try {
        const [allocRes, leaveRes] = await Promise.all([
          listAllocations({ memberId: match.id, weekStart: weekStartStr, weekEnd: weekEndStr }),
          listLeaveForMember(match.id),
        ]);
        myAllocations = allocRes;
        myLeave = leaveRes;
        const memberRow = capacityGrid?.members.find((m) => m.memberId === match.id);
        if (memberRow && memberRow.weeks.length > 0) {
          myWeeklyCapacity = memberRow.weeks[0].effectiveCapacity;
        }
      } catch {
        // Non-fatal: my schedule widget will show error state
      }
    }
  }

  return (
    <div className="space-y-8">
      <DashboardHeader from={from} to={to} />

      <KpiCardRow kpis={kpis ?? null} isAdmin={isAdmin} orgSlug={slug} />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
        <div className="lg:col-span-3">
          <ProjectHealthWidget
            projects={projectHealth ?? null}
            orgSlug={slug}
          />
        </div>
        <div className="space-y-6 lg:col-span-2">
          <TeamWorkloadWidget data={teamWorkload ?? null} isAdmin={isAdmin} />
          <TeamCapacityWidget data={capacityGrid} orgSlug={slug} />
          <MyScheduleWidget
            allocations={myAllocations}
            leaveBlocks={myLeave}
            weeklyCapacity={myWeeklyCapacity}
            projectNames={
              capacityGrid
                ? Object.fromEntries(
                    capacityGrid.members.flatMap((m) =>
                      m.weeks.flatMap((w) =>
                        w.allocations.map((a) => [a.projectId, a.projectName]),
                      ),
                    ),
                  )
                : {}
            }
          />
          {isAdmin && (
            <IncompleteProfilesWidget
              data={aggregatedCompleteness ?? null}
              orgSlug={slug}
            />
          )}
          {isAdmin && (
            <InformationRequestsWidget
              data={requestSummary ?? null}
              orgSlug={slug}
            />
          )}
          {isAdmin && (
            <AutomationsWidget
              data={automationSummary ?? null}
              orgSlug={slug}
            />
          )}
          <RecentActivityWidget items={activity ?? null} orgSlug={slug} />
        </div>
      </div>
    </div>
  );
}
