"use client";

import Link from "next/link";
import type { ProposalSummaryDto } from "@/lib/types/proposal";

interface ProposalsAttentionListProps {
  summary: ProposalSummaryDto;
  slug: string;
}

export function ProposalsAttentionList({
  summary,
  slug,
}: ProposalsAttentionListProps) {
  const overdue = summary.pendingOverdue;

  if (overdue.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-6 text-center dark:border-slate-800 dark:bg-slate-950">
        <p className="text-sm text-slate-500 dark:text-slate-400">
          No overdue proposals. All caught up!
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <h2 className="text-sm font-medium text-slate-600 dark:text-slate-400">
        Needs Attention
      </h2>
      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
        <table className="w-full">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-800">
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Customer
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Project
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                Days Overdue
              </th>
            </tr>
          </thead>
          <tbody>
            {overdue.map((item) => (
              <tr
                key={item.id}
                className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
              >
                <td className="px-4 py-3">
                  <Link
                    href={`/org/${slug}/proposals/${item.id}`}
                    className="font-medium text-slate-900 hover:text-teal-600 dark:text-slate-100 dark:hover:text-teal-400"
                  >
                    {item.customerName}
                  </Link>
                </td>
                <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                  {item.projectName ?? "\u2014"}
                </td>
                <td className="px-4 py-3 text-sm font-medium text-amber-600 dark:text-amber-400">
                  {item.daysSinceSent} days
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
