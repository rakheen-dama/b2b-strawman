import { Loader2 } from "lucide-react";

import { getAuthContext } from "@/lib/auth";
import { ApiError } from "@/lib/api";
import { resolveDateRange } from "@/lib/date-utils";
import {
  fetchDashboardKpis,
  fetchProjectHealth,
  fetchTeamWorkload,
  fetchDashboardActivity,
} from "@/lib/actions/dashboard";
import { PageHeader } from "@/components/layout/page-header";
import { DashboardKpiCards } from "@/components/dashboard/kpi-cards";
import { ProjectHealthWidget } from "@/components/dashboard/project-health-widget";
import { TeamWorkloadWidget } from "@/components/dashboard/team-workload-widget";
import { RecentActivityWidget } from "@/components/dashboard/recent-activity-widget";
import { WidgetGrid } from "@/components/layout/widget-grid";
import { ProvisioningPendingRefresh } from "./provisioning-pending-refresh";

export default async function DashboardPage({
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
    // 500 + "Member context not available" = provisioned but member not synced.
    const isProvisioning =
      error instanceof ApiError &&
      (error.status === 403 ||
        (error.status === 500 &&
          error.detail?.title === "Member context not available"));

    if (isProvisioning) {
      return (
        <div className="flex min-h-[40vh] flex-col items-center justify-center gap-4">
          <Loader2 className="size-8 animate-spin text-slate-400" />
          <div className="text-center">
            <h2 className="font-display text-lg font-semibold text-slate-900">
              Setting up your workspace
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              This usually takes just a few seconds.
            </p>
          </div>
          <ProvisioningPendingRefresh />
        </div>
      );
    }

    throw error;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Dashboard"
        description={`Overview for ${slug}`}
      />

      <DashboardKpiCards kpis={kpis ?? null} isAdmin={isAdmin} />

      <WidgetGrid>
        <ProjectHealthWidget
          projects={projectHealth ?? null}
          orgSlug={slug}
        />
        <div className="space-y-6">
          <TeamWorkloadWidget
            data={teamWorkload ?? null}
            isAdmin={isAdmin}
          />
          <RecentActivityWidget
            items={activity ?? null}
            orgSlug={slug}
          />
        </div>
      </WidgetGrid>
    </div>
  );
}
