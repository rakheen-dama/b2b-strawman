"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { MoreHorizontal } from "lucide-react";

import type { InvoiceResponse } from "@/lib/types";
import { formatCurrency, formatLocalDate, isOverdue } from "@/lib/format";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

export const invoiceColumns: ColumnDef<InvoiceResponse, unknown>[] = [
  {
    accessorKey: "invoiceNumber",
    header: "Invoice #",
    cell: ({ row }) => (
      <span className="font-mono text-sm tabular-nums">
        {row.original.invoiceNumber ?? "Draft"}
      </span>
    ),
    size: 120,
  },
  {
    accessorKey: "customerName",
    header: "Customer",
    cell: ({ row }) => (
      <span className="text-sm font-medium text-slate-900">
        {row.original.customerName}
      </span>
    ),
  },
  {
    accessorKey: "status",
    header: "Status",
    cell: ({ row }) => {
      const status = row.original.status;
      const due = row.original.dueDate;
      const showOverdue =
        status === "SENT" && due && isOverdue(due);
      return (
        <StatusBadge status={showOverdue ? "OVERDUE" : status} />
      );
    },
    size: 110,
  },
  {
    accessorKey: "total",
    header: () => <div className="text-right">Amount</div>,
    cell: ({ row }) => (
      <div className="text-right font-mono text-sm tabular-nums">
        {formatCurrency(row.original.total, row.original.currency)}
      </div>
    ),
    size: 130,
  },
  {
    accessorKey: "dueDate",
    header: "Due Date",
    cell: ({ row }) => (
      <span className="text-sm text-slate-600">
        {row.original.dueDate ? formatLocalDate(row.original.dueDate) : "\u2014"}
      </span>
    ),
    size: 120,
  },
  {
    accessorKey: "issueDate",
    header: "Issued",
    cell: ({ row }) => (
      <span className="text-sm text-slate-500">
        {row.original.issueDate
          ? formatLocalDate(row.original.issueDate)
          : "\u2014"}
      </span>
    ),
    size: 120,
  },
  {
    id: "actions",
    cell: ({ row }) => (
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            size="icon-sm"
            onClick={(e) => e.stopPropagation()}
          >
            <MoreHorizontal className="size-4" />
            <span className="sr-only">Actions</span>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem
            onClick={(e) => {
              e.stopPropagation();
              // View handled by row click
            }}
          >
            View invoice
          </DropdownMenuItem>
          {row.original.status === "DRAFT" && (
            <DropdownMenuItem onClick={(e) => e.stopPropagation()}>
              Edit
            </DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
    ),
    size: 50,
    enableSorting: false,
  },
];

/**
 * Returns true if this invoice row should show overdue tinting.
 */
export function isInvoiceOverdue(invoice: InvoiceResponse): boolean {
  return (
    invoice.status === "SENT" &&
    !!invoice.dueDate &&
    isOverdue(invoice.dueDate)
  );
}
