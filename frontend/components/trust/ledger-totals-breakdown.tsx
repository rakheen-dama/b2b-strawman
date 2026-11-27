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
   * `"tiles"` (the client ledger page, Phase 91 667A): the same figures and labels as a row of
   * compact tiles styled as the kit `StatCard`; each tile is a `div` of one `dt` (label) and one
   * `dd` (amount) and keeps its `ledger-total-<key>` testid.
   */
  layout?: "list" | "tiles";
}

/**
 * One tile in the look of the kit's compact `StatCard` (packages/ui/src/stat-card.tsx on Card,
 * card.tsx — classes copied, keep in step). Not the kit component itself: it renders div/span
 * markup, and a `<dl>` may only hold `dt`/`dd` groups (F-667A-A1, axe list/listitem).
 */
function TotalTile({ testId, label, amount }: { testId: string; label: string; amount: string }) {
  return (
    <div
      className="bg-card text-card-foreground flex flex-col gap-0.5 rounded-lg border border-slate-200/80 p-3 shadow-sm dark:border-slate-800/80"
      data-testid={testId}
    >
      <dt className="text-muted-foreground text-sm">{label}</dt>
      <dd className="text-foreground font-mono text-lg font-semibold tabular-nums">{amount}</dd>
    </div>
  );
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
            <TotalTile
              key={row.key}
              testId={`ledger-total-${row.key}`}
              label={row.label}
              amount={formatCurrency(row.amount, currency)}
            />
          ))}
          <TotalTile
            testId="ledger-total-balance"
            label={invested !== 0 ? "General balance" : "Balance"}
            amount={formatCurrency(ledger.balance, currency)}
          />
          {invested !== 0 && (
            <>
              <TotalTile
                testId="ledger-total-invested"
                label="Invested (section 86(4))"
                amount={formatCurrency(invested, currency)}
              />
              <TotalTile
                testId="ledger-total-held"
                label="Total held in trust"
                amount={formatCurrency(ledger.balance + invested, currency)}
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
