import { StatCard } from "@b2mash/ui/stat-card";
import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { ClientLedgerCard } from "@/lib/types";

type LedgerTotals = Pick<
  ClientLedgerCard,
  | "balance"
  | "broughtForward"
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
  /**
   * `"list"` (default; `TrustBalanceCard`): the signed sum, one `dt`/`dd` row per figure.
   * `"tiles"` (the client ledger page, Phase 91 667A): the same figures and labels as a compact kit
   * `StatCard` row; each tile keeps its `ledger-total-<key>` testid and holds its amount in a `dd`.
   */
  layout?: "list" | "tiles";
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
 *
 * A client taken on from the previous system (657A) starts from "Brought forward at take-on"
 * (signed; hidden while zero), then Deposits is an addition.
 *
 * The tile layout drops the +/−/= signs (a tile label is plain text); the figures, labels, order
 * and hidden-while-zero rules are the list's.
 */
export function LedgerTotalsBreakdown({
  ledger,
  currency = "ZAR",
  className,
  layout = "list",
}: LedgerTotalsBreakdownProps) {
  // `?? 0`: a stale response without the key still renders (TrustBalanceCard shares this)
  const broughtForward = ledger.broughtForward ?? 0;
  const rows: Row[] = [
    {
      key: "brought-forward",
      sign: "",
      label: "Brought forward at take-on",
      amount: broughtForward,
      hideWhenZero: true,
    },
    {
      key: "deposits",
      sign: broughtForward !== 0 ? "+" : "",
      label: "Deposits",
      amount: ledger.totalDeposits,
    },
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
  const visibleRows = rows.filter((row) => !(row.hideWhenZero && row.amount === 0));

  if (layout === "tiles") {
    // Container-sized (D-72): 4-up in the ~710px page column at 1024, 6-up at 1440.
    return (
      <div className={cn("@container", className)}>
        <dl
          className="grid grid-cols-2 gap-3 @xl:grid-cols-4 @5xl:grid-cols-6"
          data-testid="ledger-totals-breakdown"
        >
          {visibleRows.map((row) => (
            <StatCard
              key={row.key}
              variant="compact"
              label={row.label}
              value={<dd>{formatCurrency(row.amount, currency)}</dd>}
              data-testid={`ledger-total-${row.key}`}
            />
          ))}
          <StatCard
            variant="compact"
            label={invested !== 0 ? "General balance" : "Balance"}
            value={<dd>{formatCurrency(ledger.balance, currency)}</dd>}
            data-testid="ledger-total-balance"
          />
          {invested !== 0 && (
            <>
              <StatCard
                variant="compact"
                label="Invested (section 86(4))"
                value={<dd>{formatCurrency(invested, currency)}</dd>}
                data-testid="ledger-total-invested"
              />
              <StatCard
                variant="compact"
                label="Total held in trust"
                value={<dd>{formatCurrency(ledger.balance + invested, currency)}</dd>}
                data-testid="ledger-total-held"
              />
            </>
          )}
        </dl>
      </div>
    );
  }

  return (
    <dl className={cn("space-y-1 text-sm", className)} data-testid="ledger-totals-breakdown">
      {visibleRows.map((row) => (
        <div
          key={row.key}
          className="flex items-baseline justify-between gap-4"
          data-testid={`ledger-total-${row.key}`}
        >
          <dt className="text-muted-foreground">
            <span aria-hidden="true" className="text-muted-foreground inline-block w-3">
              {row.sign}
            </span>
            {/* The visual "−" is aria-hidden; say it for screen readers (PR #1746 review) */}
            {row.sign === "−" && <span className="sr-only">less </span>}
            {row.label}
          </dt>
          <dd className="text-foreground font-mono tabular-nums">
            {formatCurrency(row.amount, currency)}
          </dd>
        </div>
      ))}
      <div
        className="border-border flex items-baseline justify-between gap-4 border-t pt-1 font-medium"
        data-testid="ledger-total-balance"
      >
        <dt className="text-slate-700 dark:text-slate-300">
          <span aria-hidden="true" className="text-muted-foreground inline-block w-3">
            =
          </span>
          {invested !== 0 ? "General balance" : "Balance"}
        </dt>
        <dd className="text-foreground font-mono tabular-nums">
          {formatCurrency(ledger.balance, currency)}
        </dd>
      </div>
      {invested !== 0 && (
        <>
          <div
            className="flex items-baseline justify-between gap-4"
            data-testid="ledger-total-invested"
          >
            <dt className="text-muted-foreground">
              <span aria-hidden="true" className="text-muted-foreground inline-block w-3">
                +
              </span>
              Invested (section 86(4))
            </dt>
            <dd className="text-foreground font-mono tabular-nums">
              {formatCurrency(invested, currency)}
            </dd>
          </div>
          <div
            className="border-border flex items-baseline justify-between gap-4 border-t pt-1 font-medium"
            data-testid="ledger-total-held"
          >
            <dt className="text-slate-700 dark:text-slate-300">
              <span aria-hidden="true" className="text-muted-foreground inline-block w-3">
                =
              </span>
              Total held in trust
            </dt>
            <dd className="text-foreground font-mono tabular-nums">
              {formatCurrency(ledger.balance + invested, currency)}
            </dd>
          </div>
        </>
      )}
    </dl>
  );
}
