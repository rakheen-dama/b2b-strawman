"use client";

import { useMemo } from "react";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { formatLocalDate } from "@/lib/format";
import { cn } from "@/lib/utils";

/** One open data request on the Compliance Overview; `daysUntil` is computed by the server (DA-7). */
export interface UrgentDataRequestRow {
  id: string;
  clientName: string;
  requestType: string;
  deadline: string;
  daysUntil: number;
  status: string;
}

interface UrgentDataRequestsTableProps {
  rows: UrgentDataRequestRow[];
}

function getDeadlineColor(daysUntil: number): string {
  if (daysUntil < 0) return "text-red-600 dark:text-red-400";
  if (daysUntil < 3) return "text-red-600 dark:text-red-400";
  if (daysUntil <= 7) return "text-amber-600 dark:text-amber-400";
  return "text-emerald-600 dark:text-emerald-400";
}

function formatDeadlineCountdown(daysUntil: number): string {
  if (daysUntil < 0) return `${Math.abs(daysUntil)}d overdue`;
  if (daysUntil === 0) return "Due today";
  return `${daysUntil}d remaining`;
}

/** The Compliance › Overview › Data Requests table (open requests), rows in the server's order. */
export function UrgentDataRequestsTable({ rows }: UrgentDataRequestsTableProps) {
  const columns = useMemo(
    () =>
      defineColumns<UrgentDataRequestRow>([
        {
          id: "client",
          enableHiding: false,
          meta: { label: "Client", cellClassName: "text-foreground font-medium whitespace-normal" },
          cell: ({ row }) => row.original.clientName,
        },
        {
          id: "type",
          meta: { label: "Type", cellClassName: "text-muted-foreground" },
          cell: ({ row }) => row.original.requestType,
        },
        {
          id: "deadline",
          meta: { label: "Deadline" },
          cell: ({ row }) => (
            <div className="flex flex-col">
              <span className="text-muted-foreground">
                {formatLocalDate(row.original.deadline)}
              </span>
              <span className={cn("text-xs font-medium", getDeadlineColor(row.original.daysUntil))}>
                {formatDeadlineCountdown(row.original.daysUntil)}
              </span>
            </div>
          ),
        },
        {
          id: "status",
          meta: { label: "Status" },
          cell: ({ row }) => <Badge variant="neutral">{row.original.status}</Badge>,
        },
      ]),
    []
  );

  return (
    <DataTable
      columns={columns}
      data={rows}
      getRowId={(row) => row.id}
      columnVisibility={false}
      stickyHeader={false}
      empty={null}
    />
  );
}
