"use client";

import { useMemo } from "react";
import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { cn } from "@/lib/utils";
import { isOverdue, formatLocalDate, formatComplianceDate } from "@/lib/format";
import type { DataRequestResponse, DataRequestType } from "@/lib/types";
import { STATUS_CONFIG, type BadgeVariant } from "@/lib/compliance/data-request-status";

interface DataRequestTableProps {
  requests: DataRequestResponse[];
  slug: string;
}

const TYPE_LABELS: Record<DataRequestType, string> = {
  ACCESS: "Access",
  DELETION: "Deletion",
  CORRECTION: "Correction",
  OBJECTION: "Objection",
};

export function DataRequestTable({ requests, slug }: DataRequestTableProps) {
  const columns = useMemo(
    () =>
      defineColumns<DataRequestResponse>([
        {
          id: "client",
          enableHiding: false,
          meta: { label: "Client", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <Link
              href={`/org/${slug}/compliance/requests/${row.original.id}`}
              className="text-foreground font-medium hover:underline"
            >
              {row.original.clientName}
            </Link>
          ),
        },
        {
          id: "type",
          meta: { label: "Type" },
          cell: ({ row }) => (
            <Badge variant="neutral">
              {TYPE_LABELS[row.original.requestType] ?? row.original.requestType}
            </Badge>
          ),
        },
        {
          id: "status",
          meta: { label: "Status" },
          cell: ({ row }) => {
            const statusConfig = STATUS_CONFIG[row.original.status] ?? {
              label: row.original.status,
              variant: "neutral" as BadgeVariant,
            };
            return (
              <Badge variant={statusConfig.variant} className={statusConfig.className}>
                {statusConfig.label}
              </Badge>
            );
          },
        },
        {
          id: "deadline",
          meta: { label: "Deadline" },
          cell: ({ row }) => {
            const request = row.original;
            const overdue =
              isOverdue(request.deadline) &&
              request.status !== "COMPLETED" &&
              request.status !== "REJECTED";
            return (
              <span
                className={cn(
                  "text-sm",
                  overdue
                    ? "font-medium text-red-600 dark:text-red-400"
                    : "text-slate-700 dark:text-slate-300"
                )}
              >
                {formatLocalDate(request.deadline)}
                {overdue && " (Overdue)"}
              </span>
            );
          },
        },
        {
          id: "requested",
          meta: { label: "Requested", cellClassName: "text-muted-foreground text-sm" },
          cell: ({ row }) => formatComplianceDate(row.original.requestedAt),
        },
      ]),
    [slug]
  );

  return (
    <DataTable
      columns={columns}
      data={requests}
      getRowId={(request) => request.id}
      columnVisibility={false}
      stickyHeader={false}
      empty={null}
    />
  );
}
