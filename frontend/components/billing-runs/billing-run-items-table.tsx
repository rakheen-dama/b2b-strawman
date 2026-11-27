"use client";

import { useMemo } from "react";
import Link from "next/link";
import { Layers } from "lucide-react";
import { DataTable } from "@/components/data-table/data-table";
import { EmptyCell, MoneyCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import { BillingRunItemStatusBadge } from "@/components/billing-runs/billing-run-item-status-badge";
import { EmptyState } from "@/components/empty-state";
import type { BillingRunItem } from "@/lib/api/billing-runs";

interface BillingRunItemsTableProps {
  items: BillingRunItem[];
  currency: string;
  slug: string;
}

const getItemId = (item: BillingRunItem) => item.id;

/** A fee run's items on `DataTable` (Phase 91 666A, client mode; every item, no row click). */
export function BillingRunItemsTable({ items, currency, slug }: BillingRunItemsTableProps) {
  const columns = useMemo(
    () =>
      defineColumns<BillingRunItem>([
        {
          id: "client",
          enableHiding: false,
          meta: { label: "Client", cellClassName: "font-medium whitespace-normal" },
          cell: ({ row }) => row.original.clientName,
        },
        {
          id: "status",
          meta: { label: "Status" },
          cell: ({ row }) => <BillingRunItemStatusBadge status={row.original.status} />,
        },
        {
          id: "timeAmount",
          meta: { label: "Time Amount", align: "end" },
          cell: ({ row }) => (
            <MoneyCell amount={row.original.unbilledTimeAmount} currency={currency} />
          ),
        },
        {
          id: "disbursementAmount",
          meta: { label: "Disbursement Amount", align: "end" },
          cell: ({ row }) => (
            <MoneyCell amount={row.original.unbilledExpenseAmount} currency={currency} />
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
          id: "feeNote",
          meta: { label: "Fee note" },
          cell: ({ row }) =>
            row.original.invoiceId ? (
              // K-34: the accent token (teal-700 in firm light) instead of teal-600.
              <Link
                href={`/org/${slug}/invoices/${row.original.invoiceId}`}
                className="text-(--button-accent) hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
              >
                View fee note
              </Link>
            ) : (
              <EmptyCell />
            ),
        },
        {
          id: "failureReason",
          meta: {
            label: "Failure Reason",
            cellClassName: "text-muted-foreground whitespace-normal",
          },
          cell: ({ row }) => row.original.failureReason ?? <EmptyCell />,
        },
      ]),
    [currency, slug]
  );

  return (
    <DataTable
      columns={columns}
      data={items}
      getRowId={getItemId}
      empty={
        <EmptyState
          variant="inline"
          icon={Layers}
          title="No items in this fee run."
          description={null}
        />
      }
      pageSize={null}
      columnVisibility={false}
      stickyHeader={false}
    />
  );
}
