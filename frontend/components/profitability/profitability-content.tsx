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
  /**
   * Last 12 SA months (the page renders its card); here only the ranking chart's default currency.
   * null = absent (403/404) or failed. Plain data.
   */
  billingTrend: BillingTrendResponse | null;
}

export function ProfitabilityContent({
  initialUtilization,
  initialProfitability,
  initialFrom,
  initialTo,
  billingTrend,
}: ProfitabilityContentProps) {
  return (
    <div className="space-y-8">
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
