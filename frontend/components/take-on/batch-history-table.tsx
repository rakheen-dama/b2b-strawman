"use client";

import { useMemo, type ReactNode } from "react";
import Link from "next/link";
import { DataTable } from "@/components/data-table/data-table";
import { DateCell, StatusBadgeCell } from "@/components/data-table/cells";
import { defineColumns } from "@/components/data-table/define-columns";
import {
  TAKE_ON_BATCH_STATUS_LABEL,
  TAKE_ON_DATASET_LABEL,
  type TakeOnBatchStatus,
  type TakeOnBatchSummary,
} from "@/lib/types";

const STATUS_VARIANT: Record<TakeOnBatchStatus, "success" | "warning" | "neutral" | "outline"> = {
  UPLOADED: "outline",
  MAPPED: "outline",
  VALIDATED: "success",
  COMMITTING: "warning",
  COMPLETED: "success",
  CANCELLED: "neutral",
};

const getBatchId = (b: TakeOnBatchSummary) => b.id;

interface BatchHistoryTableProps {
  slug: string;
  batches: TakeOnBatchSummary[];
  /** Pre-rendered by the tab (server). */
  empty: ReactNode;
}

/** Take-on batches, newest first (Phase 93 653A); a row opens its wizard. */
export function BatchHistoryTable({ slug, batches, empty }: BatchHistoryTableProps) {
  const href = (id: string) => `/org/${slug}/settings/matters?tab=take-on&batch=${id}`;
  const columns = useMemo(
    () =>
      defineColumns<TakeOnBatchSummary>([
        {
          id: "name",
          enableHiding: false,
          meta: { label: "Batch", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <Link
              href={`/org/${slug}/settings/matters?tab=take-on&batch=${row.original.id}`}
              className="font-medium text-slate-950 hover:underline dark:text-slate-50"
              data-testid={`take-on-history-link-${row.original.id}`}
            >
              {row.original.name ?? `${TAKE_ON_DATASET_LABEL[row.original.dataset]} take-on`}
            </Link>
          ),
        },
        {
          id: "dataset",
          meta: { label: "Dataset" },
          cell: ({ row }) => TAKE_ON_DATASET_LABEL[row.original.dataset],
        },
        {
          id: "status",
          meta: { label: "Status" },
          cell: ({ row }) => (
            <StatusBadgeCell
              label={TAKE_ON_BATCH_STATUS_LABEL[row.original.status]}
              variant={STATUS_VARIANT[row.original.status]}
              data-testid={`take-on-history-status-${row.original.id}`}
            />
          ),
        },
        {
          id: "rows",
          meta: { label: "Rows (valid / errors)", align: "end" },
          cell: ({ row }) => (
            <span className="font-mono tabular-nums">
              {row.original.totalRows} ({row.original.validRows} / {row.original.errorRows})
            </span>
          ),
        },
        {
          id: "createdBy",
          meta: { label: "Uploaded by" },
          cell: ({ row }) => row.original.createdByName ?? "—",
        },
        {
          id: "createdAt",
          meta: { label: "Uploaded" },
          cell: ({ row }) => <DateCell value={row.original.createdAt} />,
        },
      ]),
    [slug]
  );

  return (
    <DataTable
      columns={columns}
      data={batches}
      getRowId={getBatchId}
      rowHref={(row) => href(row.id)}
      rowTestId="take-on-history-row"
      columnVisibility={false}
      pageSize={null}
      stickyHeader={false}
      frame={false}
      empty={empty}
      caption="Take-on batches"
      tableTestId="take-on-history"
    />
  );
}
