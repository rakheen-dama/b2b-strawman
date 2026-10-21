import Link from "next/link";
import { CheckCircle2, XCircle } from "lucide-react";
import type { GateResult } from "@/lib/api/matter-closure";

interface MatterClosureReportProps {
  gates: GateResult[];
  slug: string;
  projectId: string;
}

/**
 * Maps a gate code to the deep-link path that lets the user resolve
 * the failing gate. Returns null when no navigational fix exists.
 */
function fixHrefFor(code: string, slug: string, projectId: string): string | null {
  switch (code) {
    case "TRUST_BALANCE_ZERO":
      return `/org/${slug}/matters/${projectId}?tab=trust`;
    case "ALL_DISBURSEMENTS_APPROVED":
    case "ALL_DISBURSEMENTS_SETTLED":
      return `/org/${slug}/matters/${projectId}?tab=disbursements`;
    case "FINAL_BILL_ISSUED":
      return `/org/${slug}/invoices?projectId=${projectId}`;
    // Phase 87 613D: court dates and prescription trackers live in the matter's Court dates tab.
    case "NO_OPEN_COURT_DATES":
    case "NO_OPEN_PRESCRIPTIONS":
      return `/org/${slug}/matters/${projectId}?tab=court-dates`;
    case "ALL_TASKS_RESOLVED":
      return `/org/${slug}/matters/${projectId}?tab=tasks`;
    case "ALL_INFO_REQUESTS_CLOSED":
      return `/org/${slug}/matters/${projectId}?tab=requests`;
    case "ALL_ACCEPTANCE_REQUESTS_FINAL":
      return `/org/${slug}/matters/${projectId}?tab=acceptance-requests`;
    default:
      return null;
  }
}

export function MatterClosureReport({ gates, slug, projectId }: MatterClosureReportProps) {
  if (gates.length === 0) {
    return (
      <div
        className="rounded-md border border-slate-200 p-4 text-sm text-slate-600 dark:border-slate-700 dark:text-slate-400"
        data-testid="matter-closure-report-empty"
      >
        No closure gates configured for this matter.
      </div>
    );
  }

  return (
    <ul
      className="space-y-2"
      data-testid="matter-closure-report"
      aria-label="Matter closure gate report"
    >
      {gates.map((gate) => {
        const fixHref = gate.passed ? null : fixHrefFor(gate.code, slug, projectId);
        return (
          <li
            key={`${gate.order}-${gate.code}`}
            className="flex items-start gap-3 rounded-md border border-slate-200 p-3 text-sm dark:border-slate-700"
            data-testid={`matter-closure-gate-row-${gate.code}`}
            data-passed={gate.passed ? "true" : "false"}
          >
            <span className="mt-0.5 shrink-0" aria-hidden="true">
              {gate.passed ? (
                <CheckCircle2
                  className="size-4 text-teal-600 dark:text-teal-400"
                  data-testid={`matter-closure-gate-pass-${gate.code}`}
                />
              ) : (
                <XCircle
                  className="text-destructive size-4"
                  data-testid={`matter-closure-gate-fail-${gate.code}`}
                />
              )}
            </span>
            <div className="min-w-0 flex-1">
              <p
                className={
                  gate.passed
                    ? "text-slate-700 dark:text-slate-300"
                    : "font-medium text-slate-900 dark:text-slate-100"
                }
              >
                {gate.message}
              </p>
              {fixHref && (
                <Link
                  href={fixHref}
                  className="mt-1 inline-block text-xs font-medium text-teal-600 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
                  data-testid={`matter-closure-gate-fix-${gate.code}`}
                >
                  Fix this
                </Link>
              )}
            </div>
          </li>
        );
      })}
    </ul>
  );
}
