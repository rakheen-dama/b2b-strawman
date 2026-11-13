"use client";

import { useMemo, type ReactNode } from "react";
import { ArrowDownLeft, ArrowUpRight } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { DateCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import { formatCurrency } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  isInflowType,
  transactionTypeLabel,
  type TrustTransaction,
  type TrustTransactionStatus,
} from "@/lib/types";

interface TrustTransactionsTableProps {
  /** The dashboard's recent transactions, passed through untouched (data identity = navigation). */
  rows: TrustTransaction[];
  currency: string;
  /** Pre-rendered by the server page: "No transactions recorded yet". */
  empty: ReactNode;
}

function statusBadgeVariant(
  status: TrustTransactionStatus
): "success" | "warning" | "destructive" | "neutral" {
  switch (status) {
    case "APPROVED":
      return "success";
    case "AWAITING_APPROVAL":
      return "warning";
    case "REJECTED":
      return "destructive";
    case "RECORDED":
    case "REVERSED":
    default:
      return "neutral";
  }
}

const getTransactionRowId = (row: TrustTransaction) => row.id;

/**
 * Trust dashboard "Recent Transactions" on `DataTable` (Phase 90 643A). The server page passes only
 * serializable props; the column functions are built here (OQ #19). Column order Date, Reference,
 * Type, Amount, Status is unchanged. Money in = success token + down-left arrow, money out (REFUND
 * included, G88-F23) = foreground + up-right arrow; `data-direction` states it for tests.
 */
export function TrustTransactionsTable({ rows, currency, empty }: TrustTransactionsTableProps) {
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
          meta: { label: "Reference" },
          cell: ({ row }) => (
            <span className="text-foreground font-medium">{row.original.reference}</span>
          ),
        },
        {
          id: "type",
          meta: { label: "Type" },
          cell: ({ row }) => (
            <Badge variant="neutral">{transactionTypeLabel(row.original.transactionType)}</Badge>
          ),
        },
        {
          id: "amount",
          meta: { label: "Amount", align: "end" },
          cell: ({ row }) => {
            const inflow = isInflowType(row.original.transactionType);
            return (
              <span
                data-direction={inflow ? "in" : "out"}
                className={cn(
                  "inline-flex items-center gap-1 font-mono tabular-nums",
                  inflow ? "text-success-foreground" : "text-foreground"
                )}
              >
                {inflow ? (
                  <ArrowDownLeft aria-hidden="true" className="size-3" />
                ) : (
                  <ArrowUpRight aria-hidden="true" className="size-3" />
                )}
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
      ]),
    [currency]
  );

  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={getTransactionRowId}
      columnVisibility={false}
      pageSize={null}
      stickyHeader={false}
      frame={false}
      empty={empty}
      caption="Recent trust transactions"
      tableTestId="transactions-table"
    />
  );
}
