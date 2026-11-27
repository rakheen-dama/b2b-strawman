/**
 * Fee notes list column definitions for `DataTable` (Phase 91 666A, ADR-369).
 *
 * Imported ONLY by the "use client" `FeeNotesTable` (`fee-notes-table.tsx`): the column defs hold
 * functions (cells, `meta.csv`), which cannot cross the RSC boundary (Phase 90 OQ #19). No
 * directive: no hooks here; the importing client module puts it in the client bundle.
 *
 * Labels are the old table's headers. Dates are LocalDates rendered in a client component, so they
 * go through `formatDateInSA` (`DateCell`), which prints the same day the server's `formatDate` did.
 *
 * Issue/Due Date have no priority (shown at every width, as before at 1024: the table still fits a
 * 710px frame with Client ~197px); Currency is `lowest` (shown from an 80rem frame).
 */
import Link from "next/link";
import { CreditCard } from "lucide-react";
import { DateCell, MoneyCell } from "@/components/data-table/cells";
import { defineColumns, type DataTableColumn } from "@/components/data-table/define-columns";
import { BalanceDueHint } from "@/components/invoices/balance-due-hint";
import { BroughtForwardBadge } from "@/components/invoices/brought-forward-badge";
import { StatusBadge, invoiceStatusLabel } from "@/components/invoices/status-badge";
import { formatDateInSA } from "@/lib/format";
import type { InvoiceResponse } from "@/lib/types";

function csvDate(value: string | null): string | null {
  return value ? formatDateInSA(value) || null : null;
}

/** The fee notes list columns, in the old table's order. */
export function buildFeeNoteColumns(slug: string): DataTableColumn<InvoiceResponse>[] {
  return defineColumns<InvoiceResponse>([
    {
      id: "number",
      // The number link is the keyboard path and the e2e row filter: never hideable.
      enableHiding: false,
      meta: { label: "Fee note", csv: (row) => row.invoiceNumber ?? "Draft" },
      cell: ({ row }) => (
        <Link
          href={`/org/${slug}/invoices/${row.original.id}`}
          className="text-foreground font-medium hover:underline"
        >
          {row.original.invoiceNumber ?? "Draft"}
        </Link>
      ),
    },
    {
      id: "client",
      meta: {
        label: "Client",
        cellClassName: "text-muted-foreground whitespace-normal",
        // A floor for the only wrapping column: with both dates shown it is ~197px at 1024.
        minWidth: "8rem",
        csv: (row) => row.clientName,
      },
      cell: ({ row }) => row.original.clientName,
    },
    {
      id: "status",
      meta: { label: "Status", csv: (row) => invoiceStatusLabel(row.status) },
      cell: ({ row }) => (
        <div className="flex items-center gap-2">
          <StatusBadge status={row.original.status} />
          <BroughtForwardBadge takeOnBatchId={row.original.takeOnBatchId} />
          {row.original.paymentUrl && (
            <span title="Online payment enabled" className="text-teal-600 dark:text-teal-400">
              <CreditCard className="size-3.5" />
            </span>
          )}
        </div>
      ),
    },
    {
      id: "issueDate",
      meta: {
        label: "Issue Date",
        cellClassName: "text-muted-foreground",
        csv: (row) => csvDate(row.issueDate),
      },
      cell: ({ row }) => <DateCell value={row.original.issueDate} />,
    },
    {
      id: "dueDate",
      meta: {
        label: "Due Date",
        cellClassName: "text-muted-foreground",
        csv: (row) => csvDate(row.dueDate),
      },
      cell: ({ row }) => <DateCell value={row.original.dueDate} />,
    },
    {
      id: "total",
      meta: { label: "Total", align: "end", csv: (row) => row.total },
      cell: ({ row }) => (
        <>
          <MoneyCell
            amount={row.original.total}
            currency={row.original.currency}
            className="font-medium"
          />
          <BalanceDueHint invoice={row.original} />
        </>
      ),
    },
    {
      id: "currency",
      meta: {
        label: "Currency",
        priority: "lowest",
        cellClassName: "text-muted-foreground",
        csv: (row) => row.currency,
      },
      cell: ({ row }) => row.original.currency,
    },
  ]);
}
