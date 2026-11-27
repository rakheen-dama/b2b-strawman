"use client";

import { useMemo, type ReactNode } from "react";
import { ArrowDownLeft, ArrowUpRight } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { DataTable, type DataTableToolbarSlots } from "@/components/data-table/data-table";
import { DateCell, MoneyCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import { statusBadgeVariant } from "@/components/trust/trust-transactions-table";
import { buildPageHref } from "@/lib/page-href";
import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { TrustTransaction } from "@/lib/types";

interface ClientHistoryDataTableProps {
  /** The page the server sent (`historyPage.content`), passed through untouched. */
  rows: TrustTransaction[];
  currency: string;
  /** Zero-based (`historyPage.pageNumber`). */
  pageIndex: number;
  /** `historyPage.totalPages`; the pager shows from 2 pages. */
  pageCount: number;
  /** `/org/<slug>/trust-accounting/client-ledgers/<clientId>`. */
  basePath: string;
  /** Every string search param of the page; the pager keeps them all and sets `page`. */
  query: Record<string, string>;
  /** Pre-rendered toolbar slots (status pills, type pills, `ClientHistoryFilters`). */
  toolbar?: DataTableToolbarSlots;
  /** Pre-rendered empty state, shown instead of the table. */
  empty: ReactNode;
}

const getHistoryRowId = (row: TrustTransaction) => row.id;
const historyRowTestId = (row: TrustTransaction) => `history-row-${row.id}`;

/**
 * A client's trust ledger history on `DataTable` (Phase 91 667A, ADR-395 server paging). The server
 * page fetches and pre-renders the toolbar and empty state; this client wrapper receives only
 * serializable props and builds the columns, pager hrefs (`buildPageHref`, every param kept) and the
 * row testid itself. Running Balance is the server's (ADR-361): `tx.runningBalance` as-is, "—" when
 * null (an unposted row) — never computed here.
 */
export function ClientHistoryDataTable({
  rows,
  currency,
  pageIndex,
  pageCount,
  basePath,
  query,
  toolbar,
  empty,
}: ClientHistoryDataTableProps) {
  // Memoised: flexRender renders each cell function as a component, so a new column array would
  // remount every cell on each re-render.
  const columns = useMemo(
    () =>
      defineColumns<TrustTransaction>([
        {
          id: "transactionDate",
          meta: { label: "Date" },
          cell: ({ row }) => <DateCell value={row.original.transactionDate} kind="local" />,
        },
        {
          id: "reference",
          // K-10: a long reference wraps over a floor instead of widening the table past the card;
          // only tokens wider than the column break.
          meta: {
            label: "Reference",
            cellClassName: "whitespace-normal [overflow-wrap:anywhere]",
            minWidth: "9rem",
          },
          cell: ({ row }) => (
            <span className="text-foreground block max-w-md font-medium">
              {row.original.reference}
            </span>
          ),
        },
        {
          id: "type",
          // K-90: long registry labels wrap inside the badge.
          meta: { label: "Type", cellClassName: "whitespace-normal", minWidth: "10rem" },
          cell: ({ row }) => (
            <Badge variant="neutral" className="shrink rounded-md whitespace-normal">
              {row.original.typeLabel}
            </Badge>
          ),
        },
        {
          id: "amount",
          meta: { label: "Amount", align: "end" },
          cell: ({ row }) => {
            // 646E F8: styling from the row's direction; NEUTRAL draws no arrow
            const direction = row.original.direction;
            return (
              <span
                data-direction={direction.toLowerCase()}
                className={cn(
                  "inline-flex items-center gap-1 font-mono tabular-nums",
                  direction === "IN" ? "text-success-foreground" : "text-foreground"
                )}
              >
                {direction === "IN" && <ArrowDownLeft aria-hidden="true" className="size-3" />}
                {direction === "OUT" && <ArrowUpRight aria-hidden="true" className="size-3" />}
                {formatCurrency(row.original.amount, currency)}
              </span>
            );
          },
        },
        {
          id: "status",
          meta: { label: "Status" },
          cell: ({ row }) => (
            <Badge variant={statusBadgeVariant(row.original.status)}>
              {row.original.status.replace(/_/g, " ")}
            </Badge>
          ),
        },
        {
          id: "runningBalance",
          meta: { label: "Running Balance", align: "end", headerClassName: "whitespace-normal" },
          // ADR-361: server-computed; null on unposted rows only (647C #2)
          cell: ({ row }) => (
            <MoneyCell
              amount={row.original.runningBalance}
              currency={currency}
              className="text-foreground"
            />
          ),
        },
      ]),
    [currency]
  );

  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={getHistoryRowId}
      rowTestId={historyRowTestId}
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
      caption="Transaction History"
      tableTestId="history-table"
    />
  );
}
