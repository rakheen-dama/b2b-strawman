import { TrendingUp } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { createMessages } from "@/lib/messages";
import { EmptyState } from "@/components/empty-state";
import { PermissionDenied } from "@/components/permission-denied";
import { TerminologyHeading } from "@/components/terminology-heading";
import type {
  UtilizationResponse,
  OrgProfitabilityResponse,
} from "@/lib/types";
import { ProfitabilityContent } from "@/components/profitability/profitability-content";

/** Returns first day of current month and today as 'YYYY-MM-DD'. */
function getCurrentMonthRange(): { from: string; to: string } {
  const now = new Date();
  const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
  return {
    from: firstDay.toLocaleDateString("en-CA"),
    to: now.toLocaleDateString("en-CA"),
  };
}

export default async function ProfitabilityPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const capData = await fetchMyCapabilities();
  const { t } = createMessages("empty-states");

  if (!capData.isAdmin && !capData.isOwner && !capData.capabilities.includes("FINANCIAL_VISIBILITY")) {
    return <PermissionDenied featureName="Profitability" dashboardHref={`/org/${slug}/dashboard`} />;
  }

  const { from, to } = getCurrentMonthRange();

  let utilization: UtilizationResponse = { from, to, members: [] };
  let profitability: OrgProfitabilityResponse = { projects: [] };

  try {
    [utilization, profitability] = await Promise.all([
      api.get<UtilizationResponse>(
        `/api/reports/utilization?from=${from}&to=${to}`,
      ),
      api.get<OrgProfitabilityResponse>(
        `/api/reports/profitability?from=${from}&to=${to}`,
      ),
    ]);
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          <TerminologyHeading term="Profitability" />
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Team utilization, project profitability, and customer profitability
          across your organization
        </p>
      </div>

      {utilization.members.length === 0 && profitability.projects.length === 0 ? (
        <EmptyState
          icon={TrendingUp}
          title={t("profitability.page.heading")}
          description={t("profitability.page.description")}
          secondaryLink={{
            label: t("profitability.page.link"),
            href: `/org/${slug}/settings/rates`,
          }}
        />
      ) : (
        <ProfitabilityContent
          initialUtilization={utilization}
          initialProfitability={profitability}
          initialFrom={from}
          initialTo={to}
        />
      )}
    </div>
  );
}
