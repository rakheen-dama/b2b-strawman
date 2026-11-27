"use client";

import { useMemo, type ReactNode } from "react";
import Link from "next/link";
import { DataTable, type DataTableToolbarSlots } from "@/components/data-table/data-table";
import { MoneyCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import { formatCurrency } from "@/lib/format";
import type { CustomerUnbilledSummary } from "@/lib/api/billing-runs";

interface UnbilledWorkTableProps {
  rows: CustomerUnbilledSummary[];
  currency: string;
  slug: string;
  /** Pre-rendered toolbar slots (the period/currency filter form) from the server page. */
  toolbar?: DataTableToolbarSlots;
  /** Pre-rendered empty state (fetch error / no rows), shown instead of the table. */
  empty: ReactNode;
}

const getRowId = (row: CustomerUnbilledSummary) => row.clientId;

/**
 * Per-client unbilled time + expenses (GET /api/invoices/unbilled-summary), Phase 87 612B; on
 * `DataTable` since Phase 91 666A (client mode, every row). The grand total sits in the kit's
 * `footer` slot (rendered only with rows, as the old `<tfoot>` was).
 * Not `unbilled-summary.tsx` — that renders one client's entry-level detail (UnbilledTimeResponse).
 */
export function UnbilledWorkTable({
  rows,
  currency,
  slug,
  toolbar,
  empty,
}: UnbilledWorkTableProps) {
  const total = useMemo(() => rows.reduce((sum, r) => sum + r.totalUnbilledAmount, 0), [rows]);
  const columns = useMemo(
    () =>
      defineColumns<CustomerUnbilledSummary>([
        {
          id: "client",
          enableHiding: false,
          meta: { label: "Client", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <Link
              href={`/org/${slug}/clients/${row.original.clientId}`}
              className="text-foreground font-medium hover:underline"
            >
              {row.original.clientName}
            </Link>
          ),
        },
        {
          id: "time",
          meta: { label: "Time", align: "end" },
          cell: ({ row }) => (
            <>
              <MoneyCell amount={row.original.unbilledTimeAmount} currency={currency} />
              <span className="text-muted-foreground ml-1 text-xs">
                ({row.original.unbilledTimeEntryCount})
              </span>
            </>
          ),
        },
        {
          id: "expenses",
          meta: { label: "Expenses", align: "end" },
          cell: ({ row }) => (
            <>
              <MoneyCell amount={row.original.unbilledExpenseAmount} currency={currency} />
              <span className="text-muted-foreground ml-1 text-xs">
                ({row.original.unbilledExpenseCount})
              </span>
            </>
          ),
        },
        {
          id: "total",
          meta: { label: "Total", align: "end" },
          cell: ({ row }) => (
            <MoneyCell
              amount={row.original.totalUnbilledAmount}
              currency={currency}
              className="font-medium"
            />
          ),
        },
        {
          id: "readyToBill",
          meta: {
            label: "Ready to bill",
            cellClassName: "text-muted-foreground whitespace-normal",
          },
          cell: ({ row }) =>
            row.original.hasPrerequisiteIssues
              ? (row.original.prerequisiteIssueReason ?? "Missing details")
              : "Yes",
        },
      ]),
    [currency, slug]
  );

  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={getRowId}
      toolbar={toolbar}
      empty={empty}
      footer={
        <div className="flex justify-end gap-3 px-2 text-sm">
          <span className="font-semibold">Total</span>
          <span data-testid="unbilled-grand-total" className="font-mono font-semibold tabular-nums">
            {formatCurrency(total, currency)}
          </span>
        </div>
      }
      caption="Unbilled time and expenses per client"
      tableTestId="unbilled-work-table"
      pageSize={null}
      columnVisibility={false}
      stickyHeader={false}
    />
  );
}
