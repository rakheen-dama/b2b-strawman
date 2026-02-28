"use client";

import type { ColumnDef } from "@tanstack/react-table";
import { MoreHorizontal } from "lucide-react";

import type { RetainerResponse } from "@/lib/api/retainers";
import { formatCurrency } from "@/lib/format";
import { StatusBadge } from "@/components/ui/status-badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

function formatTypeLabel(type: string): string {
  switch (type) {
    case "HOUR_BANK":
      return "Hour Bank";
    case "FIXED_FEE":
      return "Fixed Fee";
    default:
      return type;
  }
}

export const retainerColumns: ColumnDef<RetainerResponse, unknown>[] = [
  {
    accessorKey: "name",
    header: "Name",
    cell: ({ row }) => (
      <span className="text-sm font-medium text-slate-900">
        {row.original.name}
      </span>
    ),
  },
  {
    accessorKey: "customerName",
    header: "Customer",
    cell: ({ row }) => (
      <span className="text-sm text-slate-600">
        {row.original.customerName}
      </span>
    ),
  },
  {
    accessorKey: "type",
    header: "Type",
    cell: ({ row }) => (
      <span className="text-sm text-slate-600">
        {formatTypeLabel(row.original.type)}
      </span>
    ),
    size: 110,
  },
  {
    id: "consumed",
    header: "Consumed",
    cell: ({ row }) => {
      const period = row.original.currentPeriod;
      if (!period || period.allocatedHours == null) {
        return <span className="text-sm text-slate-400">{"\u2014"}</span>;
      }
      const pct =
        period.allocatedHours > 0
          ? Math.round(
              (period.consumedHours / period.allocatedHours) * 100,
            )
          : 0;
      const isOverage = pct > 100;
      return (
        <div className="flex items-center gap-2">
          <Progress
            value={Math.min(pct, 100)}
            className="h-2 w-16"
          />
          <span
            className={`text-xs font-medium tabular-nums ${isOverage ? "text-red-600" : "text-slate-600"}`}
          >
            {pct}%
          </span>
        </div>
      );
    },
    size: 140,
  },
  {
    id: "amount",
    header: () => <div className="text-right">Amount / Hours</div>,
    cell: ({ row }) => {
      const r = row.original;
      if (r.type === "FIXED_FEE" && r.periodFee != null) {
        return (
          <div className="text-right font-mono text-sm tabular-nums">
            {formatCurrency(r.periodFee, "ZAR")}
          </div>
        );
      }
      if (r.type === "HOUR_BANK" && r.allocatedHours != null) {
        return (
          <div className="text-right font-mono text-sm tabular-nums">
            {r.allocatedHours}h
          </div>
        );
      }
      return (
        <div className="text-right text-sm text-slate-400">{"\u2014"}</div>
      );
    },
    size: 130,
  },
  {
    accessorKey: "status",
    header: "Status",
    cell: ({ row }) => <StatusBadge status={row.original.status} />,
    size: 100,
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
          <DropdownMenuItem onClick={(e) => e.stopPropagation()}>
            View retainer
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    ),
    size: 50,
    enableSorting: false,
  },
];
