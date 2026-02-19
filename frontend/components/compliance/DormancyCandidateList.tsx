"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { markCustomerDormant, type DormancyCandidate } from "@/app/(app)/org/[slug]/compliance/actions";
import { cn } from "@/lib/utils";

interface DormancyCandidateListProps {
  candidates: DormancyCandidate[];
  orgSlug: string;
}

export function DormancyCandidateList({ candidates, orgSlug }: DormancyCandidateListProps) {
  const router = useRouter();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState<Set<string>>(new Set());

  if (candidates.length === 0) {
    return <p className="text-sm text-slate-500 dark:text-slate-400">No dormant customers detected.</p>;
  }

  function toggleSelect(customerId: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(customerId)) {
        next.delete(customerId);
      } else {
        next.add(customerId);
      }
      return next;
    });
  }

  function toggleAll() {
    if (selected.size === candidates.length) {
      setSelected(new Set());
    } else {
      setSelected(new Set(candidates.map((c) => c.customerId)));
    }
  }

  async function handleMarkDormant(customerId: string) {
    setLoading((prev) => new Set(prev).add(customerId));
    const result = await markCustomerDormant(customerId, orgSlug);
    if (result.success) {
      router.refresh();
    }
    setLoading((prev) => {
      const next = new Set(prev);
      next.delete(customerId);
      return next;
    });
  }

  async function handleBulkMark() {
    const ids = Array.from(selected);
    await Promise.allSettled(ids.map((id) => handleMarkDormant(id)));
    setSelected(new Set());
  }

  function formatActivityDate(dateStr: string | null): string {
    if (!dateStr) return "No activity";
    return new Date(dateStr).toLocaleDateString("en-ZA", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  }

  return (
    <div className="space-y-3">
      {selected.size > 0 && (
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-600 dark:text-slate-400">
            {selected.size} selected
          </span>
          <button
            onClick={handleBulkMark}
            className="rounded-full bg-amber-600 px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-amber-700"
          >
            Mark Selected as Dormant
          </button>
        </div>
      )}
      <div className="overflow-x-auto rounded-lg border border-slate-200 dark:border-slate-800">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900">
              <th className="px-4 py-3 text-left">
                <input
                  type="checkbox"
                  checked={selected.size === candidates.length}
                  onChange={toggleAll}
                  className="rounded border-slate-300 dark:border-slate-600"
                />
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Customer
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Last Activity
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Days Since Activity
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Action
              </th>
            </tr>
          </thead>
          <tbody>
            {candidates.map((candidate) => (
              <tr
                key={candidate.customerId}
                className="border-b border-slate-100 last:border-0 dark:border-slate-800"
              >
                <td className="px-4 py-3">
                  <input
                    type="checkbox"
                    checked={selected.has(candidate.customerId)}
                    onChange={() => toggleSelect(candidate.customerId)}
                    className="rounded border-slate-300 dark:border-slate-600"
                  />
                </td>
                <td className="px-4 py-3">
                  <Link
                    href={`/org/${orgSlug}/customers/${candidate.customerId}`}
                    className="font-medium text-slate-950 hover:text-teal-600 dark:text-slate-50"
                  >
                    {candidate.customerName}
                  </Link>
                </td>
                <td className="px-4 py-3 text-slate-600 dark:text-slate-400">
                  {formatActivityDate(candidate.lastActivityDate)}
                </td>
                <td className="px-4 py-3">
                  <span
                    className={cn(
                      "font-mono tabular-nums",
                      candidate.daysSinceActivity > 90
                        ? "text-red-600 dark:text-red-400"
                        : "text-slate-600 dark:text-slate-400",
                    )}
                  >
                    {candidate.daysSinceActivity}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <button
                    onClick={() => handleMarkDormant(candidate.customerId)}
                    disabled={loading.has(candidate.customerId)}
                    className="rounded-full bg-slate-900 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-slate-700 disabled:opacity-50 dark:bg-slate-100 dark:text-slate-900"
                  >
                    {loading.has(candidate.customerId) ? "Marking..." : "Mark as Dormant"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
