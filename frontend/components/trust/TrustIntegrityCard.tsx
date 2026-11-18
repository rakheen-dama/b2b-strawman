import Link from "next/link";
import { AlertTriangle, CheckCircle2, Landmark, PiggyBank, Users, Wallet } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@b2mash/ui/card";
import { StatCard } from "@b2mash/ui/stat-card";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import {
  FLAG_SUBJECT_KIND_LABELS,
  integrityFlagLabel,
  integrityFlagLink,
  integritySeverityLabel,
  integritySubjectHref,
  isBlockingFlag,
} from "@/lib/trust-integrity";
import type { FirmIntegrityReport, IntegrityFlag, TrustAccount } from "@/lib/types";

// Phase 92 649C — the firm-wide trust integrity check on the trust overview (§16.6.1). Server
// components: plain compositions of the Phase 90 primitives, fed by GET /api/trust-integrity.

interface TrustIntegrityTilesProps {
  report: FirmIntegrityReport | null;
  failed: boolean;
  currency: string;
}

/**
 * The firm-wide balance tiles (ruling D-1): Cashbook = Client ledgers + Other trust creditors +
 * Invested, every trust account as at today (EQ-3; the section 86(3) inter-account terms cancel
 * firm-wide). Other trust creditors = the Fund interest and charges ledger + unidentified deposits
 * + any other non-client creditor; Invested = section 86(4) client investment creditors.
 */
export function TrustIntegrityTiles({ report, failed, currency }: TrustIntegrityTilesProps) {
  if (!report && !failed) return null;
  const state = report ? "ready" : "error";
  const money = (amount: number) => (report ? formatCurrency(amount, currency) : null);
  return (
    <div className="@container">
      <div
        className="grid grid-cols-1 gap-4 @xl:grid-cols-2 @6xl:grid-cols-4"
        data-testid="integrity-tiles"
      >
        <StatCard
          label="Cashbook"
          icon={<Wallet />}
          state={state}
          value={money(report?.cashbook ?? 0)}
          hint={report ? "All trust accounts" : null}
        />
        <StatCard
          label="Client ledgers"
          icon={<Users />}
          state={state}
          value={money(report?.clientLedgers ?? 0)}
          hint={report ? "Money held for clients" : null}
        />
        <StatCard
          label="Other trust creditors"
          icon={<Landmark />}
          state={state}
          value={money(
            report ? report.fundLedger + report.unidentifiedDeposits + report.otherNonClient : 0
          )}
          hint={report ? "Fund interest and charges, and unidentified deposits" : null}
        />
        <StatCard
          label="Invested"
          icon={<PiggyBank />}
          state={state}
          value={money(report?.investmentCreditors ?? 0)}
          hint={report ? "Section 86(4) investments for clients" : null}
        />
      </div>
    </div>
  );
}

interface TrustIntegrityCardProps {
  report: FirmIntegrityReport | null;
  failed: boolean;
  /** The firm's trust accounts, to name each account's flags (the report carries ids only). */
  accounts: TrustAccount[];
  slug: string;
  currency: string;
}

interface ListedFlag {
  key: string;
  accountName: string | null;
  flag: IntegrityFlag;
}

/** Firm-level flags first, then each account's (each already blocking first). */
function listFlags(report: FirmIntegrityReport, accounts: TrustAccount[]): ListedFlag[] {
  const names = new Map(accounts.map((a) => [a.id, a.accountName]));
  const named = report.accounts.length > 1;
  return [
    ...report.flags.map((flag, i) => ({ key: `firm-${flag.code}-${i}`, accountName: null, flag })),
    ...report.accounts.flatMap((account) =>
      account.flags.map((flag, i) => ({
        key: `${account.trustAccountId}-${flag.code}-${i}`,
        accountName: named ? (names.get(account.trustAccountId) ?? "Trust account") : null,
        flag,
      }))
    ),
  ];
}

