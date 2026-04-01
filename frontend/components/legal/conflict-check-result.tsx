"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  CheckCircle2,
  AlertTriangle,
  XCircle,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { ResolveConflictDialog } from "@/components/legal/resolve-conflict-dialog";
import type {
  ConflictCheck,
  ConflictCheckResult,
  ConflictMatch,
} from "@/lib/types";

function resultIcon(result: ConflictCheckResult) {
  switch (result) {
    case "NO_CONFLICT":
      return <CheckCircle2 className="size-10 text-green-500" />;
    case "POTENTIAL_CONFLICT":
      return <AlertTriangle className="size-10 text-amber-500" />;
    case "CONFLICT_FOUND":
      return <XCircle className="size-10 text-red-500" />;
  }
}

function resultLabel(result: ConflictCheckResult) {
  switch (result) {
    case "NO_CONFLICT":
      return "No Conflict";
    case "POTENTIAL_CONFLICT":
      return "Potential Conflict";
    case "CONFLICT_FOUND":
      return "Conflict Found";
  }
}

function resultBadgeClass(result: ConflictCheckResult) {
  switch (result) {
    case "NO_CONFLICT":
      return "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300";
    case "POTENTIAL_CONFLICT":
      return "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300";
    case "CONFLICT_FOUND":
      return "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300";
  }
}

function matchTypeBadge(matchType: string) {
  switch (matchType) {
    case "NAME_SIMILARITY":
      return <Badge variant="neutral">Name Match</Badge>;
    case "ID_NUMBER_EXACT":
      return <Badge variant="neutral">ID Number</Badge>;
    case "REGISTRATION_NUMBER_EXACT":
      return <Badge variant="neutral">Reg. Number</Badge>;
    default:
      return <Badge variant="neutral">{matchType}</Badge>;
  }
}

interface ConflictCheckResultDisplayProps {
  result: ConflictCheck;
  slug: string;
  onResolved?: () => void;
}

export function ConflictCheckResultDisplay({
  result,
  slug,
  onResolved,
}: ConflictCheckResultDisplayProps) {
  const [resolveOpen, setResolveOpen] = useState(false);
  const matches = result.conflictsFound ?? [];

  return (
    <div
      data-testid="conflict-check-result"
      className={cn(
        "rounded-lg border p-6",
        result.result === "NO_CONFLICT" &&
          "border-green-200 bg-green-50 dark:border-green-900 dark:bg-green-950",
        result.result === "POTENTIAL_CONFLICT" &&
          "border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950",
        result.result === "CONFLICT_FOUND" &&
          "border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950"
      )}
    >
      {/* Header */}
      <div className="flex items-center gap-4">
        {resultIcon(result.result)}
        <div>
          <h3 className="text-lg font-semibold text-slate-900 dark:text-slate-100">
            {resultLabel(result.result)}
          </h3>
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Checked &quot;{result.checkedName}&quot; at{" "}
            {new Date(result.checkedAt).toLocaleString()}
          </p>
        </div>
        <Badge className={cn("ml-auto", resultBadgeClass(result.result))}>
          {resultLabel(result.result)}
        </Badge>
      </div>

      {/* Match details table */}
      {matches.length > 0 && (
        <div className="mt-4 overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-700">
                <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Party Name
                </th>
                <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Match Type
                </th>
                <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Score
                </th>
                <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Linked Matter
                </th>
                <th className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Relationship
                </th>
              </tr>
            </thead>
            <tbody>
              {matches.map((match: ConflictMatch, idx: number) => (
                <tr
                  key={idx}
                  className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                >
                  <td className="px-3 py-2 font-medium text-slate-900 dark:text-slate-100">
                    {match.adversePartyName}
                  </td>
                  <td className="px-3 py-2">{matchTypeBadge(match.matchType)}</td>
                  <td className="px-3 py-2 font-mono text-slate-600 dark:text-slate-400">
                    {(match.similarityScore * 100).toFixed(0)}%
                  </td>
                  <td className="px-3 py-2 text-slate-600 dark:text-slate-400">
                    {match.projectName}
                  </td>
                  <td className="px-3 py-2 text-slate-600 dark:text-slate-400">
                    {match.relationship.replace(/_/g, " ")}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Action buttons */}
      {result.result !== "NO_CONFLICT" && !result.resolution && (
        <div className="mt-4 flex flex-wrap gap-2">
          <Button
            size="sm"
            variant="outline"
            onClick={() => setResolveOpen(true)}
          >
            Resolve Conflict
          </Button>
        </div>
      )}

      {result.resolution && (
        <div className="mt-4 rounded-md bg-slate-100 p-3 dark:bg-slate-800">
          <p className="text-sm font-medium text-slate-700 dark:text-slate-300">
            Resolution: {result.resolution.replace(/_/g, " ")}
          </p>
          {result.resolutionNotes && (
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
              {result.resolutionNotes}
            </p>
          )}
        </div>
      )}

      <ResolveConflictDialog
        open={resolveOpen}
        onOpenChange={setResolveOpen}
        conflictCheckId={result.id}
        slug={slug}
        onSuccess={() => {
          setResolveOpen(false);
          onResolved?.();
        }}
      />
    </div>
  );
}
