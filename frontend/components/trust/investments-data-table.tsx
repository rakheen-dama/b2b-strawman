"use client";

import { useMemo, type ReactNode } from "react";
import { Badge } from "@b2mash/ui/badge";
import { daysBetween } from "@b2mash/shared/date";
import { DataTable, type DataTableToolbarSlots } from "@/components/data-table/data-table";
import { MoneyCell } from "@/components/data-table/cells";
import { defineColumns, type DataTableColumn } from "@/components/data-table/define-columns";
import { InvestmentPageClient } from "@/app/(app)/org/[slug]/trust-accounting/investments/InvestmentPageClient";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { InvestmentBasis, TrustInvestment, TrustInvestmentStatus } from "@/lib/types/trust";

// -- Badge variant mapping ------------------------------------------------

const STATUS_BADGE_VARIANT: Record<TrustInvestmentStatus, "neutral" | "success" | "warning"> = {
  ACTIVE: "success",
  MATURED: "warning",
  WITHDRAWN: "neutral",
  CANCELLED: "neutral",
};

/**
 * Status text in the register (no raw enum on screen). CANCELLED: the placement was rejected, so
 * nothing was invested (G89-F87).
 */
const STATUS_LABEL: Record<TrustInvestmentStatus, string> = {
  ACTIVE: "Active",
  MATURED: "Matured",
  WITHDRAWN: "Withdrawn",
  CANCELLED: "Cancelled",
};

/** The backend withdraws a held investment — ACTIVE or MATURED (648A). */
const WITHDRAWABLE: ReadonlySet<TrustInvestmentStatus> = new Set(["ACTIVE", "MATURED"]);

/** A section 86(3) investment is the firm's surplus trust money; it names no client. */
const FIRM_CLIENT_LABEL = "Firm (section 86(3))";

const BASIS_BADGE_VARIANT: Record<InvestmentBasis, "neutral" | "lead"> = {
  FIRM_DISCRETION: "neutral",
  CLIENT_INSTRUCTION: "lead",
};

const BASIS_LABEL: Record<InvestmentBasis, string> = {
  FIRM_DISCRETION: "Section 86(3)",
  CLIENT_INSTRUCTION: "Section 86(4)",
};

// -- Register cell classes (W-1: every cell may wrap; secondary facts stack under the first) --

/** Every header and cell wraps (tailwind-merge drops the kit's `whitespace-nowrap`). */
const HEAD = "whitespace-normal align-bottom";
const CELL = "text-foreground whitespace-normal align-top";
const SUB = "text-muted-foreground block text-xs";

/**
 * K-91: the Actions column sticks to the right edge of the register's scroll frame, so Record
 * interest / Interest entries / Withdraw stay in view while the wider columns scroll under it.
 * Opaque card background, above the scrolled cells, with a left rule. A local copy of
 * `STICKY_ACTIONS_CLASS` in `trust-transactions-table.tsx` (667A, K-90); `pr-0` as the column always
 * had, so the sticky column clears Balance at 1024.
 */
const STICKY_ACTIONS_CLASS =
  "sticky right-0 z-[1] bg-card pr-0 shadow-[inset_1px_0_0_var(--border)]";

/**
 * The maturity highlight (G88-F49) on the `maturity-alert` row, set from this wrapper because the
 * kit has no row-class hook. The last cell (the sticky Actions cell, when shown) is opaque, so in
 * dark it takes the row's amber-over-card colour as a solid fill instead of the card colour.
 */
const MATURITY_TINT_CLASS = cn(
  "[&_tr[data-testid=maturity-alert]]:bg-amber-50 dark:[&_tr[data-testid=maturity-alert]]:bg-amber-950/30",
  "[&_tr[data-testid=maturity-alert]>td:last-child]:bg-amber-50 dark:[&_tr[data-testid=maturity-alert]>td:last-child]:bg-[color-mix(in_oklab,var(--color-amber-950)_30%,var(--card))]"
);

// -- Maturity helper ------------------------------------------------------