function FlagItem({
  listed,
  slug,
  currency,
}: {
  listed: ListedFlag;
  slug: string;
  currency: string;
}) {
  const { flag, accountName } = listed;
  const blocking = isBlockingFlag(flag);
  const flagLink = flag.subjects.length === 0 ? integrityFlagLink(slug, flag.code) : null;
  return (
    <li
      className="space-y-1 border-t border-slate-200 pt-3 first:border-t-0 first:pt-0 dark:border-slate-800"
      data-testid={`integrity-flag-${flag.code}`}
    >
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <Badge variant={blocking ? "destructive" : "warning"} className="shrink whitespace-normal">
          {integritySeverityLabel(flag.severity)}
        </Badge>
        <span className="min-w-0 text-sm font-medium break-words text-slate-950 dark:text-slate-50">
          {accountName ? `${accountName}: ` : null}
          {integrityFlagLabel(flag.code)}
        </span>
        {flag.amount !== null ? (
          <span className="ml-auto font-mono text-sm text-slate-950 tabular-nums dark:text-slate-50">
            {formatCurrency(flag.amount, currency)}
          </span>
        ) : null}
      </div>
      <p className="text-muted-foreground text-sm break-words">{flag.message}</p>
      {flag.subjects.length > 0 ? (
        <ul className="space-y-0.5 text-sm">
          {flag.subjects.map((subject, i) => {
            const href = integritySubjectHref(slug, subject);
            const name = subject.name ?? FLAG_SUBJECT_KIND_LABELS[subject.kind] ?? "Trust account";
            return (
              <li
                key={`${subject.kind}-${subject.id ?? i}`}
                className="flex flex-wrap items-baseline justify-between gap-x-4"
              >
                {href ? (
                  <Link
                    href={href}
                    className="min-w-0 break-words text-teal-700 underline-offset-2 hover:underline dark:text-teal-300"
                  >
                    {name}
                  </Link>
                ) : (
                  <span className="min-w-0 break-words text-slate-700 dark:text-slate-300">
                    {name}
                  </span>
                )}
                {subject.amount !== null ? (
                  <span className="font-mono text-slate-700 tabular-nums dark:text-slate-300">
                    {formatCurrency(subject.amount, currency)}
                  </span>
                ) : null}
              </li>
            );
          })}
        </ul>
      ) : null}
      {flagLink ? (
        <Link
          href={flagLink.href}
          className="text-sm text-teal-700 underline-offset-2 hover:underline dark:text-teal-300"
        >
          {flagLink.label}
        </Link>
      ) : null}
    </li>
  );
}

/**
 * The trust integrity card: "Books balance" or the cashbook, trust creditors and their difference,
 * then every flag — its label (never the raw code), severity, sentence, amount and the page where
 * it is acted on. A warning never makes the books unbalanced (ADR-377).
 */
export function TrustIntegrityCard({
  report,
  failed,
  accounts,
  slug,
  currency,
}: TrustIntegrityCardProps) {
  if (!report) {
    if (!failed) return null;
    return (
      <Card data-testid="trust-integrity-card">
        <CardHeader>
          <CardTitle>Trust integrity</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-muted-foreground text-sm" data-testid="integrity-error">
            Trust integrity could not be loaded.
          </p>
        </CardContent>
      </Card>
    );
  }

  const flags = listFlags(report, accounts);
  return (
    <Card data-testid="trust-integrity-card">
      <CardHeader>
        <CardTitle>Trust integrity</CardTitle>
        <CardDescription>As at {formatLocalDate(report.asAt)}, all trust accounts</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {report.balanced ? (
          <p
            className="text-success-foreground flex items-center gap-2 text-sm font-medium"
            data-testid="integrity-books-balance"
          >
            <CheckCircle2 aria-hidden="true" className="size-4 shrink-0" />
            Books balance
          </p>
        ) : (
          <div className="space-y-2" data-testid="integrity-out-of-balance">
            <p className="text-danger-foreground flex items-center gap-2 text-sm font-medium">
              <AlertTriangle aria-hidden="true" className="size-4 shrink-0" />
              {report.difference === 0
                ? "The books do not balance — see the flags below."
                : "The books do not balance."}
            </p>
            <dl className="max-w-md space-y-1 text-sm">
              <div className="flex items-baseline justify-between gap-4">
                <dt className="text-slate-700 dark:text-slate-300">Cashbook</dt>
                <dd className="font-mono text-slate-950 tabular-nums dark:text-slate-50">
                  {formatCurrency(report.cashbook, currency)}
                </dd>
              </div>
              <div className="flex items-baseline justify-between gap-4">
                <dt className="text-slate-700 dark:text-slate-300">Trust creditors</dt>
                <dd className="font-mono text-slate-950 tabular-nums dark:text-slate-50">
                  {formatCurrency(report.creditorTotal, currency)}
                </dd>
              </div>
              <div className="flex items-baseline justify-between gap-4 border-t border-slate-200 pt-1 font-medium dark:border-slate-800">
                <dt className="text-slate-950 dark:text-slate-50">Difference</dt>
                <dd
                  className={`font-mono tabular-nums ${
                    report.difference === 0
                      ? "text-slate-950 dark:text-slate-50"
                      : "text-danger-foreground"
                  }`}
                  data-testid="integrity-difference"
                >
                  {formatCurrency(report.difference, currency)}
                </dd>
              </div>
            </dl>
          </div>
        )}

        {flags.length === 0 ? (
          <p className="text-muted-foreground text-sm" data-testid="integrity-no-flags">
            No flags
          </p>
        ) : (
          <ul className="space-y-3" data-testid="integrity-flags" aria-label="Integrity flags">
            {flags.map((listed) => (
              <FlagItem key={listed.key} listed={listed} slug={slug} currency={currency} />
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
