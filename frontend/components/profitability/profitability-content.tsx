"use client";

import { useState, useTransition } from "react";
import { Switch } from "@/components/ui/switch";
import { UtilizationTable } from "@/components/profitability/utilization-table";
import { ProjectProfitabilityTable } from "@/components/profitability/project-profitability-table";
import { CustomerProfitabilitySection } from "@/components/profitability/customer-profitability-section";
import { getOrgProfitability } from "@/app/(app)/org/[slug]/profitability/actions";
import type {
  UtilizationResponse,
  OrgProfitabilityResponse,
} from "@/lib/types";

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
  const [includeProjections, setIncludeProjections] = useState(false);
  const [profitability, setProfitability] =
    useState<OrgProfitabilityResponse>(initialProfitability);
  const [isPending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);

  function handleToggle(checked: boolean) {
    const previousValue = includeProjections;
    setIncludeProjections(checked);
    setError(null);
    startTransition(async () => {
      const result = await getOrgProfitability(
        initialFrom,
        initialTo,
        undefined,
        checked,
      );
      if (result.data) {
        setProfitability(result.data);
      } else {
        setIncludeProjections(previousValue);
        setError(result.error ?? "Failed to load profitability data.");
      }
    });
  }

  return (
    <div className="space-y-8">
      <div className="flex items-center gap-3">
        <Switch
          id="projections-toggle"
          checked={includeProjections}
          onCheckedChange={handleToggle}
        />
        <label
          htmlFor="projections-toggle"
          className="text-sm font-medium text-slate-700 dark:text-slate-300"
        >
          Include Projections
        </label>
        {isPending && (
          <span className="text-xs text-slate-400 dark:text-slate-500">
            Loading...
          </span>
        )}
        {error && (
          <span className="text-xs text-red-600 dark:text-red-400">
            {error}
          </span>
        )}
      </div>

      <UtilizationTable
        initialData={initialUtilization}
        initialFrom={initialFrom}
        initialTo={initialTo}
      />

      <div className={includeProjections ? "relative" : undefined}>
        {includeProjections && (
          <p className="mb-2 text-xs italic text-slate-500 dark:text-slate-400">
            Projected values included
          </p>
        )}
        <ProjectProfitabilityTable
          initialData={profitability}
          initialFrom={initialFrom}
          initialTo={initialTo}
        />
      </div>

      <CustomerProfitabilitySection
        initialData={profitability}
        initialFrom={initialFrom}
        initialTo={initialTo}
      />
    </div>
  );
}
