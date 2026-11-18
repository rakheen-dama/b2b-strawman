import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { ClientLedgerCard } from "@/lib/types";

type LedgerTotals = Pick<
  ClientLedgerCard,
  | "balance"
  | "totalDeposits"
  | "totalTransfersIn"
  | "totalPayments"
  | "totalFeeTransfers"
  | "totalRefunds"
  | "totalTransfersOut"
  | "totalInvestmentPlacements"
  | "totalInvestmentReturns"
  | "investedBalance"
>;

interface LedgerTotalsBreakdownProps {
  ledger: LedgerTotals;
  currency?: string;
  className?: string;
}

interface Row {
  key: string;
  sign: "" | "+" | "−";
  label: string;
  amount: number;
  hideWhenZero?: boolean;
}

/**
 * The client ledger's posted totals as the sum that makes up its balance (G89-F13, 648E):
 * Deposits + Transfers in + Returned from section 86(4) investments − Payments − Fee transfers
 * − Refunds − Transfers out − Placed in section 86(4) investments = Balance (the general balance).
 * Every total is derived server-side from the posted ledger, so the rows always reconcile.
 * Transfer and investment rows are hidden while they are zero.
 *
 * While money is invested for the client (ADR-379 L7), the balance row reads "General balance"
 * and is followed by "Invested (section 86(4))" and "Total held in trust" (general + invested —
 * the client's total, unchanged by a placement or its return).
 */
export function LedgerTotalsBreakdown({
  ledger,
  currency = "ZAR",
  className,
}: LedgerTotalsBreakdownProps) {
  const rows: Row[] = [
    { key: "deposits", sign: "", label: "Deposits", amount: ledger.totalDeposits },
    {
      key: "transfers-in",
      sign: "+",
      label: "Transfers in",
      amount: ledger.totalTransfersIn,
      hideWhenZero: true,
    },
    {
      key: "investment-returns",
      sign: "+",
      label: "Returned from section 86(4) investments",
      amount: ledger.totalInvestmentReturns,
      hideWhenZero: true,
    },
    { key: "payments", sign: "−", label: "Payments", amount: ledger.totalPayments },
    { key: "fee-transfers", sign: "−", label: "Fee transfers", amount: ledger.totalFeeTransfers },
    { key: "refunds", sign: "−", label: "Refunds", amount: ledger.totalRefunds },
    {
      key: "transfers-out",
      sign: "−",
      label: "Transfers out",
      amount: ledger.totalTransfersOut,
      hideWhenZero: true,
    },
    {
      key: "investment-placements",
      sign: "−",
      label: "Placed in section 86(4) investments",
      amount: ledger.totalInvestmentPlacements,
      hideWhenZero: true,
    },
  ];
  const invested = ledger.investedBalance;

  return (
    <dl className={cn("space-y-1 text-sm", className)} data-testid="ledger-totals-breakdown">
      {rows
        .filter((row) => !(row.hideWhenZero && row.amount === 0))
        .map((row) => (
          <div
            key={row.key}
            className="flex items-baseline justify-between gap-4"
            data-testid={`ledger-total-${row.key}`}
          >
            <dt className="text-slate-500 dark:text-slate-400">
              <span aria-hidden="true" className="inline-block w-3 text-slate-400">
                {row.sign}
              </span>
              {/* The visual "−" is aria-hidden; say it for screen readers (PR #1746 review) */}
              {row.sign === "−" && <span className="sr-only">less </span>}
              {row.label}
            </dt>
            <dd className="font-mono text-slate-900 tabular-nums dark:text-slate-100">
              {formatCurrency(row.amount, currency)}
            </dd>
          </div>
        ))}
      <div
        className="flex items-baseline justify-between gap-4 border-t border-slate-200 pt-1 font-medium dark:border-slate-800"
        data-testid="ledger-total-balance"
      >
        <dt className="text-slate-700 dark:text-slate-300">
          <span aria-hidden="true" className="inline-block w-3 text-slate-400">
            =
          </span>
          {invested !== 0 ? "General balance" : "Balance"}
        </dt>
        <dd className="font-mono text-slate-950 tabular-nums dark:text-slate-50">
          {formatCurrency(ledger.balance, currency)}
        </dd>
      </div>
      {invested !== 0 && (
        <>
          <div
            className="flex items-baseline justify-between gap-4"
            data-testid="ledger-total-invested"
          >
            <dt className="text-slate-500 dark:text-slate-400">
              <span aria-hidden="true" className="inline-block w-3 text-slate-400">
                +
              </span>
              Invested (section 86(4))
            </dt>
            <dd className="font-mono text-slate-900 tabular-nums dark:text-slate-100">
              {formatCurrency(invested, currency)}
            </dd>
          </div>
          <div
            className="flex items-baseline justify-between gap-4 border-t border-slate-200 pt-1 font-medium dark:border-slate-800"
            data-testid="ledger-total-held"
          >
            <dt className="text-slate-700 dark:text-slate-300">
              <span aria-hidden="true" className="inline-block w-3 text-slate-400">
                =
              </span>
              Total held in trust
            </dt>
            <dd className="font-mono text-slate-950 tabular-nums dark:text-slate-50">
              {formatCurrency(ledger.balance + invested, currency)}
            </dd>
          </div>
        </>
      )}
    </dl>
  );
}
