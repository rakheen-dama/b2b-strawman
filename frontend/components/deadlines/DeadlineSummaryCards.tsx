"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { DeadlineSummary } from "@/lib/types";

interface DeadlineSummaryCardsProps {
  summaries: DeadlineSummary[];
}

function formatCategoryLabel(category: string): string {
  if (category === "vat") return "VAT";
  return category.charAt(0).toUpperCase() + category.slice(1);
}

interface CategoryTotals {
  total: number;
  filed: number;
  pending: number;
  overdue: number;
}

export function DeadlineSummaryCards({ summaries }: DeadlineSummaryCardsProps) {
  if (summaries.length === 0) {
    return (
      <Card>
        <CardContent className="py-8 text-center">
          <p className="text-sm text-slate-500 dark:text-slate-400">
            No deadline data for this period.
          </p>
        </CardContent>
      </Card>
    );
  }

  const grouped = summaries.reduce<Record<string, CategoryTotals>>((acc, summary) => {
    const cat = summary.category;
    if (!acc[cat]) {
      acc[cat] = { total: 0, filed: 0, pending: 0, overdue: 0 };
    }
    acc[cat].total += summary.total;
    acc[cat].filed += summary.filed;
    acc[cat].pending += summary.pending;
    acc[cat].overdue += summary.overdue;
    return acc;
  }, {});

  const categories = Object.entries(grouped);

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      {categories.map(([category, totals]) => (
        <Card key={category}>
          <CardHeader>
            <CardTitle>{formatCategoryLabel(category)}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-baseline gap-4">
              <div className="text-center">
                <p className="font-mono text-2xl font-semibold text-slate-950 tabular-nums dark:text-slate-50">
                  {totals.total}
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400">Total</p>
              </div>
              <div className="text-center">
                <p className="font-mono text-2xl font-semibold text-teal-600 tabular-nums dark:text-teal-400">
                  {totals.filed}
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400">Filed</p>
              </div>
              <div className="text-center">
                <p className="font-mono text-2xl font-semibold text-amber-600 tabular-nums dark:text-amber-400">
                  {totals.pending}
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400">Pending</p>
              </div>
              <div className="text-center">
                <p
                  className={`font-mono text-2xl font-semibold tabular-nums ${
                    totals.overdue > 0
                      ? "text-red-600 dark:text-red-400"
                      : "text-slate-300 dark:text-slate-600"
                  }`}
                >
                  {totals.overdue}
                </p>
                <p className="text-xs text-slate-500 dark:text-slate-400">Overdue</p>
              </div>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
