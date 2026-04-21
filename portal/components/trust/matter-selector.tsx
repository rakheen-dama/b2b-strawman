"use client";

import Link from "next/link";
import { Scale, Calendar } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatCurrency, formatRelativeDate } from "@/lib/format";
import {
  formatMatterLabel,
  type PortalTrustMatterSummary,
} from "@/lib/api/trust";

interface MatterSelectorProps {
  matters: PortalTrustMatterSummary[];
  /** Currency code (ISO 4217). Defaults to ZAR — all local trust accounts are ZAR. */
  currency?: string;
}

/**
 * Lists the matters with trust activity as clickable cards. Each card shows
 * the matter label, current balance, and a relative "last activity" hint.
 * Renders an empty state when no matters have activity.
 */
export function MatterSelector({
  matters,
  currency = "ZAR",
}: MatterSelectorProps) {
  if (matters.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-slate-200 bg-white py-16 text-center">
        <Scale className="mb-4 size-12 text-slate-300" aria-hidden="true" />
        <p className="text-lg font-medium text-slate-600">
          No trust activity on your matters
        </p>
        <p className="mt-1 text-sm text-slate-500">
          Balances and transactions will appear here once your firm records
          them.
        </p>
      </div>
    );
  }

  return (
    <div
      className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
      aria-label="Matters with trust activity"
    >
      {matters.map((matter) => (
        <Link
          key={matter.matterId}
          href={`/trust/${matter.matterId}`}
          className="block rounded-xl focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500"
        >
          <Card className="transition-shadow hover:shadow-md">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base text-slate-900">
                <Scale className="size-4 text-slate-400" aria-hidden="true" />
                {formatMatterLabel(matter.matterId)}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="font-mono text-2xl font-semibold text-slate-900 tabular-nums">
                {formatCurrency(matter.currentBalance, currency)}
              </p>
              <div className="flex items-center gap-1 text-xs text-slate-500">
                <Calendar className="size-3.5" aria-hidden="true" />
                <span>
                  Last activity{" "}
                  {formatRelativeDate(matter.lastTransactionAt) || "recently"}
                </span>
              </div>
            </CardContent>
          </Card>
        </Link>
      ))}
    </div>
  );
}
