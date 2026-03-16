import { notFound } from "next/navigation";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { FileText } from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import { ProposalSummaryCards } from "@/components/proposals/proposal-summary-cards";
import { ProposalsAttentionList } from "@/components/proposals/proposals-attention-list";
import { ProposalTable } from "@/components/proposals/proposal-table";
import type { ProposalSummaryDto } from "@/lib/types/proposal";
import type { ProposalResponse } from "@/lib/types/proposal";

const emptySummary: ProposalSummaryDto = {
  total: 0,
  byStatus: {},
  avgDaysToAcceptance: 0,
  conversionRate: 0,
  pendingOverdue: [],
};

export default async function ProposalsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();

  if (
    !caps.isAdmin &&
    !caps.isOwner &&
    !caps.capabilities.includes("INVOICING")
  ) {
    notFound();
  }

  let summary: ProposalSummaryDto = emptySummary;
  let proposals: ProposalResponse[] = [];

  try {
    const [summaryResult, proposalsResult] = await Promise.allSettled([
      api.get<ProposalSummaryDto>("/api/proposals/summary"),
      api.get<{ content: ProposalResponse[] }>("/api/proposals?size=200"),
    ]);

    if (summaryResult.status === "fulfilled") {
      summary = summaryResult.value;
    }

    if (proposalsResult.status === "fulfilled") {
      proposals = proposalsResult.value.content;
    }
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Proposals
          </h1>
          {proposals.length > 0 && (
            <span className="rounded-full bg-slate-200 px-2.5 py-0.5 text-sm text-slate-700 dark:bg-slate-800 dark:text-slate-300">
              {proposals.length}
            </span>
          )}
        </div>
      </div>

      {/* Summary Cards */}
      <ProposalSummaryCards summary={summary} />

      {/* Needs Attention */}
      <ProposalsAttentionList summary={summary} slug={slug} />

      {/* Proposal Table or Empty State */}
      {proposals.length === 0 ? (
        <EmptyState
          icon={FileText}
          title="No proposals yet"
          description="Create a proposal to start tracking client engagements."
        />
      ) : (
        <ProposalTable proposals={proposals} slug={slug} now={new Date().getTime()} />
      )}
    </div>
  );
}
