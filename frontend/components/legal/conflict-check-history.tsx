"use client";

import { useState, useTransition, useCallback, useEffect, useRef } from "react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
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

  return (
    <div data-testid="conflict-check-history" className="space-y-4">
      {/* Filters */}
      <div className="flex flex-wrap items-center gap-3">
        <select
          value={resultFilter}
          onChange={(e) => setResultFilter(e.target.value as ConflictCheckResultType | "")}
          className="flex h-9 rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm dark:border-slate-800"
        >
          <option value="">All Results</option>
          <option value="NO_CONFLICT">No Conflict</option>
          <option value="POTENTIAL_CONFLICT">Potential Conflict</option>
          <option value="CONFLICT_FOUND">Conflict Found</option>
        </select>

        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value as ConflictCheckType | "")}
          className="flex h-9 rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-sm dark:border-slate-800"
        >
          <option value="">All Types</option>
          <option value="NEW_CLIENT">New Client</option>
          <option value="NEW_MATTER">New Matter</option>
          <option value="PERIODIC_REVIEW">Periodic Review</option>
        </select>

        <span className="ml-auto text-sm text-slate-500 dark:text-slate-400">
          {total} check{total !== 1 ? "s" : ""}
        </span>
      </div>

      {/* Table */}
      <div className={cn("overflow-x-auto", isPending && "opacity-50 transition-opacity")}>
        {checks.length === 0 ? (
          <div className="rounded-lg border border-slate-200 p-8 text-center dark:border-slate-800">
            <p className="text-sm text-slate-500 dark:text-slate-400">No conflict checks found.</p>
          </div>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Date
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Checked Name
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Type
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Result
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Resolution
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {checks.map((check) => (
                <tr
                  key={check.id}
                  className="border-b border-slate-100 last:border-0 dark:border-slate-800/50"
                >
                  <td className="px-4 py-3 font-mono text-sm text-slate-600 dark:text-slate-400">
                    {new Date(check.checkedAt).toLocaleDateString()}
                  </td>
                  <td className="px-4 py-3 font-medium text-slate-900 dark:text-slate-100">
                    {check.checkedName}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {check.checkType.replace(/_/g, " ")}
                  </td>
                  <td className="px-4 py-3">{resultBadge(check.result)}</td>
                  <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                    {resolutionLabel(check.resolution)}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {check.result !== "NO_CONFLICT" && !check.resolution && (
                      <button
                        className="text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
                        onClick={() => setResolveTarget(check)}
                      >
                        Resolve
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
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
