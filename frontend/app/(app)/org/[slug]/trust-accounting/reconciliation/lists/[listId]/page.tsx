import Link from "next/link";
import { notFound } from "next/navigation";
import { ArrowLeft, Lock } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@b2mash/ui/card";
import { PageHeader } from "@b2mash/ui/page-header";
import { StatCard } from "@b2mash/ui/stat-card";
import { ApiError } from "@/lib/api";
import { fetchMonthlyList } from "@/app/(app)/org/[slug]/trust-accounting/reconciliation/actions";
import { formatCurrency, formatDateTime, formatLocalDate } from "@/lib/format";
import { integrityFlagLabel, integritySeverityLabel } from "@/lib/trust-integrity";
import { CREDITOR_LINE_KIND_LABELS, type CreditorLine, type MonthlyList } from "@/lib/types";

/** Currency of the trust books (the reconciliation pages use ZAR throughout). */
const CURRENCY = "ZAR";

/**
 * One locked Rule 54.15 monthly trust creditor list (Phase 92 650A, ADR-382): what was compared and
 * frozen when the month's reconciliation completed — the totals, every creditor by name, the flags
 * — and, separately, any entry made after the lock and dated in the month. Read-only. The
 * reconciliation layout gates the trust module and VIEW_TRUST.
 */
