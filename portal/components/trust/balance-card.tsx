"use client";

import Link from "next/link";
import { Scale } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { formatCurrency, formatDate } from "@b2mash/shared/format";
import { totalHeldInTrust } from "@/lib/api/trust";

const GENERAL_LABEL = "In the firm's trust account";
const INVESTED_LABEL = "Invested on your instruction (section 86(4))";

interface BalanceCardProps {
  /** The portal matter name (PortalTrustMatterSummary.matterName); null → "Matter". */
  matterName: string | null;
  /** General (client ledger) balance — what sits in the firm's trust account for the client. */
  currentBalance: number;
  /**
   * Section 86(4) invested balance (Phase 92 648D); the breakdown shows only when non-zero. Absent
   * from a pre-648D backend's response — treated as nothing invested.
   */
  investedBalance?: number | null;
  lastTransactionAt: string;
  /** When present, the matter name/label wraps in a link to the matter detail page. */
  href?: string;
  /** Currency code (ISO 4217) — defaults to ZAR. */
  currency?: string;
}

/**
 * Trust balance summary for a single matter: the client's total held in trust
 * (general + section 86(4) invested — a placement does not move it, ADR-379) as
 * a large ZAR figure, and — only when money is invested — the two parts under
 * it. Then the "as of" date and the matter name. Full width below `md`.
 */
export function BalanceCard({
  matterName,
  currentBalance,
  investedBalance,
  lastTransactionAt,
  href,
  currency = "ZAR",
}: BalanceCardProps) {
  const label = matterName ?? "Matter";
  const invested = investedBalance ?? 0;
  const title = (
    <span className="flex items-center gap-2 text-sm font-medium text-slate-600">
      <Scale className="size-4" aria-hidden="true" />
      Held in trust
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
          {formatCurrency(
            totalHeldInTrust({ currentBalance, investedBalance: invested }),
            currency,
          )}
        </p>
        {invested !== 0 && (
          <dl className="space-y-1 text-sm">
            <div className="flex items-baseline justify-between gap-4">
              <dt className="text-slate-600">{GENERAL_LABEL}</dt>
              <dd className="font-mono text-slate-900 tabular-nums">
                {formatCurrency(currentBalance, currency)}
              </dd>
            </div>
            <div className="flex items-baseline justify-between gap-4">
              <dt className="text-slate-600">{INVESTED_LABEL}</dt>
              <dd className="font-mono text-slate-900 tabular-nums">
                {formatCurrency(invested, currency)}
              </dd>
            </div>
          </dl>
        )}
        <p className="text-xs text-slate-500">
          As of {formatDate(lastTransactionAt)}
        </p>
        <p className="text-sm font-medium text-slate-700">{label}</p>
      </CardContent>
    </Card>
  );
}
