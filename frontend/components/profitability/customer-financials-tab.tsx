import { TrendingUp } from "lucide-react";
import { cn } from "@/lib/utils";
import { EmptyState } from "@/components/empty-state";
import { formatCurrency, formatCurrencySafe } from "@/lib/format";
import {
  Table,
  TableHeader,
  TableBody,
  TableHead,
  TableRow,
  TableCell,
} from "@/components/ui/table";
import type {
  CustomerProfitabilityResponse,
  OrgProfitabilityResponse,
  ProjectProfitabilitySummary,
} from "@/lib/types";

interface CustomerFinancialsTabProps {
  profitability: CustomerProfitabilityResponse | null;
  projectBreakdown: OrgProfitabilityResponse | null;
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
    <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-slate-950">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
        {label}
      </p>
      <p className={valueClassName ?? "text-slate-950 dark:text-slate-50"}>
        <span className="font-display text-2xl">{value}</span>
      </p>
    </div>
  );
}

function marginClassName(value: number | null): string | undefined {
  if (value == null) return undefined;
  return cn(
    value >= 0
      ? "text-green-600 dark:text-green-400"
      : "text-red-600 dark:text-red-400",
  );
}

function ProjectBreakdownTable({
  projects,
}: {
  projects: ProjectProfitabilitySummary[];
}) {
  if (projects.length === 0) return null;

  return (
    <div className="space-y-3">
      <h4 className="text-sm font-medium text-slate-700 dark:text-slate-300">
        Per-Project Breakdown
      </h4>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Project</TableHead>
            <TableHead>Currency</TableHead>
            <TableHead className="text-right">Hours</TableHead>
            <TableHead className="text-right">Revenue</TableHead>
            <TableHead className="text-right">Cost</TableHead>
            <TableHead className="text-right">Margin</TableHead>
            <TableHead className="text-right">Margin %</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {projects.map((p) => (
            <TableRow key={`${p.projectId}-${p.currency}`}>
              <TableCell className="font-medium text-slate-950 dark:text-slate-50">
                {p.projectName}
              </TableCell>
              <TableCell className="text-slate-600 dark:text-slate-400">
                {p.currency}
              </TableCell>
              <TableCell className="text-right text-slate-600 dark:text-slate-400">
                {p.billableHours.toFixed(1)}h
              </TableCell>
              <TableCell className="text-right text-slate-950 dark:text-slate-50">
                {formatCurrency(p.billableValue, p.currency)}
              </TableCell>
              <TableCell className="text-right text-slate-600 dark:text-slate-400">
                {formatCurrencySafe(p.costValue, p.currency)}
              </TableCell>
              <TableCell
                className={cn(
                  "text-right",
                  marginClassName(p.margin) ??
                    "text-slate-600 dark:text-slate-400",
                )}
              >
                {p.margin != null
                  ? formatCurrency(p.margin, p.currency)
                  : "N/A"}
              </TableCell>
              <TableCell
                className={cn(
                  "text-right",
                  marginClassName(p.marginPercent) ??
                    "text-slate-600 dark:text-slate-400",
                )}
              >
                {p.marginPercent != null
                  ? `${p.marginPercent.toFixed(1)}%`
                  : "N/A"}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

export function CustomerFinancialsTab({
  profitability,
  projectBreakdown,
}: CustomerFinancialsTabProps) {
  const hasProfitability =
    profitability && profitability.currencies.length > 0;

  if (!hasProfitability) {
    return (
      <EmptyState
        icon={TrendingUp}
        title="No financial data yet"
        description="Track billable time and set up billing rates to see customer profitability here."
      />
    );
  }

  return (
    <div className="space-y-6">
      <h3 className="font-display text-lg text-slate-950 dark:text-slate-50">
        Customer Profitability
      </h3>
      {profitability.currencies.map((curr) => (
        <div key={curr.currency} className="space-y-3">
          {profitability.currencies.length > 1 && (
            <h4 className="text-sm font-medium text-slate-700 dark:text-slate-300">
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
              valueClassName={marginClassName(curr.margin)}
            />
            <StatCard
              label="Margin %"
              value={
                curr.marginPercent != null
                  ? `${curr.marginPercent.toFixed(1)}%`
                  : "N/A"
              }
              valueClassName={marginClassName(curr.marginPercent)}
            />
          </div>
        </div>
      ))}

      {/* Per-project breakdown table */}
      {projectBreakdown && projectBreakdown.projects.length > 0 && (
        <ProjectBreakdownTable projects={projectBreakdown.projects} />
      )}
    </div>
  );
}