export default async function MonthlyListPage({
  params,
}: {
  params: Promise<{ slug: string; listId: string }>;
}) {
  const { slug, listId } = await params;
  const backHref = `/org/${slug}/trust-accounting/reconciliation`;

  let list: MonthlyList | null = null;
  try {
    list = await fetchMonthlyList(listId);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      notFound();
    }
  }

  if (!list) {
    return (
      <div className="space-y-8">
        <PageHeader title="Trust creditors list" />
        <Card>
          <CardContent className="py-8 text-center">
            <p className="text-sm text-red-600 dark:text-red-400" data-testid="monthly-list-error">
              Failed to load the monthly list. Please try again.
            </p>
          </CardContent>
        </Card>
      </div>
    );
  }

  const adjustments = list.adjustmentsSinceLock;

  return (
    <div className="space-y-8" data-testid="monthly-list-page">
      <PageHeader
        title={`Trust creditors list — ${formatLocalDate(list.periodEnd)}`}
        description={`${list.trustAccountName ?? "Trust account"} · Locked ${formatDateTime(list.lockedAt)}${
          list.lockedByName ? ` by ${list.lockedByName}` : ""
        } · Rule 54.15`}
        actions={
          <Button asChild variant="outline" size="sm">
            <Link href={backHref}>
              <ArrowLeft className="mr-1 size-4" />
              Reconciliation
            </Link>
          </Button>
        }
      />

      <div className="@container">
        <div className="grid grid-cols-1 gap-4 @xl:grid-cols-2 @4xl:grid-cols-4">
          <StatCard
            label="Adjusted bank balance"
            value={formatCurrency(list.adjustedBankBalance, CURRENCY)}
          />
          <StatCard label="Cashbook" value={formatCurrency(list.cashbookBalance, CURRENCY)} />
          <StatCard label="Trust creditors" value={formatCurrency(list.creditorTotal, CURRENCY)} />
          <StatCard label="Difference" value={formatCurrency(list.difference, CURRENCY)} />
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Lock className="size-4 text-teal-600 dark:text-teal-400" />
            Trust creditors as at {formatLocalDate(list.periodEnd)}
          </CardTitle>
          <CardDescription>
            Frozen when the reconciliation completed. Trust cash, estates and other trust assets are
            not recorded in Kazi and are not included in this comparison.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-sm" data-testid="monthly-list-creditors">
              <thead>
                <tr className="border-b border-slate-200 dark:border-slate-700">
                  <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                    Trust creditor
                  </th>
                  <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                    Kind
                  </th>
                  <th className="pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                    Balance
                  </th>
                </tr>
              </thead>
              <tbody>
                {list.lines.map((line, index) => (
                  <CreditorRows key={`${line.kind}-${index}`} line={line} />
                ))}
              </tbody>
              <tfoot>
                <tr className="border-t border-slate-200 dark:border-slate-700">
                  <td
                    colSpan={2}
                    className="pt-3 pr-4 font-medium text-slate-950 dark:text-slate-50"
                  >
                    Total trust creditors
                  </td>
                  <td className="pt-3 text-right font-mono font-medium text-slate-950 tabular-nums dark:text-slate-50">
                    {formatCurrency(list.creditorTotal, CURRENCY)}
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
        </CardContent>
      </Card>

      {list.integrityFlags.length > 0 && (
        <Card data-testid="monthly-list-flags">
          <CardHeader>
            <CardTitle>Flags at completion</CardTitle>
            <CardDescription>
              What the trust books check showed when the list was locked
            </CardDescription>
          </CardHeader>
          <CardContent>
            <ul className="space-y-3">
              {list.integrityFlags.map((flag, index) => (
                <li key={`${flag.code}-${index}`} className="text-sm">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-slate-950 dark:text-slate-50">
                      {integrityFlagLabel(flag.code)}
                    </span>
                    <Badge variant={flag.severity === "BLOCKING" ? "destructive" : "warning"}>
                      {integritySeverityLabel(flag.severity)}
                    </Badge>
                  </div>
                  <p className="mt-1 text-slate-600 dark:text-slate-400">{flag.message}</p>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      <Card data-testid="monthly-list-after-lock">
        <CardHeader>
          <CardTitle>Entered after this list was locked</CardTitle>
          <CardDescription>
            Entries dated on or before {formatLocalDate(list.periodEnd)} and recorded after the list
            was locked. They are part of the books, not of this list.
          </CardDescription>
        </CardHeader>
        <CardContent>
          {adjustments.entries.length === 0 ? (
            <p className="py-4 text-center text-sm text-slate-500 dark:text-slate-400">
              Nothing has been entered after this list was locked.
            </p>
          ) : (
            <div className="space-y-4">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 dark:border-slate-700">
                      <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        Date
                      </th>
                      <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        Entered
                      </th>
                      <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        Type
                      </th>
                      <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        Client
                      </th>
                      <th className="pr-4 pb-3 text-left font-medium text-slate-500 dark:text-slate-400">
                        Reference
                      </th>
                      <th className="pb-3 text-right font-medium text-slate-500 dark:text-slate-400">
                        Cashbook
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {adjustments.entries.map((entry) => (
                      <tr
                        key={entry.transactionId}
                        className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                        data-testid={`after-lock-entry-${entry.transactionId}`}
                      >
                        <td className="py-3 pr-4 text-slate-700 dark:text-slate-300">
                          {formatLocalDate(entry.transactionDate)}
                        </td>
                        <td className="py-3 pr-4 text-slate-700 dark:text-slate-300">
                          {formatDateTime(entry.postedAt)}
                        </td>
                        <td className="py-3 pr-4 text-slate-950 dark:text-slate-50">
                          {entry.typeLabel}
                        </td>
                        <td className="py-3 pr-4 text-slate-700 dark:text-slate-300">
                          {entry.clientName ?? "—"}
                        </td>
                        <td className="py-3 pr-4 text-slate-700 dark:text-slate-300">
                          {entry.reference ?? "—"}
                        </td>
                        <td className="py-3 text-right font-mono text-slate-700 tabular-nums dark:text-slate-300">
                          {formatCurrency(entry.cashbookEffect, CURRENCY)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <p className="text-sm text-slate-700 dark:text-slate-300">
                Trust creditors as at {formatLocalDate(list.periodEnd)} are now{" "}
                <span className="font-mono tabular-nums">
                  {formatCurrency(adjustments.liveCreditorTotal, CURRENCY)}
                </span>
                , a change of{" "}
                <span className="font-mono tabular-nums">
                  {formatCurrency(adjustments.changeInCreditorTotal, CURRENCY)}
                </span>{" "}
                from the locked list.
              </p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

/** One creditor, its per-matter breakdown beneath; an unidentified deposit shows when received. */
function CreditorRows({ line }: { line: CreditorLine }) {
  const name =
    line.kind === "UNIDENTIFIED"
      ? `${line.bankDescription ?? "Unidentified deposit"}${
          line.receivedDate ? ` · received ${formatLocalDate(line.receivedDate)}` : ""
        }`
      : (line.name ?? CREDITOR_LINE_KIND_LABELS[line.kind]);
  return (
    <>
      <tr className="border-b border-slate-100 dark:border-slate-800">
        <td className="py-3 pr-4 font-medium text-slate-950 dark:text-slate-50">{name}</td>
        <td className="py-3 pr-4 text-slate-600 dark:text-slate-400">
          {CREDITOR_LINE_KIND_LABELS[line.kind]}
        </td>
        <td className="py-3 text-right font-mono text-slate-700 tabular-nums dark:text-slate-300">
          {formatCurrency(line.balance, CURRENCY)}
        </td>
      </tr>
      {line.matters.length > 0 &&
        line.matters.map((matter, index) => (
          <tr
            key={`${matter.matterId ?? "none"}-${index}`}
            className="border-b border-slate-100 dark:border-slate-800"
            data-testid="monthly-list-matter"
          >
            <td className="py-2 pr-4 pl-6 text-slate-600 dark:text-slate-400">
              {matter.name ?? "Not linked to a matter"}
            </td>
            <td className="py-2 pr-4 text-slate-500 dark:text-slate-400">Matter</td>
            <td className="py-2 text-right font-mono text-slate-600 tabular-nums dark:text-slate-400">
              {formatCurrency(matter.balance, CURRENCY)}
            </td>
          </tr>
        ))}
    </>
  );
}
