import { auth } from "@clerk/nextjs/server";
import { api } from "@/lib/api";
import type {
  UtilizationResponse,
  OrgProfitabilityResponse,
} from "@/lib/types";
import { UtilizationTable } from "@/components/profitability/utilization-table";
import { ProjectProfitabilityTable } from "@/components/profitability/project-profitability-table";
import { CustomerProfitabilitySection } from "@/components/profitability/customer-profitability-section";

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
  await params;
  const { orgRole } = await auth();

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return (
      <div className="space-y-8">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Profitability
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to view profitability reports. Only admins
          and owners can access this page.
        </p>
      </div>
    );
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
          Profitability
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Team utilization, project profitability, and customer profitability
          across your organization
        </p>
      </div>

      <UtilizationTable
        initialData={utilization}
        initialFrom={from}
        initialTo={to}
      />

      <ProjectProfitabilityTable
        initialData={profitability}
        initialFrom={from}
        initialTo={to}
      />

      <CustomerProfitabilitySection
        initialData={profitability}
        initialFrom={from}
        initialTo={to}
      />
    </div>
  );
}
