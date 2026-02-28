import { getAuthContext } from "@/lib/auth";
import { api } from "@/lib/api";
import { resolveDateRange } from "@/lib/date-utils";
import { PageHeader } from "@/components/layout/page-header";
import { ProfitabilityView } from "@/components/profitability/profitability-view";
import type {
  UtilizationResponse,
  OrgProfitabilityResponse,
} from "@/lib/types";

export default async function ProfitabilityPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ from?: string; to?: string }>;
}) {
  await params;
  const resolvedSearchParams = await searchParams;
  const { orgRole } = await getAuthContext();

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return (
      <div className="space-y-6">
        <PageHeader
          title="Profitability"
          description="You do not have permission to view profitability reports. Only admins and owners can access this page."
        />
      </div>
    );
  }

  const { from, to } = resolveDateRange(resolvedSearchParams);

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
    // Non-fatal: show empty state, client can retry with period selector
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Profitability"
        description="Team utilization, project margins, and customer profitability"
      />

      <ProfitabilityView
        initialProfitability={profitability}
        initialUtilization={utilization}
        initialFrom={from}
        initialTo={to}
      />
    </div>
  );
}
