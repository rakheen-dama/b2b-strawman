"use client";

import { useState, useTransition, useCallback, useEffect, useMemo, useRef } from "react";
import { History } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { cn } from "@/lib/utils";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { DateCell } from "@/components/data-table/cells";
import { EmptyState } from "@/components/empty-state";
import { ResolveConflictDialog } from "@/components/legal/resolve-conflict-dialog";
import {
  fetchConflictChecks,
  type ConflictCheckFilters,
} from "@/app/(app)/org/[slug]/conflict-check/actions";
import type {
  ConflictCheck,
  ConflictCheckResult as ConflictCheckResultType,
  ConflictCheckType,
} from "@/lib/types";

function resultBadge(result: ConflictCheckResultType) {
  switch (result) {
    case "NO_CONFLICT":
      return (
        <Badge className="bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300">
          No Conflict
        </Badge>
      );
    case "POTENTIAL_CONFLICT":
      return (
        <Badge className="bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300">
          Potential
        </Badge>
      );
    case "CONFLICT_FOUND":
      return (
        <Badge className="bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300">
          Conflict
        </Badge>
      );
  }
}

function resolutionLabel(resolution: string | null) {
  if (!resolution) return "-";
  return resolution.replace(/_/g, " ");
}

interface ConflictCheckHistoryProps {
  initialChecks: ConflictCheck[];
  initialTotal: number;
  slug: string;
}

export function ConflictCheckHistory({
  initialChecks,
  initialTotal,
  slug,
}: ConflictCheckHistoryProps) {
  const [checks, setChecks] = useState(initialChecks);
  const [total, setTotal] = useState(initialTotal);
  const [isPending, startTransition] = useTransition();

  // Filter state
  const [resultFilter, setResultFilter] = useState<ConflictCheckResultType | "">("");
  const [typeFilter, setTypeFilter] = useState<ConflictCheckType | "">("");

  // Resolve dialog
  const [resolveTarget, setResolveTarget] = useState<ConflictCheck | null>(null);

  const refetch = useCallback(() => {
    startTransition(async () => {
      try {
        const filters: ConflictCheckFilters = {};
        if (resultFilter) filters.result = resultFilter;
        if (typeFilter) filters.checkType = typeFilter;
        const res = await fetchConflictChecks(filters);
        setChecks(res?.content ?? []);
        setTotal(res?.page?.totalElements ?? 0);
      } catch (err) {
        console.error("Failed to refetch conflict checks:", err);
      }
    });
  }, [resultFilter, typeFilter]);

  // Refetch when filters change
  const isInitialMount = useRef(true);
  useEffect(() => {
    if (isInitialMount.current) {
      isInitialMount.current = false;
      return;
    }
    refetch();
  }, [resultFilter, typeFilter, refetch]);

  const columns = useMemo(
    () =>
      defineColumns<ConflictCheck>([
        {
          id: "date",
          meta: { label: "Date", cellClassName: "text-muted-foreground" },
          // DA-8: client-rendered dates on the South African calendar (formatDateInSA).
          cell: ({ row }) => <DateCell value={row.original.checkedAt} />,
        },
        {
          id: "checkedName",
          enableHiding: false,
          meta: {
            label: "Checked Name",
            cellClassName: "text-foreground font-medium whitespace-normal",
          },
          cell: ({ row }) => row.original.checkedName,
        },
        {
          id: "type",
          meta: { label: "Type", cellClassName: "text-muted-foreground" },
          cell: ({ row }) => row.original.checkType.replace(/_/g, " "),
        },
        {
          id: "result",
          meta: { label: "Result" },
          cell: ({ row }) => resultBadge(row.original.result),
        },
        {
          id: "resolution",
          meta: { label: "Resolution", cellClassName: "text-muted-foreground whitespace-normal" },
          cell: ({ row }) => resolutionLabel(row.original.resolution),
        },
        {
          id: "actions",
          enableHiding: false,
          meta: { label: "Actions", align: "end" },
          cell: ({ row }) =>
            row.original.result !== "NO_CONFLICT" && !row.original.resolution ? (
              <button
                type="button"
                className="text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
                onClick={() => setResolveTarget(row.original)}
              >
                Resolve
              </button>
            ) : null,
        },
      ]),
    []
  );

  return (
    <div data-testid="conflict-check-history" className="space-y-4">
      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3">
        <select
          value={resultFilter}
          onChange={(e) => setResultFilter(e.target.value as ConflictCheckResultType | "")}
          className="dark:bg-input/30 flex h-9 rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm dark:border-slate-700"
        >
          <option value="">All Results</option>
          <option value="NO_CONFLICT">No Conflict</option>
          <option value="POTENTIAL_CONFLICT">Potential Conflict</option>
          <option value="CONFLICT_FOUND">Conflict Found</option>
        </select>

        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value as ConflictCheckType | "")}
          className="dark:bg-input/30 flex h-9 rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm dark:border-slate-700"
        >
          <option value="">All Types</option>
          <option value="NEW_CLIENT">New Client</option>
          <option value="NEW_MATTER">New Matter</option>
          <option value="PERIODIC_REVIEW">Periodic Review</option>
        </select>

        <span className="text-muted-foreground ml-auto text-sm">
          {total} check{total !== 1 ? "s" : ""}
        </span>
      </div>

      {/* Table */}
      <div className={cn(isPending && "opacity-50 transition-opacity")}>
        <DataTable
          columns={columns}
          data={checks}
          getRowId={(check) => check.id}
          columnVisibility={false}
          stickyHeader={false}
          empty={
            <EmptyState
              variant="inline"
              icon={History}
              title="No conflict checks found."
              description={null}
            />
          }
        />
      </div>

      {resolveTarget && (
        <ResolveConflictDialog
          open={!!resolveTarget}
          onOpenChange={(open) => {
            if (!open) setResolveTarget(null);
          }}
          conflictCheckId={resolveTarget.id}
          slug={slug}
          onSuccess={() => {
            setResolveTarget(null);
            refetch();
          }}
        />
      )}
    </div>
  );
}
