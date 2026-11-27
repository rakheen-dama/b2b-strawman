"use client";

import { useMemo, type ReactNode } from "react";
import Link from "next/link";
import { DataTable, type DataTableToolbarSlots } from "@/components/data-table/data-table";
import { MoneyCell } from "@/components/data-table/cells";
import { defineColumns, type DataTableColumn } from "@/components/data-table/define-columns";
import { buildPageHref } from "@/lib/page-href";
import { formatLocalDate } from "@/lib/format";
import type { ClientLedgerCard } from "@/lib/types";

interface ClientLedgersDataTableProps {
  /** The page the server sent (`ledgerPage.content`), passed through untouched. */
  rows: ClientLedgerCard[];
  currency: string;
  /** Zero-based (`ledgerPage.pageNumber`). */
  pageIndex: number;
  /** `ledgerPage.totalPages`; the pager shows from 2 pages. */
  pageCount: number;
  /** `/org/<slug>/trust-accounting/client-ledgers`; each client links to `<basePath>/<clientId>`. */
  basePath: string;
  /** Every string search param of the page; the pager keeps them all and sets `page`. */
  query: Record<string, string>;
  /** Pre-rendered toolbar slots (the non-zero toggle and the search form). */
  toolbar?: DataTableToolbarSlots;
  /** Pre-rendered empty state, shown instead of the table. */
  empty: ReactNode;
}

const getLedgerRowId = (row: ClientLedgerCard) => row.clientId;
const ledgerRowTestId = (row: ClientLedgerCard) => `ledger-row-${row.clientId}`;

/** A right-aligned money column whose (multi-word) header may wrap (K-90). */
function moneyColumn(
  id: string,
  label: string,
  amount: (row: ClientLedgerCard) => number,
  currency: string
): DataTableColumn<ClientLedgerCard> {
  return {
    id,
    meta: { label, align: "end", headerClassName: "whitespace-normal" },
    cell: ({ row }) => <MoneyCell amount={amount(row.original)} currency={currency} />,
  };
}

/**
 * Trust › Client Ledgers on `DataTable` (Phase 91 667A, ADR-395 server paging). The server page
 * fetches and pre-renders the toolbar and empty state; this client wrapper receives only
 * serializable props and builds the columns, pager hrefs (`buildPageHref`, every param kept) and the
 * row testid itself.
 *
 * K-89 (founder-approved register row): while any listed client has money placed in section 86(4)
 * investments, a "Placed in s86(4)" column (the total ever placed) appears after "Total Refunds";
 * while any has money still invested, an "Invested (s86(4))" column (the balance invested now)
 * follows it, and "Trust Balance" reads "General balance" — the wording of `LedgerTotalsBreakdown`.
 * The columns show those totals; they are not a full reconciliation of the row (returns from
 * investments and transfers in/out have no column here — `LedgerTotalsBreakdown` on the client
 * ledger page has the sum).
 */
export function ClientLedgersDataTable({
  rows,
  currency,
  pageIndex,
  pageCount,
  basePath,
  query,
  toolbar,
  empty,
}: ClientLedgersDataTableProps) {
  const showPlaced = useMemo(() => rows.some((r) => r.totalInvestmentPlacements !== 0), [rows]);
  const showInvested = useMemo(() => rows.some((r) => r.investedBalance !== 0), [rows]);

  // Memoised: flexRender renders each cell function as a component, so a new column array would
  // remount every cell on each re-render.
  const columns = useMemo(
    () =>
      defineColumns<ClientLedgerCard>([
        {
          id: "clientName",
          enableHiding: false,
          meta: { label: "Client Name", cellClassName: "whitespace-normal", minWidth: "9rem" },
          cell: ({ row }) => (
            <Link
              href={`${basePath}/${row.original.clientId}`}
              className="font-medium text-(--button-accent) hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
            >
              {row.original.clientName}
            </Link>
          ),
        },
        {
          id: "balance",
          meta: {
            label: showInvested ? "General balance" : "Trust Balance",
            align: "end",
            headerClassName: "whitespace-normal",
          },
          cell: ({ row }) => (
            <MoneyCell
              amount={row.original.balance}
              currency={currency}
              className="text-foreground font-medium"
            />
          ),
        },
        moneyColumn("totalDeposits", "Total Deposits", (r) => r.totalDeposits, currency),
        moneyColumn("totalPayments", "Total Payments", (r) => r.totalPayments, currency),
        moneyColumn(
          "totalFeeTransfers",
          "Total Fee Transfers",
          (r) => r.totalFeeTransfers,
          currency
        ),
        moneyColumn("totalRefunds", "Total Refunds", (r) => r.totalRefunds, currency),
        ...(showPlaced
          ? [
              moneyColumn(
                "totalInvestmentPlacements",
                "Placed in s86(4)",
                (r) => r.totalInvestmentPlacements,
                currency
              ),
            ]
          : []),
        ...(showInvested
          ? [
              moneyColumn(
                "investedBalance",
                "Invested (s86(4))",
                (r) => r.investedBalance,
                currency
              ),
            ]
          : []),
        {
          id: "lastTransactionDate",
          meta: { label: "Last Transaction", cellClassName: "text-muted-foreground" },
          cell: ({ row }) =>
            row.original.lastTransactionDate
              ? formatLocalDate(row.original.lastTransactionDate)
              : "No transactions",
        },
      ]),
    [basePath, currency, showPlaced, showInvested]
  );

  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={getLedgerRowId}
      rowTestId={ledgerRowTestId}
      pagination={{
        pageIndex,
        pageCount,
        hrefFor: (index) => buildPageHref(basePath, query, index),
      }}
      toolbar={toolbar}
      columnVisibility={false}
      stickyHeader={false}
      frame={false}
      empty={empty}
      caption="Client Balances"
      tableTestId="client-ledgers-table"
    />
  );
}
