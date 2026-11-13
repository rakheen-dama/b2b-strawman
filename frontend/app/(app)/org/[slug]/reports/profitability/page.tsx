import { TrendingUp } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { getBillingTrend } from "@/lib/api/reports";
import { settle } from "@/lib/settle";
import { createMessages } from "@/lib/messages";
import { EmptyState } from "@/components/empty-state";
import { PermissionDenied } from "@/components/permission-denied";
import type { UtilizationResponse, OrgProfitabilityResponse } from "@/lib/types";
import { ProfitabilityContent } from "@/components/profitability/profitability-content";
import { BillingTrendChart } from "@/components/profitability/billing-trend-chart";
import { todayInSA } from "@b2mash/shared/date";
import { PageHeader } from "@b2mash/ui/page-header";

/** Returns the first day of the current South African month and SA today as 'YYYY-MM-DD'. */
function getCurrentMonthRange(): { from: string; to: string } {
  const today = todayInSA();
  return { from: `${today.slice(0, 8)}01`, to: today };
}

export default async function ReportsProfitabilityPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const capData = await fetchMyCapabilities();
  const { t } = createMessages("empty-states");

  if (
    !capData.isAdmin &&
    !capData.isOwner &&
    !capData.capabilities.includes("FINANCIAL_VISIBILITY")
  ) {
    return (
      <PermissionDenied featureName="Profitability" dashboardHref={`/org/${slug}/dashboard`} />
    );
  }

  const { from, to } = getCurrentMonthRange();
  // Independent of the page's month range: always the last 12 SA months (ADR-372). Started first
  // so it runs in parallel with the two reads below; settle() never throws (403/404 → absent).
  const billingTrendRead = settle(true, "billing trend", () => getBillingTrend(12));

  let utilization: UtilizationResponse = { from, to, members: [] };
  let profitability: OrgProfitabilityResponse = { matters: [] };

  try {
    [utilization, profitability] = await Promise.all([
      api.get<UtilizationResponse>(`/api/reports/utilization?from=${from}&to=${to}`),
      api.get<OrgProfitabilityResponse>(`/api/reports/profitability?from=${from}&to=${to}`),
    ]);
  } catch {
    // Non-fatal: show empty state
  }

  const billingTrend = await billingTrendRead;
  const trend = billingTrend.state === "ok" ? billingTrend.data : null;

  return (
    <div className="space-y-8">
      <div>
        <PageHeader
          title="Profitability"
          description="Team utilization, matter profitability, and client profitability across your organization"
        />
        {/* Phase 87 ADR-340 / OQ #1: the cost leg still reads Expense until the follow-up lands. */}
        <p data-testid="profitability-cost-note" className="text-muted-foreground mt-1 text-xs">
          Costs reflect logged expenses only; disbursements are shown as fee-note lines
        </p>
      </div>

      {/* The last 12 SA months, so it stays outside the current-month empty state below. */}
      <BillingTrendChart trend={trend} failed={billingTrend.state === "error"} />

      {utilization.members.length === 0 && profitability.matters.length === 0 ? (
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
          billingTrend={trend}
        />
      )}
    </div>
  );
}
