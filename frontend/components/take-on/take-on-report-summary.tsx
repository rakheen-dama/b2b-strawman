"use client";

import { formatCurrency, formatDateTime } from "@/lib/format";
import {
  TAKE_ON_REPORT_FLAG_LABEL,
  takeOnLongDate,
  type TakeOnReportCreditorKind,
  type TakeOnReportCreditorLine,
  type TakeOnReportPosition,
} from "@/lib/types";
import { TakeOnError } from "./take-on-feedback";
import { useTakeOnReport } from "./use-take-on-report";

/**
 * The reconciled-position lines in X3 words; the Fund is spelt out on its first use on the screen,
 * "the Fund" (its ledger) after that.
 */
const POSITION_ROWS: ReadonlyArray<readonly [keyof TakeOnReportPosition, string]> = [
  ["bankBalance", "Bank statement balance"],
  ["outstandingDeposits", "Outstanding deposits"],
  ["outstandingPayments", "Outstanding payments"],
  ["adjustedBank", "Adjusted bank balance"],
  ["cashbook", "Cashbook balance"],
  ["fundLedger", "Legal Practitioners' Fidelity Fund interest and charges ledger"],
  ["investedTotal", "Total invested"],
];

/** The trust creditor groups of the banking account's list, in report order (product copy, 657C). */
const CREDITOR_GROUPS: ReadonlyArray<readonly [TakeOnReportCreditorKind, string]> = [
  ["CLIENT", "Client ledgers"],
  ["FUND", "Fund interest and charges ledger"],
  ["UNIDENTIFIED", "Unidentified deposits"],
  ["CLIENT_INVESTMENT", "Section 86(4) investments"],
  ["INTER_ACCOUNT", "Other trust accounts"],
];

const money = (amount: string | null | undefined) =>
  amount == null ? "—" : formatCurrency(Number(amount), "ZAR");

/** Σ balance of one creditor group, in cents (the strings are exact two-decimal amounts). */
function groupTotal(lines: TakeOnReportCreditorLine[], kind: TakeOnReportCreditorKind) {
  const of = lines.filter((l) => l.kind === kind);
  const cents = of.reduce((sum, l) => sum + Math.round(Number(l.balance ?? "0") * 100), 0);
  return { count: of.length, amount: cents / 100 };
}

/**
 * The trust take-on report summary on the Results step (Phase 93 657C, R-11): read from `GET
 * /api/take-on/reports/{id}` — the snapshot the commit wrote — never recomputed. Previous system
 * against Kazi with the difference (R0,00 by the self-check), the trust creditors by group, the
 * sign-off and the flags. The PDF and CSV are `ReportDownloads` (657D), shown beside it.
 */
export function TakeOnReportSummary({ reportId }: { reportId: string }) {
  const report = useTakeOnReport(reportId);

  if (report.error) {
    return <TakeOnError message={String(report.error.message)} testId="take-on-report-error" />;
  }
  if (!report.data) {
    return (
      <p role="status" className="text-sm text-slate-600 dark:text-slate-400">
        Loading the take-on report…
      </p>
    );
  }

  const { snapshot, generatedAt } = report.data;
  const lines = snapshot.creditorList?.lines ?? [];
  const groups = CREDITOR_GROUPS.map(([kind, label]) => ({
    kind,
    label,
    ...groupTotal(lines, kind),
  })).filter((g) => g.count > 0);
  const signOff = snapshot.signOff;

  return (
    <section
      className="space-y-5 rounded-lg border border-slate-200 px-4 py-4 dark:border-slate-800"
      data-testid="take-on-report-summary"
    >
      <div className="space-y-1">
        <h4 className="text-base font-medium text-slate-950 dark:text-slate-50">
          Trust take-on report
        </h4>
        <p className="text-sm text-slate-600 dark:text-slate-400">
          {snapshot.account?.name ?? "Trust account"} at {takeOnLongDate(snapshot.cutoverDate)} ·
          generated {formatDateTime(generatedAt)}
        </p>
      </div>

      <div className="space-y-2">
        <h5 className="text-sm font-medium text-slate-900 dark:text-slate-100">
          Reconciled position at cutover
        </h5>
        <div className="overflow-x-auto">
          <table className="w-full text-sm" data-testid="take-on-report-position">
            <thead>
              <tr className="text-left text-xs text-slate-500 dark:text-slate-400">
                <th className="py-1 pr-4 font-normal">
                  <span className="sr-only">Figure</span>
                </th>
                <th className="py-1 pr-4 text-right font-normal">Previous system</th>
                <th className="py-1 pr-4 text-right font-normal">Kazi</th>
                <th className="py-1 text-right font-normal">Difference</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {POSITION_ROWS.map(([key, label]) => (
                <tr key={key} data-testid={`take-on-report-position-${key}`}>
                  <td className="py-1 pr-4 text-slate-700 dark:text-slate-300">{label}</td>
                  <td className="py-1 pr-4 text-right font-mono tabular-nums">
                    {money(snapshot.previousSystem?.[key])}
                  </td>
                  <td className="py-1 pr-4 text-right font-mono tabular-nums">
                    {money(snapshot.kazi?.[key])}
                  </td>
                  <td className="py-1 text-right font-mono tabular-nums">
                    {money(snapshot.differences?.[key])}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="space-y-2" data-testid="take-on-report-creditors">
        <h5 className="text-sm font-medium text-slate-900 dark:text-slate-100">
          Trust creditors at cutover
        </h5>
        <dl className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-[1fr_max-content]">
          {groups.map((g) => (
            <div key={g.kind} className="contents">
              <dt className="text-slate-700 dark:text-slate-300">
                {g.label}{" "}
                <span className="text-xs text-slate-500 dark:text-slate-400">({g.count})</span>
              </dt>
              <dd
                className="text-right font-mono tabular-nums"
                data-testid={`take-on-report-creditor-${g.kind.toLowerCase().replace(/_/g, "-")}`}
              >
                {formatCurrency(g.amount, "ZAR")}
              </dd>
            </div>
          ))}
          <dt className="font-medium text-slate-900 dark:text-slate-100">Trust creditors</dt>
          <dd
            className="text-right font-mono font-medium tabular-nums"
            data-testid="take-on-report-creditor-total"
          >
            {money(snapshot.creditorList?.creditorTotal)}
          </dd>
        </dl>
      </div>

      {signOff && (
        <div className="space-y-1 text-sm" data-testid="take-on-report-signoff">
          <h5 className="font-medium text-slate-900 dark:text-slate-100">Sign-off</h5>
          <p className="text-slate-700 dark:text-slate-300">
            Signed off by {signOff.signedBy ?? "—"}
            {signOff.role ? ` (${signOff.role})` : ""}
            {signOff.signedAt ? ` on ${formatDateTime(signOff.signedAt)}` : ""}. Prepared by{" "}
            {signOff.preparedBy ?? "—"}.
          </p>
          {signOff.notes.map((note) => (
            <p key={note} className="text-slate-700 dark:text-slate-300">
              {note}
            </p>
          ))}
        </div>
      )}

      {snapshot.flags.length > 0 && (
        <div className="space-y-1 text-sm" data-testid="take-on-report-flags">
          <h5 className="font-medium text-slate-900 dark:text-slate-100">Flags</h5>
          <ul className="list-disc space-y-0.5 pl-5 text-slate-700 dark:text-slate-300">
            {snapshot.flags.map((flag) => (
              <li key={flag}>{TAKE_ON_REPORT_FLAG_LABEL[flag] ?? "Flagged"}</li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
