"use client";

import { useParams } from "next/navigation";
import { FileText } from "lucide-react";
import Link from "next/link";

import type { ProposalResponse } from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import { ProposalListTable } from "@/components/proposals/proposal-list-table";
import { Button } from "@/components/ui/button";

interface CustomerProposalsTabProps {
  proposals: ProposalResponse[];
  customerId: string;
}

export function CustomerProposalsTab({
  proposals,
  customerId,
}: CustomerProposalsTabProps) {
  const params = useParams<{ slug: string }>();

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-slate-500">
          {proposals.length === 0
            ? "No proposals for this customer yet."
            : `${proposals.length} proposal${proposals.length === 1 ? "" : "s"}`}
        </p>
        <Button variant="outline" size="sm" asChild>
          <Link
            href={`/org/${params.slug}/proposals/new?customerId=${customerId}`}
          >
            New Proposal
          </Link>
        </Button>
      </div>

      {proposals.length === 0 ? (
        <div className="flex min-h-[200px] flex-col items-center justify-center rounded-lg bg-slate-50/50 px-6 py-12 text-center">
          <FileText className="mb-3 size-10 text-slate-400" />
          <h3 className="text-base font-semibold text-slate-700">
            No proposals yet
          </h3>
          <p className="mt-1 text-sm text-slate-500">
            Proposals for this customer will appear here.
          </p>
        </div>
      ) : (
        <ProposalListTable
          proposals={proposals}
          orgSlug={params.slug}
          activeStatus="ALL"
          embeddedMode
        />
      )}
    </div>
  );
}