/** Matures within `daysAhead` days of `todayIso` (South African today), inclusive (G88-F49). */
function isMaturing(maturityDate: string | null, todayIso: string, daysAhead = 30): boolean {
  if (!maturityDate) return false;
  const diffDays = daysBetween(todayIso, maturityDate);
  return diffDays >= 0 && diffDays <= daysAhead;
}

// -- Table ----------------------------------------------------------------

interface InvestmentsDataTableProps {
  /** The investments the server read (`fetchInvestments`), passed through untouched. */
  rows: TrustInvestment[];
  currency: string;
  /** The funding trust banking account (the row actions post against it). */
  accountId: string;
  /** MANAGE_TRUST (or admin/owner): the Actions column shows. */
  canManageTrust: boolean;
  /** South African today (`todayInSA()`), computed once on the server (ADR-396). */
  todayIso: string;
  /** Pre-rendered toolbar slots (the basis filter). */
  toolbar?: DataTableToolbarSlots;
  /** Pre-rendered empty state, shown instead of the table. */
  empty: ReactNode;
}

const getInvestmentRowId = (row: TrustInvestment) => row.id;

/**
 * Trust › Investments' register on `DataTable` (Phase 91 667B, client mode, no pager). Eight
 * columns in today's order (Client, Investment account, Rate, Term, Principal, Balance, Status, and
 * Actions for MANAGE_TRUST); the basis sits under the client, the institution and account number
 * under the investment account, the LPFF share under the rate, the maturity under the deposit date
 * and the interest under the principal (W-1). A held investment maturing within 30 days is the
 * `maturity-alert` row.
 */
