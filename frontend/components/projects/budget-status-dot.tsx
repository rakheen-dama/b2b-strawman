import { cn } from "@/lib/utils";
import type { BudgetStatus } from "@/lib/types";

const BUDGET_DOT_COLORS: Record<BudgetStatus, string> = {
  ON_TRACK: "bg-green-500",
  AT_RISK: "bg-amber-500",
  OVER_BUDGET: "bg-red-500",
};

const BUDGET_DOT_LABELS: Record<BudgetStatus, string> = {
  ON_TRACK: "On track",
  AT_RISK: "At risk",
  OVER_BUDGET: "Over budget",
};

export function BudgetStatusDot({ status }: { status: BudgetStatus }) {
  return (
    <span
      className={cn("inline-block size-2 rounded-full", BUDGET_DOT_COLORS[status])}
      title={BUDGET_DOT_LABELS[status]}
      aria-label={`Budget: ${BUDGET_DOT_LABELS[status]}`}
    />
  );
}
