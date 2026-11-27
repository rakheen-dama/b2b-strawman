"use client";

import { useMemo, type ReactNode } from "react";
import Link from "next/link";
import { DataTable } from "@/components/data-table/data-table";
import { DateCell, EmptyCell, MoneyCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import { BillingRunStatusBadge } from "@/components/billing-runs/billing-run-status-badge";
import { billingRunTitle } from "@/components/billing-runs/billing-run-title";
import { formatLocalDate } from "@/lib/format";
import type { BillingRun } from "@/lib/api/billing-runs";

interface BillingRunsTableProps {
  slug: string;
  runs: BillingRun[];
  /** Pre-rendered empty state (server), shown instead of the table. */
  empty: ReactNode;
}

const getRunId = (run: BillingRun) => run.id;

/**
 * Fee runs list on `DataTable` (Phase 91 666A, client mode). Every run renders (no pager, as
 * before); the Name link opens the detail (no row click, as before). Labels are the old headers.
 */
export function BillingRunsTable({ slug, runs, empty }: BillingRunsTableProps) {
  const columns = useMemo(
    () =>
      defineColumns<BillingRun>([
        {
          id: "name",
          enableHiding: false,
          meta: { label: "Name", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <Link
              href={`/org/${slug}/invoices/billing-runs/${row.original.id}`}
              className="text-foreground font-medium hover:underline"
            >
              {billingRunTitle(row.original)}
            </Link>
          ),
        },
        {
          id: "status",
          meta: { label: "Status" },
          cell: ({ row }) => <BillingRunStatusBadge status={row.original.status} />,
        },
        {
          id: "period",
          meta: { label: "Period", cellClassName: "text-muted-foreground" },
          cell: ({ row }) => (
            <>
              {formatLocalDate(row.original.periodFrom)} &ndash;{" "}
              {formatLocalDate(row.original.periodTo)}
            </>
          ),
        },
        {
          id: "clients",
          meta: { label: "Clients", align: "end", cellClassName: "text-muted-foreground" },
          cell: ({ row }) => row.original.totalClients ?? <EmptyCell />,
        },
        {
          id: "feeNotes",
          meta: { label: "Fee notes", align: "end", cellClassName: "text-muted-foreground" },
          cell: ({ row }) => row.original.totalInvoices ?? <EmptyCell />,
        },
        {
          id: "total",
          meta: { label: "Total", align: "end" },
          cell: ({ row }) =>
            row.original.totalAmount != null ? (
              <MoneyCell
                amount={row.original.totalAmount}
                currency={row.original.currency}
                className="font-medium"
              />
            ) : (
              <EmptyCell />
            ),
        },
        {
          id: "createdAt",
          meta: { label: "Created", cellClassName: "text-muted-foreground" },
          // An instant rendered in a client component: the South African calendar date.
          cell: ({ row }) => <DateCell value={row.original.createdAt} />,
        },
      ]),
    [slug]
  );

  return (
    <DataTable
      columns={columns}
      data={runs}
      getRowId={getRunId}
      empty={empty}
      pageSize={null}
      columnVisibility={false}
      stickyHeader={false}
    />
  );
}
