"use client";

import { useRouter } from "next/navigation";
import { Receipt } from "lucide-react";

import type { InvoiceResponse, InvoiceStatus } from "@/lib/types";
import { cn } from "@/lib/utils";
import { DataTable } from "@/components/ui/data-table";
import { DataTableEmpty } from "@/components/ui/data-table-empty";
import { invoiceColumns, isInvoiceOverdue } from "./invoice-columns";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table";

interface InvoiceListProps {
  invoices: InvoiceResponse[];
  orgSlug: string;
  activeStatus?: InvoiceStatus | "ALL";
}

const STATUS_TABS: { label: string; value: InvoiceStatus | "ALL" }[] = [
  { label: "All", value: "ALL" },
  { label: "Draft", value: "DRAFT" },
  { label: "Approved", value: "APPROVED" },
  { label: "Sent", value: "SENT" },
  { label: "Paid", value: "PAID" },
  { label: "Void", value: "VOID" },
];

export function InvoiceList({
  invoices,
  orgSlug,
  activeStatus = "ALL",
}: InvoiceListProps) {
  const router = useRouter();

  const filtered =
    activeStatus === "ALL"
      ? invoices
      : invoices.filter((inv) => inv.status === activeStatus);

  // Count per status for tab badges
  const counts: Record<string, number> = { ALL: invoices.length };
  for (const inv of invoices) {
    counts[inv.status] = (counts[inv.status] ?? 0) + 1;
  }

  const table = useReactTable({
    data: filtered,
    columns: invoiceColumns,
    getCoreRowModel: getCoreRowModel(),
  });

  return (
    <div className="space-y-4">
      {/* Status filter tabs */}
      <div className="flex gap-1 overflow-x-auto border-b border-slate-200">
        {STATUS_TABS.map((tab) => {
          const isActive = tab.value === activeStatus;
          const count = counts[tab.value] ?? 0;
          return (
            <button
              key={tab.value}
              onClick={() => {
                const base = `/org/${orgSlug}/invoices`;
                if (tab.value === "ALL") {
                  router.push(base);
                } else {
                  router.push(`${base}?status=${tab.value}`);
                }
              }}
              className={cn(
                "relative whitespace-nowrap px-3 pb-2.5 pt-1 text-sm font-medium transition-colors",
                isActive
                  ? "text-slate-900"
                  : "text-slate-500 hover:text-slate-700",
              )}
            >
              {tab.label}
              {count > 0 && (
                <span
                  className={cn(
                    "ml-1.5 inline-flex min-w-[18px] items-center justify-center rounded-full px-1 text-[10px] font-medium",
                    isActive
                      ? "bg-slate-200 text-slate-700"
                      : "bg-slate-100 text-slate-500",
                  )}
                >
                  {count}
                </span>
              )}
              {isActive && (
                <span className="absolute inset-x-0 -bottom-px h-0.5 bg-teal-600" />
              )}
            </button>
          );
        })}
      </div>

      {/* Table with overdue row tinting */}
      {filtered.length === 0 ? (
        <DataTableEmpty
          icon={<Receipt />}
          title="No invoices found"
          description={
            activeStatus === "ALL"
              ? "Create your first invoice to get started."
              : `No ${activeStatus.toLowerCase()} invoices.`
          }
        />
      ) : (
        <Table>
          <TableHeader className="sticky top-0 z-10 bg-white dark:bg-slate-950">
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead
                    key={header.id}
                    style={{
                      width:
                        header.getSize() !== 150
                          ? header.getSize()
                          : undefined,
                    }}
                  >
                    {header.isPlaceholder
                      ? null
                      : flexRender(
                          header.column.columnDef.header,
                          header.getContext(),
                        )}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.map((row) => {
              const overdue = isInvoiceOverdue(row.original);
              return (
                <TableRow
                  key={row.id}
                  className={cn(
                    "cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900",
                    overdue && "bg-amber-50 hover:bg-amber-100/70 dark:bg-amber-950/20",
                  )}
                  onClick={() =>
                    router.push(
                      `/org/${orgSlug}/invoices/${row.original.id}`,
                    )
                  }
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id}>
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext(),
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
    </div>
  );
}
