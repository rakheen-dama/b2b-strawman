"use client";

import { useMemo, type ReactNode } from "react";
import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { DataTable } from "@/components/data-table/data-table";
import { EmptyCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import { activityBadgeVariant, formatSnakeCase } from "@/components/collections/activity-format";
import { formatDateTime, formatDaysOverdue } from "@/lib/format";
import type { CollectionActivityResponse } from "@/lib/api/collections";

interface CollectionsHistoryDataTableProps {
  activities: CollectionActivityResponse[];
  slug: string;
  /** Pre-rendered empty state from the server table, shown instead of the table. */
  empty: ReactNode;
}

const getActivityRowId = (activity: CollectionActivityResponse) => activity.id;

const TIMESTAMP_CELL = "text-muted-foreground font-mono text-xs tabular-nums";

/**
 * The chase-history ledger on the `DataTable` kit (Phase 91 666B). The client half of
 * `CollectionsHistoryTable` (which stays a Server Component for its two RSC callers): it receives
 * serializable rows + the slug and builds the column defs itself (OQ #19). "Updated" is
 * `priority: "low"` (shown from a 67rem table frame).
 */
export function CollectionsHistoryDataTable({
  activities,
  slug,
  empty,
}: CollectionsHistoryDataTableProps) {
  const columns = useMemo(
    () =>
      defineColumns<CollectionActivityResponse>([
        {
          id: "stage",
          enableHiding: false,
          meta: { label: "Stage", cellClassName: "text-foreground font-medium" },
          cell: ({ row }) => formatSnakeCase(row.original.stage),
        },
        {
          id: "status",
          meta: { label: "Status", cellClassName: "whitespace-normal", minWidth: "8rem" },
          cell: ({ row }) => (
            <div className="flex flex-col gap-0.5">
              <Badge variant={activityBadgeVariant(row.original.status)}>
                {formatSnakeCase(row.original.status)}
              </Badge>
              {row.original.reason && (
                <span className="text-muted-foreground text-xs">
                  {formatSnakeCase(row.original.reason)}
                </span>
              )}
            </div>
          ),
        },
        {
          id: "overdue",
          meta: {
            label: "Overdue at action",
            align: "end",
            cellClassName: "text-foreground font-mono tabular-nums",
          },
          cell: ({ row }) => formatDaysOverdue(row.original.daysOverdueAtAction),
        },
        {
          id: "proposed",
          meta: { label: "Proposed", cellClassName: TIMESTAMP_CELL },
          cell: ({ row }) => formatDateTime(row.original.createdAt),
        },
        {
          id: "updated",
          meta: { label: "Updated", priority: "low", cellClassName: TIMESTAMP_CELL },
          cell: ({ row }) => formatDateTime(row.original.updatedAt),
        },
        {
          id: "gate",
          enableHiding: false,
          meta: { label: "Gate", align: "end" },
          cell: ({ row }) =>
            row.original.status === "PROPOSED" && row.original.gateId !== null ? (
              <Link
                href={`/org/${slug}/ai/reviews`}
                className="text-sm font-medium text-teal-600 hover:underline dark:text-teal-400"
              >
                Review
              </Link>
            ) : (
              <EmptyCell />
            ),
        },
      ]),
    [slug]
  );

  return (
    <DataTable
      columns={columns}
      data={activities}
      getRowId={getActivityRowId}
      empty={empty}
      pageSize={null}
      columnVisibility={false}
      stickyHeader={false}
    />
  );
}
