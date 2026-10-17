import { UtilizationTable } from "@/components/profitability/utilization-table";
import { ProjectProfitabilityTable } from "@/components/profitability/project-profitability-table";
import { CustomerProfitabilitySection } from "@/components/profitability/customer-profitability-section";
import type { UtilizationResponse, OrgProfitabilityResponse } from "@/lib/types";

interface ProfitabilityContentProps {
  initialUtilization: UtilizationResponse;
  initialProfitability: OrgProfitabilityResponse;
  initialFrom: string;
  initialTo: string;
}

export function ProfitabilityContent({
  initialUtilization,
  initialProfitability,
  initialFrom,
  initialTo,
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
      />

      <CustomerProfitabilitySection
        initialData={initialProfitability}
        initialFrom={initialFrom}
        initialTo={initialTo}
      />
    </div>
  );
}
