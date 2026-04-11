import { notFound } from "next/navigation";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { FileText, Plus } from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import { docsLink } from "@/lib/docs";
import { TerminologyHeading } from "@/components/terminology-heading";
import { TerminologyText } from "@/components/terminology-text";
import { ProposalSummaryCards } from "@/components/proposals/proposal-summary-cards";
import { ProposalsAttentionList } from "@/components/proposals/proposals-attention-list";
import { ProposalTable } from "@/components/proposals/proposal-table";
import { CreateProposalDialog } from "@/components/proposals/create-proposal-dialog";
import { Button } from "@/components/ui/button";
import type { ProposalSummaryDto } from "@/lib/types/proposal";
import type { ProposalResponse } from "@/lib/types/proposal";
import type { Customer } from "@/lib/types";

const emptySummary: ProposalSummaryDto = {
  total: 0,
  byStatus: {},
  avgDaysToAcceptance: 0,
  conversionRate: 0,
  pendingOverdue: [],
};

export default async function ProposalsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner && !caps.capabilities.includes("INVOICING")) {
    notFound();
  }

  let summary: ProposalSummaryDto = emptySummary;
  let proposals: ProposalResponse[] = [];

  const [summaryResult, proposalsResult, customersResult] = await Promise.allSettled([
    api.get<ProposalSummaryDto>("/api/proposals/summary"),
    api.get<{ content: ProposalResponse[] }>("/api/proposals?size=200"),
    api.get<Customer[]>("/api/customers?size=200"),
  ]);

  if (summaryResult.status === "fulfilled") {
    summary = summaryResult.value;
  }

  if (proposalsResult.status === "fulfilled") {
    proposals = proposalsResult.value.content;
  }

  const customers: Array<{ id: string; name: string; email: string }> =
    customersResult.status === "fulfilled"
      ? (Array.isArray(customersResult.value)
          ? customersResult.value
          : ((customersResult.value as unknown as { content: Customer[] }).content ?? [])
        )
          .filter((c) => c.lifecycleStatus !== "OFFBOARDED" && c.lifecycleStatus !== "PROSPECT")
          .map((c) => ({ id: c.id, name: c.name, email: c.email }))
      : [];

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            <TerminologyHeading term="Proposals" />
          </h1>
          {proposals.length > 0 && (
            <span className="rounded-full bg-slate-200 px-2.5 py-0.5 text-sm text-slate-700 dark:bg-slate-800 dark:text-slate-300">
              {proposals.length}
            </span>
          )}
        </div>
        <CreateProposalDialog slug={slug} customers={customers}>
          <Button>
            <Plus className="mr-2 size-4" />
            New Proposal
          </Button>
        </CreateProposalDialog>
      </div>

      {/* Summary Cards */}
      <ProposalSummaryCards summary={summary} />

      {/* Needs Attention */}
      <ProposalsAttentionList summary={summary} slug={slug} />

      {/* Proposal Table or Empty State */}
      {proposals.length === 0 ? (
        <EmptyState
          icon={FileText}
          title={<TerminologyText template="No {proposals} yet" />}
          description={
            <TerminologyText template="Create a {proposal} to start tracking client engagements." />
          }
          secondaryLink={{ label: "Read the guide", href: docsLink("/features/proposals") }}
        />
      ) : (
        <ProposalTable proposals={proposals} slug={slug} now={new Date().getTime()} />
      )}
    </div>
  );
}
