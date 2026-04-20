"use client";

import Link from "next/link";
import { Scale } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatCurrency, formatDate } from "@/lib/format";
import { formatMatterLabel } from "@/lib/api/trust";

interface BalanceCardProps {
  matterId: string;
  currentBalance: number;
  lastTransactionAt: string;
  /** When present, the matter name/label wraps in a link to the matter detail page. */
  href?: string;
  /** Currency code (ISO 4217) — defaults to ZAR. */
  currency?: string;
}

/**
 * Trust balance summary for a single matter. Renders a large ZAR figure,
 * "as of" date, and the matter label. Full width below `md` for mobile.
 */
export function BalanceCard({
  matterId,
  currentBalance,
  lastTransactionAt,
  href,
  currency = "ZAR",
}: BalanceCardProps) {
  const label = formatMatterLabel(matterId);
  const title = (
    <span className="flex items-center gap-2 text-sm font-medium text-slate-600">
      <Scale className="size-4" aria-hidden="true" />
      Trust balance
    </span>
  );

  return (
    <Card className="w-full md:w-auto">
      <CardHeader>
        <CardTitle>
          {href ? (
            <Link
              href={href}
              className="text-teal-600 hover:text-teal-700 hover:underline"
            >
              {title}
            </Link>
          ) : (
            title
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-2">
        <p
          className="font-mono text-3xl font-semibold text-slate-900 tabular-nums sm:text-4xl"
          aria-label="Current balance"
        >
          {formatCurrency(currentBalance, currency)}
        </p>
        <p className="text-xs text-slate-500">
          As of {formatDate(lastTransactionAt)}
        </p>
        <p className="text-sm font-medium text-slate-700">{label}</p>
      </CardContent>
    </Card>
  );
}
