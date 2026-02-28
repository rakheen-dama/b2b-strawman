"use client";

import * as React from "react";
import type { BudgetStatusResponse } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { StatusBadge } from "@/components/ui/status-badge";
import { formatDuration, formatCurrencySafe } from "@/lib/format";
import { cn } from "@/lib/utils";
import { Gauge } from "lucide-react";

interface BudgetTabProps {
  budgetStatus: BudgetStatusResponse | null;
}

export function BudgetTab({ budgetStatus }: BudgetTabProps) {
  if (!budgetStatus) {
    return (
      <div className="flex flex-col items-center py-16 text-center">
        <Gauge className="size-12 text-slate-300" />
        <h3 className="mt-4 font-display text-lg text-slate-900">
          No budget configured
        </h3>
        <p className="mt-1 text-sm text-slate-500">
          Configure a budget to track hours and spending against targets.
        </p>
      </div>
    );
  }

  const hoursPct = Math.round(budgetStatus.hoursConsumedPct);
  const amountPct = Math.round(budgetStatus.amountConsumedPct);

  return (
    <div className="space-y-6">
      {/* Overall status */}
      <div className="flex items-center gap-3">
        <StatusBadge status={budgetStatus.overallStatus} />
        {budgetStatus.notes && (
          <span className="text-sm text-slate-500">{budgetStatus.notes}</span>
        )}
      </div>

      <div className="grid gap-6 sm:grid-cols-2">
        {/* Hours gauge */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Hours Budget</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-end justify-between">
              <span className="font-mono text-3xl font-semibold tabular-nums text-slate-900">
                {hoursPct}%
              </span>
              <StatusBadge status={budgetStatus.hoursStatus} />
            </div>
            <Progress
              value={Math.min(hoursPct, 100)}
              className={cn(
                "h-2",
                budgetStatus.hoursStatus === "OVER_BUDGET" && "[&>div]:bg-red-500"
              )}
            />
            <div className="flex justify-between text-sm text-slate-500">
              <span>
                {formatDuration(budgetStatus.hoursConsumed * 60)} consumed
              </span>
              <span>
                {budgetStatus.budgetHours
                  ? `${budgetStatus.budgetHours}h budget`
                  : "No hours budget"}
              </span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-slate-500">Remaining</span>
              <span className="font-mono tabular-nums text-slate-900">
                {formatDuration(Math.max(0, budgetStatus.hoursRemaining) * 60)}
              </span>
            </div>
          </CardContent>
        </Card>

        {/* Amount gauge */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Amount Budget</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-end justify-between">
              <span className="font-mono text-3xl font-semibold tabular-nums text-slate-900">
                {amountPct}%
              </span>
              <StatusBadge status={budgetStatus.amountStatus} />
            </div>
            <Progress
              value={Math.min(amountPct, 100)}
              className={cn(
                "h-2",
                budgetStatus.amountStatus === "OVER_BUDGET" && "[&>div]:bg-red-500"
              )}
            />
            <div className="flex justify-between text-sm text-slate-500">
              <span>
                {formatCurrencySafe(
                  budgetStatus.amountConsumed,
                  budgetStatus.budgetCurrency
                )}{" "}
                consumed
              </span>
              <span>
                {formatCurrencySafe(
                  budgetStatus.budgetAmount,
                  budgetStatus.budgetCurrency
                )}{" "}
                budget
              </span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-slate-500">Remaining</span>
              <span className="font-mono tabular-nums text-slate-900">
                {formatCurrencySafe(
                  Math.max(0, budgetStatus.amountRemaining),
                  budgetStatus.budgetCurrency
                )}
              </span>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Threshold config display */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex justify-between text-sm">
            <span className="text-slate-500">Alert Threshold</span>
            <span className="font-mono tabular-nums text-slate-900">
              {budgetStatus.alertThresholdPct}%
            </span>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
