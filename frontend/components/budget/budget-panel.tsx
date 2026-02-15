"use client";

import { Wallet, Pencil, Trash2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { EmptyState } from "@/components/empty-state";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { BudgetConfigDialog } from "@/components/budget/budget-config-dialog";
import { DeleteBudgetDialog } from "@/components/budget/delete-budget-dialog";
import { formatCurrency, formatDuration } from "@/lib/format";
import type { BudgetStatus, BudgetStatusResponse } from "@/lib/types";

interface BudgetPanelProps {
  slug: string;
  projectId: string;
  budget: BudgetStatusResponse | null;
  canManage: boolean;
  defaultCurrency: string;
}

const STATUS_BADGE: Record<
  BudgetStatus,
  { label: string; variant: "success" | "warning" | "destructive" }
> = {
  ON_TRACK: { label: "On Track", variant: "success" },
  AT_RISK: { label: "At Risk", variant: "warning" },
  OVER_BUDGET: { label: "Over Budget", variant: "destructive" },
};

function BudgetProgressBar({
  value,
  status,
}: {
  value: number;
  status: BudgetStatus;
}) {
  const clamped = Math.min(100, Math.max(0, value));
  const barColor =
    status === "OVER_BUDGET"
      ? "bg-red-500"
      : status === "AT_RISK"
        ? "bg-amber-500"
        : "bg-green-500";

  return (
    <div
      className="relative h-2.5 w-full overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800"
      role="progressbar"
      aria-valuenow={Math.round(value)}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <div
        className={cn("h-full rounded-full transition-all", barColor)}
        style={{ width: `${clamped}%` }}
      />
    </div>
  );
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

export function BudgetPanel({
  slug,
  projectId,
  budget,
  canManage,
  defaultCurrency,
}: BudgetPanelProps) {
  if (!budget) {
    return (
      <EmptyState
        icon={Wallet}
        title="No budget set"
        description="Configure a budget to track hours and costs against targets."
        action={
          canManage ? (
            <BudgetConfigDialog
              slug={slug}
              projectId={projectId}
              existing={null}
              defaultCurrency={defaultCurrency}
            >
              <Button>Set Budget</Button>
            </BudgetConfigDialog>
          ) : undefined
        }
      />
    );
  }

  const statusBadge = STATUS_BADGE[budget.overallStatus];
  const hasHours = budget.budgetHours !== null;
  const hasAmount = budget.budgetAmount !== null;

  return (
    <div className="space-y-6">
      {/* Header with status and actions */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h3 className="font-display text-lg text-slate-950 dark:text-slate-50">
            Budget Status
          </h3>
          <Badge variant={statusBadge.variant}>{statusBadge.label}</Badge>
        </div>
        {canManage && (
          <div className="flex gap-2">
            <BudgetConfigDialog
              slug={slug}
              projectId={projectId}
              existing={budget}
              defaultCurrency={defaultCurrency}
            >
              <Button variant="outline" size="sm">
                <Pencil className="mr-1.5 size-4" />
                Edit
              </Button>
            </BudgetConfigDialog>
            <DeleteBudgetDialog slug={slug} projectId={projectId}>
              <Button
                variant="ghost"
                size="sm"
                className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
              >
                <Trash2 className="mr-1.5 size-4" />
                Delete
              </Button>
            </DeleteBudgetDialog>
          </div>
        )}
      </div>

      {/* Hours budget section */}
      {hasHours && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-medium text-slate-700 dark:text-slate-300">
              Hours
            </h4>
            <Badge variant={STATUS_BADGE[budget.hoursStatus].variant} className="text-xs">
              {STATUS_BADGE[budget.hoursStatus].label}
            </Badge>
          </div>
          <BudgetProgressBar
            value={budget.hoursConsumedPct}
            status={budget.hoursStatus}
          />
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <StatCard
              label="Budget"
              value={formatDuration(budget.budgetHours! * 60)}
            />
            <StatCard
              label="Consumed"
              value={formatDuration(budget.hoursConsumed * 60)}
            />
            <StatCard
              label="Remaining"
              value={formatDuration(
                Math.max(0, budget.hoursRemaining) * 60
              )}
            />
            <StatCard
              label="Used"
              value={`${Math.round(budget.hoursConsumedPct)}%`}
            />
          </div>
        </div>
      )}

      {/* Amount budget section */}
      {hasAmount && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-medium text-slate-700 dark:text-slate-300">
              Amount
            </h4>
            <Badge variant={STATUS_BADGE[budget.amountStatus].variant} className="text-xs">
              {STATUS_BADGE[budget.amountStatus].label}
            </Badge>
          </div>
          <BudgetProgressBar
            value={budget.amountConsumedPct}
            status={budget.amountStatus}
          />
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <StatCard
              label="Budget"
              value={formatCurrency(
                budget.budgetAmount!,
                budget.budgetCurrency!
              )}
            />
            <StatCard
              label="Consumed"
              value={formatCurrency(
                budget.amountConsumed,
                budget.budgetCurrency!
              )}
            />
            <StatCard
              label="Remaining"
              value={formatCurrency(
                Math.max(0, budget.amountRemaining),
                budget.budgetCurrency!
              )}
            />
            <StatCard
              label="Used"
              value={`${Math.round(budget.amountConsumedPct)}%`}
            />
          </div>
        </div>
      )}

      {/* Alert threshold and notes */}
      <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-900/50">
        <p className="text-sm text-slate-600 dark:text-slate-400">
          <span className="font-medium">Alert threshold:</span>{" "}
          {budget.alertThresholdPct}%
        </p>
        {budget.notes && (
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            <span className="font-medium">Notes:</span> {budget.notes}
          </p>
        )}
      </div>
    </div>
  );
}
