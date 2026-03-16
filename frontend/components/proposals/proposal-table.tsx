"use client";

import { useState } from "react";
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { formatDate } from "@/lib/format";
import type { ProposalResponse, ProposalStatus } from "@/lib/types/proposal";

const STATUS_BADGE: Record<
  ProposalStatus,
  { label: string; variant: "neutral" | "lead" | "success" | "destructive" | "warning" }
> = {
  DRAFT: { label: "Draft", variant: "neutral" },
  SENT: { label: "Sent", variant: "lead" },
  ACCEPTED: { label: "Accepted", variant: "success" },
  DECLINED: { label: "Declined", variant: "destructive" },
  EXPIRED: { label: "Expired", variant: "warning" },
};

const STATUS_FILTERS: { label: string; value: ProposalStatus | "ALL" }[] = [
  { label: "All", value: "ALL" },
  { label: "Draft", value: "DRAFT" },
  { label: "Sent", value: "SENT" },
  { label: "Accepted", value: "ACCEPTED" },
  { label: "Declined", value: "DECLINED" },
  { label: "Expired", value: "EXPIRED" },
];

interface ProposalTableProps {
  proposals: ProposalResponse[];
  slug: string;
  now: number;
}

export function ProposalTable({ proposals, slug, now }: ProposalTableProps) {
  const [activeFilter, setActiveFilter] = useState<ProposalStatus | "ALL">(
    "ALL",
  );

  const filtered =
    activeFilter === "ALL"
      ? proposals
      : proposals.filter((p) => p.status === activeFilter);

  return (
    <div className="space-y-4">
      {/* Status Filter Tabs */}
      <div className="flex gap-1 rounded-lg border border-slate-200 bg-slate-50 p-1 dark:border-slate-800 dark:bg-slate-900">
        {STATUS_FILTERS.map((filter) => (
          <button
            key={filter.value}
            onClick={() => setActiveFilter(filter.value)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
              activeFilter === filter.value
                ? "bg-white text-slate-900 shadow-sm dark:bg-slate-800 dark:text-slate-100"
                : "text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
            }`}
          >
            {filter.label}
          </button>
        ))}
      </div>

      {/* Proposals Table */}
      {filtered.length === 0 ? (
        <div className="rounded-lg border border-slate-200 bg-white p-6 text-center dark:border-slate-800 dark:bg-slate-950">
          <p className="text-sm text-slate-500 dark:text-slate-400">
            No proposals found for this filter.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Title
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Customer
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 dark:text-slate-400">
                  Status
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 sm:table-cell dark:text-slate-400">
                  Sent Date
                </th>
                <th className="hidden px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-slate-600 lg:table-cell dark:text-slate-400">
                  Days Since Sent
                </th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((proposal) => {
                const badge = STATUS_BADGE[proposal.status];
                const daysSinceSent = proposal.sentAt
                  ? Math.floor(
                      (now - new Date(proposal.sentAt).getTime()) /
                        (1000 * 60 * 60 * 24),
                    )
                  : null;

                return (
                  <tr
                    key={proposal.id}
                    className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                  >
                    <td className="px-4 py-3">
                      <Link
                        href={`/org/${slug}/proposals/${proposal.id}`}
                        className="font-medium text-slate-900 hover:text-teal-600 dark:text-slate-100 dark:hover:text-teal-400"
                      >
                        {proposal.title}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-600 dark:text-slate-400">
                      {proposal.proposalNumber}
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={badge.variant}>{badge.label}</Badge>
                    </td>
                    <td className="hidden px-4 py-3 text-sm text-slate-600 sm:table-cell dark:text-slate-400">
                      {proposal.sentAt
                        ? formatDate(proposal.sentAt)
                        : "\u2014"}
                    </td>
                    <td className="hidden px-4 py-3 text-sm text-slate-600 lg:table-cell dark:text-slate-400">
                      {daysSinceSent !== null ? `${daysSinceSent}` : "\u2014"}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