export function InvestmentsDataTable({
  rows,
  currency,
  accountId,
  canManageTrust,
  todayIso,
  toolbar,
  empty,
}: InvestmentsDataTableProps) {
  const maturingIds = useMemo(
    () =>
      new Set(
        rows
          .filter((inv) => inv.status === "ACTIVE" && isMaturing(inv.maturityDate, todayIso))
          .map((inv) => inv.id)
      ),
    [rows, todayIso]
  );

  // Memoised: flexRender renders each cell function as a component, so a new column array would
  // remount every cell on each re-render.
  const columns = useMemo(() => {
    const base: DataTableColumn<TrustInvestment>[] = [
      {
        id: "client",
        enableHiding: false,
        meta: { label: "Client", headerClassName: HEAD, cellClassName: CELL },
        cell: ({ row }) => (
          <div data-testid="investment-client-cell">
            <span className="block">{row.original.clientName ?? FIRM_CLIENT_LABEL}</span>
            <Badge
              variant={BASIS_BADGE_VARIANT[row.original.investmentBasis]}
              className="mt-1 shrink whitespace-normal"
            >
              {BASIS_LABEL[row.original.investmentBasis]}
            </Badge>
          </div>
        ),
      },
      {
        id: "investmentAccount",
        meta: { label: "Investment account", headerClassName: HEAD, cellClassName: CELL },
        cell: ({ row }) => (
          <div data-testid="investment-account-cell">
            <span className="block">{row.original.investmentAccountName}</span>
            <span className={SUB}>{row.original.institution}</span>
            <span className={SUB}>{row.original.accountNumber}</span>
          </div>
        ),
      },
      {
        id: "rate",
        meta: { label: "Rate", headerClassName: HEAD, cellClassName: cn(CELL, "tabular-nums") },
        cell: ({ row }) => (
          <>
            <span className="block font-mono">
              {(Number(row.original.interestRate) * 100).toFixed(2)}%
            </span>
            <span className={SUB}>
              LPFF share{" "}
              {row.original.investmentBasis === "CLIENT_INSTRUCTION" ? (
                // K-36: the accent token (teal-700 in firm light), not raw teal-600
                <span className="font-medium text-(--button-accent) dark:text-teal-400">
                  5% (statutory)
                </span>
              ) : (
                <span>Arrangement</span>
              )}
            </span>
          </>
        ),
      },
      {
        id: "term",
        meta: { label: "Term", headerClassName: HEAD, cellClassName: CELL },
        cell: ({ row }) => (
          <>
            <span className="block">{formatLocalDate(row.original.depositDate)}</span>
            <span className={SUB}>
              {row.original.maturityDate
                ? `to ${formatLocalDate(row.original.maturityDate)}`
                : "Call deposit"}
            </span>
          </>
        ),
      },
      {
        id: "principal",
        meta: {
          label: "Principal",
          align: "end",
          headerClassName: HEAD,
          cellClassName: CELL,
        },
        cell: ({ row }) => {
          const inv = row.original;
          return (
            <>
              <span className="block font-mono tabular-nums">
                {formatCurrency(inv.principal, currency)}
              </span>
              {inv.investmentBasis === "CLIENT_INSTRUCTION" ? (
                // 648B (OQ-9): Σ of the posted interest entries, gross = Fund + net
                <>
                  <span className={cn(SUB, "font-mono tabular-nums")}>
                    Gross interest {formatCurrency(inv.grossInterest, currency)}
                  </span>
                  <span className={cn(SUB, "font-mono tabular-nums")}>
                    Fund 5% {formatCurrency(inv.fundShare, currency)}
                  </span>
                  <span className={cn(SUB, "font-mono tabular-nums")}>
                    Net interest {formatCurrency(inv.netInterest, currency)}
                  </span>
                </>
              ) : (
                // Section 86(3): the interest is the Fund's, never the investment's. K-91: a sans
                // sub-line, not the money column's mono
                <span className={cn(SUB, "font-sans")} data-testid="investment-interest-fund-note">
                  Interest is the Fund&apos;s — record it from the investment account&apos;s
                  statement
                </span>
              )}
            </>
          );
        },
      },
      {
        id: "balance",
        meta: { label: "Balance", align: "end", headerClassName: HEAD, cellClassName: CELL },
        // The cell's text is exactly the amount (e2e reads it whole)
        cell: ({ row }) => <MoneyCell amount={row.original.balance} currency={currency} />,
      },
      {
        id: "status",
        meta: { label: "Status", headerClassName: HEAD, cellClassName: CELL },
        cell: ({ row }) =>
          row.original.awaitingApproval ? (
            <Badge variant="warning" className="shrink whitespace-normal">
              Awaiting approval
            </Badge>
          ) : (
            <Badge
              variant={STATUS_BADGE_VARIANT[row.original.status]}
              className="shrink whitespace-normal"
            >
              {STATUS_LABEL[row.original.status]}
            </Badge>
          ),
      },
    ];
    if (!canManageTrust) return defineColumns<TrustInvestment>(base);
    return defineColumns<TrustInvestment>([
      ...base,
      {
        id: "actions",
        enableHiding: false,
        meta: {
          label: "Actions",
          headerClassName: cn(HEAD, STICKY_ACTIONS_CLASS),
          cellClassName: cn(CELL, STICKY_ACTIONS_CLASS),
        },
        cell: ({ row }) => {
          const inv = row.original;
          if (!WITHDRAWABLE.has(inv.status) || inv.awaitingApproval) return null;
          return (
            <InvestmentPageClient
              accountId={accountId}
              variant="actions"
              investmentId={inv.id}
              investmentBalance={inv.balance}
              investmentDepositDate={inv.depositDate}
              investmentAccountId={inv.investmentAccountId}
              canRecordInterest={
                inv.investmentBasis === "CLIENT_INSTRUCTION" && inv.status === "ACTIVE"
              }
              showInterestEntries={inv.investmentBasis === "CLIENT_INSTRUCTION"}
              currency={currency}
            />
          );
        },
      },
    ]);
  }, [accountId, canManageTrust, currency]);

  return (
    <div data-slot="investments-register" className={MATURITY_TINT_CLASS}>
      <DataTable
        columns={columns}
        data={rows}
        getRowId={getInvestmentRowId}
        rowTestId={(inv) =>
          maturingIds.has(inv.id) ? "maturity-alert" : `investment-row-${inv.id}`
        }
        pageSize={null}
        toolbar={toolbar}
        columnVisibility={false}
        stickyHeader={false}
        frame={false}
        empty={empty}
        caption="Investment Register"
        tableTestId="investments-table"
      />
    </div>
  );
}
