import { TrendingUp } from "lucide-react";
import { cn } from "@/lib/utils";
import { EmptyState } from "@/components/empty-state";
import { formatCurrency, formatCurrencySafe } from "@/lib/format";
import type { ProjectProfitabilityResponse } from "@/lib/types";

interface ProjectFinancialsTabProps {
  profitability: ProjectProfitabilityResponse | null;
}

function StatCard({
  label,
  value,
  valueClassName,
}: {
  label: string;
  value: string;
  valueClassName?: string;
}) {
  return (
    <div className="rounded-lg border border-olive-200 bg-white p-4 dark:border-olive-800 dark:bg-olive-950">
      <p className="text-xs font-medium uppercase tracking-wide text-olive-600 dark:text-olive-400">
        {label}
      </p>
      <p className={valueClassName ?? "text-olive-950 dark:text-olive-50"}>
        <span className="font-display text-2xl">{value}</span>
      </p>
    </div>
  );
}

export function ProjectFinancialsTab({
  profitability,
}: ProjectFinancialsTabProps) {
  const hasProfitability =
    profitability && profitability.currencies.length > 0;

  if (!hasProfitability) {
    return (
      <EmptyState
        icon={TrendingUp}
        title="No financial data yet"
        description="Track billable time and set up billing rates to see project profitability here."
      />
    );
  }

  return (
    <div className="space-y-6">
      <h3 className="font-display text-lg text-olive-950 dark:text-olive-50">
        Project Profitability
      </h3>
      {profitability.currencies.map((curr) => (
        <div key={curr.currency} className="space-y-3">
          {profitability.currencies.length > 1 && (
            <h4 className="text-sm font-medium text-olive-700 dark:text-olive-300">
              {curr.currency}
            </h4>
          )}
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            <StatCard
              label="Billable Hours"
              value={`${curr.totalBillableHours.toFixed(1)}h`}
            />
            <StatCard
              label="Revenue"
              value={formatCurrency(curr.billableValue, curr.currency)}
            />
            <StatCard
              label="Cost"
              value={formatCurrencySafe(curr.costValue, curr.currency)}
            />
            <StatCard
              label="Margin"
              value={
                curr.margin != null
                  ? formatCurrency(curr.margin, curr.currency)
                  : "N/A"
              }
              valueClassName={
                curr.margin != null
                  ? cn(
                      curr.margin >= 0
                        ? "text-green-600 dark:text-green-400"
                        : "text-red-600 dark:text-red-400",
                    )
                  : undefined
              }
            />
            <StatCard
              label="Margin %"
              value={
                curr.marginPercent != null
                  ? `${curr.marginPercent.toFixed(1)}%`
                  : "N/A"
              }
              valueClassName={
                curr.marginPercent != null
                  ? cn(
                      curr.marginPercent >= 0
                        ? "text-green-600 dark:text-green-400"
                        : "text-red-600 dark:text-red-400",
                    )
                  : undefined
              }
            />
          </div>
        </div>
      ))}
    </div>
  );
}
