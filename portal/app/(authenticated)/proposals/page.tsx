"use client";

import { useCallback, useEffect, useState } from "react";
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

  const fetchProposals = useCallback(async () => {
    setError(null);
    setIsLoading(true);
    try {
      const data = await portalGet<PortalProposal[]>(
        "/portal/api/proposals",
      );
      setProposals(data);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load proposals",
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchProposals();
  }, [fetchProposals]);

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
          <p className="mb-2">{error}</p>
          <button
            type="button"
            onClick={() => fetchProposals()}
            className="inline-flex min-h-11 items-center rounded-md bg-white px-3 py-1.5 text-sm font-medium text-red-700 ring-1 ring-red-200 hover:bg-red-100"
          >
            Try again
          </button>
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
          <ProposalList proposals={actionable} variant="actionable" />
        </section>
      )}

      {!isLoading && !error && past.length > 0 && (
        <section>
          {actionable.length > 0 && (
            <h2 className="font-display mb-3 text-lg font-semibold text-slate-900">
              Past Proposals
            </h2>
          )}
          <ProposalList proposals={past} variant="past" />
        </section>
      )}
    </div>
  );
}

function ProposalList({
  proposals,
  variant,
}: {
  proposals: PortalProposal[];
  variant: "actionable" | "past";
}) {
  return (
    <>
      {/* Mobile: Card layout */}
      <div
        data-testid={`proposals-${variant}-mobile`}
        className="flex flex-col gap-3 md:hidden"
      >
        {proposals.map((proposal) => (
          <div
            key={proposal.id}
            className="flex flex-col gap-3 rounded-lg border border-slate-200/80 bg-white p-4 shadow-sm"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0 flex-1">
                <Link
                  href={`/proposals/${proposal.id}`}
                  className="inline-flex min-h-11 items-center font-medium text-teal-600 hover:text-teal-700 hover:underline"
                >
                  {proposal.proposalNumber}
                </Link>
                <p className="mt-0.5 truncate text-sm text-slate-700">
                  {proposal.title}
                </p>
              </div>
              <ProposalStatusBadge status={proposal.status} />
            </div>
            <div className="flex items-center justify-between border-t border-slate-100 pt-3">
              <div className="flex flex-col gap-0.5 text-xs text-slate-500">
                {proposal.sentAt && (
                  <span>Sent {formatDate(proposal.sentAt)}</span>
                )}
                {proposal.feeAmount != null && proposal.feeCurrency && (
                  <span className="text-base font-semibold text-slate-900">
                    {formatCurrency(proposal.feeAmount, proposal.feeCurrency)}
                  </span>
                )}
              </div>
              <Link
                href={`/proposals/${proposal.id}`}
                className="inline-flex min-h-11 items-center text-sm text-slate-600 hover:text-slate-900"
              >
                View
              </Link>
            </div>
          </div>
        ))}
      </div>

      {/* Desktop: Table layout */}
      <div
        data-testid={`proposals-${variant}-desktop`}
        className="hidden overflow-x-auto rounded-lg border border-slate-200 md:block"
      >
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
              <th className="px-4 py-3 text-left font-medium text-slate-600">
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
                <td className="px-4 py-3 text-slate-700">
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
    </>
  );
}
