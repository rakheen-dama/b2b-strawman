import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { ClientLedgerCard } from "@/lib/types";

type LedgerTotals = Pick<
  ClientLedgerCard,
  | "balance"
  | "totalDeposits"
  | "totalInterestCredited"
  | "totalTransfersIn"
  | "totalPayments"
  | "totalFeeTransfers"
  | "totalRefunds"
  | "totalTransfersOut"
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
 * The client ledger's posted totals as the sum that makes up its balance (G89-F13):
 * Deposits + Interest + Transfers in − Payments − Fee transfers − Refunds − Transfers out = Balance.
 * Every total is derived server-side from the posted ledger, so the rows always reconcile.
 * Transfer rows are hidden while they are zero.
 */
export function LedgerTotalsBreakdown({
  ledger,
  currency = "ZAR",
  className,
}: LedgerTotalsBreakdownProps) {
  const rows: Row[] = [
    { key: "deposits", sign: "", label: "Deposits", amount: ledger.totalDeposits },
    { key: "interest", sign: "+", label: "Interest", amount: ledger.totalInterestCredited },
    {
      key: "transfers-in",
      sign: "+",
      label: "Transfers in",
      amount: ledger.totalTransfersIn,
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
  ];

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
          Balance
        </dt>
        <dd className="font-mono text-slate-950 tabular-nums dark:text-slate-50">
          {formatCurrency(ledger.balance, currency)}
        </dd>
      </div>
    </dl>
  );
}
