import { CheckCircle2, XCircle } from "lucide-react";
import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { TakeOnTrustEquation } from "@/lib/types";

/** The indicator rows' X3 labels (§17.10), in X3 order. The EQ-1 minus is U+2212. */
export const TRUST_EQUATION_LABEL = {
  eq1: "Bank balance + outstanding deposits − outstanding payments = cashbook",
  items: "Listed outstanding items agree with the totals",
  eq2: "Cashbook = trust creditors",
  investments: "Investments agree with the confirmations",
  deficit: "No trust deficit",
} as const;

type RowId = keyof typeof TRUST_EQUATION_LABEL;

interface Side {
  label: string;
  amount: string;
}

interface IndicatorRow {
  id: RowId;
  holds: boolean;
  difference: string;
  sides: Side[];
}

/** The figures behind each row (product copy, D-190), in the order the sum reads. */
function rowsOf(equation: TakeOnTrustEquation): IndicatorRow[] {
  const { eq1, itemsTie, eq2, investments, deficit } = equation;
  return [
    {
      id: "eq1",
      holds: eq1.holds,
      difference: eq1.difference,
      sides: [
        { label: "Bank statement balance", amount: eq1.bankBalance },
        { label: "Plus outstanding deposits", amount: eq1.plusOutstandingDeposits },
        { label: "Less outstanding payments", amount: eq1.lessOutstandingPayments },
        { label: "Adjusted bank balance", amount: eq1.adjustedBank },
        { label: "Cashbook balance", amount: eq1.cashbook },
      ],
    },
    {
      id: "items",
      holds: itemsTie.holds,
      difference: itemsTie.difference,
      sides: [
        { label: "Outstanding deposits listed", amount: itemsTie.listedDeposits },
        { label: "Outstanding deposits at cutover", amount: itemsTie.outstandingDeposits },
        { label: "Outstanding payments listed", amount: itemsTie.listedPayments },
        { label: "Outstanding payments at cutover", amount: itemsTie.outstandingPayments },
      ],
    },
    {
      id: "eq2",
      holds: eq2.holds,
      difference: eq2.difference,
      sides: [
        { label: "Client ledgers", amount: eq2.clientLedgers },
        { label: "Fund interest and charges ledger", amount: eq2.fundLedger },
        { label: "Unidentified deposits", amount: eq2.unidentified },
        { label: "Placed in section 86(3) investments", amount: eq2.placedInSection86_3 },
        { label: "Trust creditors", amount: eq2.creditors },
        { label: "Cashbook balance", amount: eq2.cashbook },
      ],
    },
    {
      id: "investments",
      holds: investments.holds,
      difference: investments.difference,
      sides: [
        { label: "Investments listed", amount: investments.listed },
        { label: "Total invested per the confirmations", amount: investments.perConfirmations },
      ],
    },
    {
      id: "deficit",
      holds: !deficit.inDeficit,
      difference: deficit.difference,
      sides: [
        { label: "Trust creditors", amount: deficit.trustCreditors },
        { label: "Trust money held", amount: deficit.moneyHeld },
      ],
    },
  ];
}

/**
 * A difference is shown as its size: the sign says which side is larger, which the figures in
 * the row's detail show (D-185 OP-9 — a difference quoted as "Difference R{x}" is a magnitude).
 */
const differenceText = (difference: string) => formatCurrency(Math.abs(Number(difference)), "ZAR");

interface TrustEquationIndicatorProps {
  /** `summary.equation` of a validated trust batch; null when an account check failed. */
  equation: TakeOnTrustEquation | null | undefined;
}

/**
 * The trust position's five control totals (Phase 93 657B, §17.3.9, §17.10 X3): each row says
 * whether it agrees and shows "Difference R{x}". Purely presentational — the figures are the
 * backend's dry-run summary (X2), which every changed position re-runs (OP-5), so it stays live.
 * Renders nothing when there is no equation (the account errors above the table say why).
 */
export function TrustEquationIndicator({ equation }: TrustEquationIndicatorProps) {
  if (!equation) return null;
  return (
    <section
      aria-labelledby="take-on-equation-heading"
      className="rounded-lg border border-slate-200 dark:border-slate-800"
      data-testid="take-on-equation"
    >
      <h3
        id="take-on-equation-heading"
        className="border-b border-slate-200 px-4 py-2 text-sm font-medium text-slate-950 dark:border-slate-800 dark:text-slate-50"
      >
        Control totals
      </h3>
      <ul className="divide-y divide-slate-200 dark:divide-slate-800">
        {rowsOf(equation).map((row) => (
          <li
            key={row.id}
            className="px-4 py-2"
            data-testid={`take-on-equation-${row.id}`}
            data-holds={row.holds}
          >
            <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 text-sm">
              <span className="flex items-center gap-2 text-slate-950 dark:text-slate-50">
                {row.holds ? (
                  <CheckCircle2
                    aria-hidden="true"
                    className="size-4 shrink-0 text-green-600 dark:text-green-400"
                  />
                ) : (
                  <XCircle
                    aria-hidden="true"
                    className="size-4 shrink-0 text-red-600 dark:text-red-400"
                  />
                )}
                <span>{TRUST_EQUATION_LABEL[row.id]}</span>
                <span className="sr-only">{row.holds ? "Agrees" : "Does not agree"}</span>
              </span>
              <span
                className={cn(
                  "font-mono font-medium tabular-nums",
                  row.holds
                    ? "text-green-600 dark:text-green-400"
                    : "text-red-600 dark:text-red-400"
                )}
                data-testid={`take-on-equation-${row.id}-difference`}
              >
                Difference {differenceText(row.difference)}
              </span>
            </div>
            <details className="mt-1 text-xs text-slate-600 dark:text-slate-400">
              <summary className="cursor-pointer select-none">The figures</summary>
              <dl className="mt-1 space-y-0.5 pl-6">
                {row.sides.map((side) => (
                  <div key={side.label} className="flex justify-between gap-4">
                    <dt>{side.label}</dt>
                    <dd className="font-mono tabular-nums">
                      {formatCurrency(Number(side.amount), "ZAR")}
                    </dd>
                  </div>
                ))}
              </dl>
            </details>
          </li>
        ))}
      </ul>
    </section>
  );
}
