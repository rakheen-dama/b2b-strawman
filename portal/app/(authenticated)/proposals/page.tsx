"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { FileText } from "lucide-react";
import { portalGet } from "@/lib/api-client";
import { formatCurrency, formatDate } from "@/lib/format";
import { ProposalStatusBadge } from "@/components/proposal-status-badge";
import { Skeleton } from "@/components/ui/skeleton";
import type { PortalProposal } from "@/lib/types";

function TableSkeleton() {
  return (
    <div className="space-y-3">
      {Array.from({ length: 5 }).map((_, i) => (
        <Skeleton key={i} className="h-12 w-full" />
      ))}
    </div>
  );
}

export default function ProposalsPage() {
  const [proposals, setProposals] = useState<PortalProposal[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function fetchProposals() {
      try {
        const data = await portalGet<PortalProposal[]>(
          "/portal/api/proposals",
        );
        if (!cancelled) {
          setProposals(data);
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : "Failed to load proposals",
          );
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    fetchProposals();

    return () => {
      cancelled = true;
    };
  }, []);

  const actionable = proposals.filter((p) => p.status === "SENT");
  const past = proposals.filter((p) => p.status !== "SENT");

  return (
    <div>
      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        Proposals
      </h1>

      {isLoading && <TableSkeleton />}

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
        </div>
      )}

      {!isLoading && !error && proposals.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <FileText className="mb-4 size-12 text-slate-300" />
          <p className="text-lg font-medium text-slate-600">
            No proposals yet.
          </p>
        </div>
      )}

      {!isLoading && !error && actionable.length > 0 && (
        <section className="mb-8">
          <h2 className="font-display mb-3 text-lg font-semibold text-slate-900">
            Awaiting Your Response
          </h2>
          <ProposalTable proposals={actionable} />
        </section>
      )}

      {!isLoading && !error && past.length > 0 && (
        <section>
          {actionable.length > 0 && (
            <h2 className="font-display mb-3 text-lg font-semibold text-slate-900">
              Past Proposals
            </h2>
          )}
          <ProposalTable proposals={past} />
        </section>
      )}
    </div>
  );
}

function ProposalTable({ proposals }: { proposals: PortalProposal[] }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200">
      <table className="w-full text-sm" aria-label="Proposal list">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50">
            <th className="px-4 py-3 text-left font-medium text-slate-600">
              Proposal #
            </th>
            <th className="px-4 py-3 text-left font-medium text-slate-600">
              Title
            </th>
            <th className="px-4 py-3 text-left font-medium text-slate-600">
              Status
            </th>
            <th className="hidden px-4 py-3 text-left font-medium text-slate-600 sm:table-cell">
              Sent
            </th>
            <th className="px-4 py-3 text-right font-medium text-slate-600">
              Fee
            </th>
            <th className="px-4 py-3 text-right font-medium text-slate-600">
              Actions
            </th>
          </tr>
        </thead>
        <tbody>
          {proposals.map((proposal) => (
            <tr
              key={proposal.id}
              className="border-b border-slate-100 last:border-b-0"
            >
              <td className="px-4 py-3">
                <Link
                  href={`/proposals/${proposal.id}`}
                  className="inline-flex min-h-[44px] items-center font-medium text-teal-600 hover:text-teal-700 hover:underline"
                >
                  {proposal.proposalNumber}
                </Link>
              </td>
              <td className="px-4 py-3 text-slate-700">{proposal.title}</td>
              <td className="px-4 py-3">
                <ProposalStatusBadge status={proposal.status} />
              </td>
              <td className="hidden px-4 py-3 text-slate-700 sm:table-cell">
                {proposal.sentAt ? formatDate(proposal.sentAt) : "-"}
              </td>
              <td className="px-4 py-3 text-right font-medium text-slate-900">
                {proposal.feeAmount != null && proposal.feeCurrency
                  ? formatCurrency(proposal.feeAmount, proposal.feeCurrency)
                  : "-"}
              </td>
              <td className="px-4 py-3 text-right">
                <Link
                  href={`/proposals/${proposal.id}`}
                  className="inline-flex min-h-[44px] items-center text-sm text-slate-500 hover:text-slate-700"
                >
                  View
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
