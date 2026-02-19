"use client";

import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { isOverdue, formatLocalDate, formatComplianceDate } from "@/lib/format";
import type { DataRequestResponse, DataRequestStatus, DataRequestType } from "@/lib/types";

interface DataRequestTableProps {
  requests: DataRequestResponse[];
  slug: string;
}

export type BadgeVariant = "success" | "warning" | "destructive" | "neutral";

export interface StatusConfig {
  label: string;
  variant: BadgeVariant;
  className?: string;
}

export const STATUS_CONFIG: Record<DataRequestStatus, StatusConfig> = {
  RECEIVED: { label: "Received", variant: "neutral" },
  IN_PROGRESS: {
    label: "In Progress",
    variant: "neutral",
    className: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
  },
  COMPLETED: { label: "Completed", variant: "success" },
  REJECTED: { label: "Rejected", variant: "destructive" },
};

const TYPE_LABELS: Record<DataRequestType, string> = {
  ACCESS: "Access",
  DELETION: "Deletion",
  CORRECTION: "Correction",
  OBJECTION: "Objection",
};

export function DataRequestTable({ requests, slug }: DataRequestTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead>
          <tr className="border-b border-slate-200 dark:border-slate-800">
            <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Customer
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Type
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Status
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Deadline
            </th>
            <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
              Requested
            </th>
          </tr>
        </thead>
        <tbody>
          {requests.map((request) => {
            const overdue = isOverdue(request.deadline) && request.status !== "COMPLETED" && request.status !== "REJECTED";
            const statusConfig = STATUS_CONFIG[request.status] ?? {
              label: request.status,
              variant: "neutral" as BadgeVariant,
            };

            return (
              <tr
                key={request.id}
                className="group border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
              >
                <td className="px-4 py-3">
                  <Link
                    href={`/org/${slug}/compliance/requests/${request.id}`}
                    className="font-medium text-slate-950 hover:underline dark:text-slate-50"
                  >
                    {request.customerName}
                  </Link>
                </td>
                <td className="px-4 py-3">
                  <Badge variant="neutral">
                    {TYPE_LABELS[request.requestType] ?? request.requestType}
                  </Badge>
                </td>
                <td className="px-4 py-3">
                  <Badge variant={statusConfig.variant} className={statusConfig.className}>
                    {statusConfig.label}
                  </Badge>
                </td>
                <td className="px-4 py-3">
                  <span
                    className={cn(
                      "text-sm",
                      overdue
                        ? "text-red-600 dark:text-red-400 font-medium"
                        : "text-slate-700 dark:text-slate-300",
                    )}
                  >
                    {formatLocalDate(request.deadline)}
                    {overdue && " (Overdue)"}
                  </span>
                </td>
                <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                  {formatComplianceDate(request.requestedAt)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
