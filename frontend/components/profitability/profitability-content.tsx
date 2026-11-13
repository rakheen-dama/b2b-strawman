import { BillingTrendChart } from "@/components/profitability/billing-trend-chart";
import { UtilizationTable } from "@/components/profitability/utilization-table";
import { ProjectProfitabilityTable } from "@/components/profitability/project-profitability-table";
import { CustomerProfitabilitySection } from "@/components/profitability/customer-profitability-section";
import type {
  BillingTrendResponse,
  UtilizationResponse,
  OrgProfitabilityResponse,
} from "@/lib/types";

interface ProfitabilityContentProps {
  initialUtilization: UtilizationResponse;
  initialProfitability: OrgProfitabilityResponse;
  initialFrom: string;
  initialTo: string;
  /** Last 12 SA months; null = absent (403/404) or failed (see billingTrendFailed). Plain data. */
  billingTrend?: BillingTrendResponse | null;
  billingTrendFailed?: boolean;
}

export function ProfitabilityContent({
  initialUtilization,
  initialProfitability,
  initialFrom,
  initialTo,
  billingTrend = null,
  billingTrendFailed = false,
}: ProfitabilityContentProps) {
  return (
    <div className="space-y-8">
      <BillingTrendChart trend={billingTrend} failed={billingTrendFailed} />

      <UtilizationTable
        initialData={initialUtilization}
        initialFrom={initialFrom}
        initialTo={initialTo}
      />

      <ProjectProfitabilityTable
        initialData={initialProfitability}
        initialFrom={initialFrom}
        initialTo={initialTo}
        defaultCurrency={billingTrend?.currency ?? null}
      />

      <CustomerProfitabilitySection
        initialData={initialProfitability}
        initialFrom={initialFrom}
        initialTo={initialTo}
      />
    </div>
  );
}
