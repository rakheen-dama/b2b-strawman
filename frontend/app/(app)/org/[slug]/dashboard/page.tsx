import { getAuthContext } from "@/lib/auth";
import { ApiError, handleApiError } from "@/lib/api";
import { Loader2 } from "lucide-react";
import { ProvisioningPendingRefresh } from "./provisioning-pending-refresh";
import { DashboardHeader } from "./dashboard-header";
import { KpiCardRow } from "@/components/dashboard/kpi-card-row";
import { ProjectHealthWidget } from "@/components/dashboard/project-health-widget";
import { TeamWorkloadWidget } from "@/components/dashboard/team-workload-widget";
import { RecentActivityWidget } from "@/components/dashboard/recent-activity-widget";
import {
  fetchDashboardKpis,
  fetchProjectHealth,
  fetchTeamWorkload,
  fetchDashboardActivity,
} from "@/lib/actions/dashboard";
import { resolveDateRange } from "@/lib/date-utils";

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

  try {
    [kpis, projectHealth, teamWorkload, activity] = await Promise.all([
      fetchDashboardKpis(from, to),
      fetchProjectHealth(),
      fetchTeamWorkload(from, to),
      fetchDashboardActivity(10),
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
          <RecentActivityWidget items={activity ?? null} orgSlug={slug} />
        </div>
      </div>
    </div>
  );
}
