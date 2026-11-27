"use client";

import { useMemo } from "react";
import Link from "next/link";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";

/** One Onboarding Pipeline row; `days` is computed by the server section (DA-7). */
export interface OnboardingPipelineRow {
  id: string;
  name: string;
  days: number;
  completed: number;
  total: number;
}

interface OnboardingPipelineTableProps {
  rows: OnboardingPipelineRow[];
  orgSlug: string;
}

function SimpleProgressBar({ completed, total }: { completed: number; total: number }) {
  const pct = total > 0 ? Math.round((completed / total) * 100) : 0;
  return (
    <div className="space-y-1">
      <div className="bg-muted h-2 w-full overflow-hidden rounded-full">
        <div
          className="h-full rounded-full bg-teal-500 transition-all"
          style={{ width: `${pct}%` }}
        />
      </div>
      <p className="text-muted-foreground text-xs">
        {completed}/{total} items
      </p>
    </div>
  );
}

/** The Compliance › Overview Onboarding Pipeline table, rows in the server's order. */
export function OnboardingPipelineTable({ rows, orgSlug }: OnboardingPipelineTableProps) {
  const columns = useMemo(
    () =>
      defineColumns<OnboardingPipelineRow>([
        {
          id: "client",
          enableHiding: false,
          meta: { label: "Client", cellClassName: "whitespace-normal" },
          cell: ({ row }) => (
            <Link
              href={`/org/${orgSlug}/clients/${row.original.id}`}
              className="text-foreground font-medium hover:text-teal-600"
            >
              {row.original.name}
            </Link>
          ),
        },
        {
          id: "duration",
          meta: { label: "Duration", cellClassName: "text-muted-foreground" },
          cell: ({ row }) => {
            const { days } = row.original;
            return `In onboarding ${days} ${days === 1 ? "day" : "days"}`;
          },
        },
        {
          id: "progress",
          meta: { label: "Progress", cellClassName: "w-48" },
          cell: ({ row }) => (
            <SimpleProgressBar completed={row.original.completed} total={row.original.total} />
          ),
        },
      ]),
    [orgSlug]
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
