"use client";

import { useMemo } from "react";
import type { ColumnDef } from "@tanstack/react-table";
import { cn } from "@/lib/utils";
import { formatCurrency, formatCurrencySafe } from "@/lib/format";
import { Badge } from "@/components/ui/badge";
import { DataTable } from "@/components/ui/data-table";
import type { ProjectProfitabilitySummary } from "@/lib/types";

interface MarginTableProps {
  data: ProjectProfitabilitySummary[];
  isLoading?: boolean;
}

function marginColorClass(percent: number | null): string {
  if (percent == null) return "";
  if (percent >= 30) return "text-emerald-600";
  if (percent >= 10) return "text-amber-600";
  return "text-red-600";
}

export function MarginTable({ data, isLoading }: MarginTableProps) {
  const columns = useMemo<ColumnDef<ProjectProfitabilitySummary, unknown>[]>(
    () => [
      {
        accessorKey: "projectName",
        header: "Project",
        cell: ({ row }) => (
          <span className="font-medium">{row.original.projectName}</span>
        ),
      },
      {
        accessorKey: "customerName",
        header: "Customer",
        cell: ({ row }) =>
          row.original.customerName ?? (
            <span className="text-slate-400">&mdash;</span>
          ),
      },
      {
        accessorKey: "currency",
        header: "Currency",
        enableSorting: false,
      },
      {
        accessorKey: "billableHours",
        header: "Billable Hours",
        cell: ({ row }) => (
          <span className="tabular-nums">
            {row.original.billableHours.toFixed(1)}h
          </span>
        ),
      },
      {
        accessorKey: "billableValue",
        header: "Revenue",
        cell: ({ row }) => (
          <span className="tabular-nums">
            {formatCurrency(
              row.original.billableValue,
              row.original.currency,
            )}
          </span>
        ),
      },
      {
        accessorKey: "costValue",
        header: "Cost",
        cell: ({ row }) => (
          <span className="tabular-nums">
            {formatCurrencySafe(row.original.costValue, row.original.currency)}
          </span>
        ),
      },
      {
        accessorKey: "margin",
        header: "Margin",
        cell: ({ row }) => {
          const { margin, currency } = row.original;
          if (margin == null) return <Badge variant="neutral">N/A</Badge>;
          return (
            <span
              className={cn(
                "tabular-nums font-medium",
                margin >= 0 ? "text-emerald-600" : "text-red-600",
              )}
            >
              {formatCurrency(margin, currency)}
            </span>
          );
        },
      },
      {
        accessorKey: "marginPercent",
        header: "Margin %",
        cell: ({ row }) => {
          const { marginPercent } = row.original;
          if (marginPercent == null)
            return <Badge variant="neutral">N/A</Badge>;
          return (
            <span
              className={cn(
                "tabular-nums font-semibold",
                marginColorClass(marginPercent),
              )}
            >
              {marginPercent.toFixed(1)}%
            </span>
          );
        },
      },
    ],
    [],
  );

  return (
    <DataTable
      columns={columns}
      data={data}
      isLoading={isLoading}
      emptyState={
        <p className="py-12 text-center text-sm text-slate-500">
          No profitability data for this period.
        </p>
      }
    />
  );
}
