import { handleApiError } from "@/lib/api";
import { PageHeader } from "@/components/layout/page-header";
import { ProposalListTable } from "@/components/proposals/proposal-list-table";
import { listProposals, getProposalStats } from "./proposal-actions";
import type {
  ProposalResponse,
  ProposalStats,
  ProposalStatus,
} from "./proposal-actions";

interface ProposalsPageProps {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ status?: string }>;
}

export default async function ProposalsPage({
  params,
  searchParams,
}: ProposalsPageProps) {
  const { slug } = await params;
  const { status } = await searchParams;

  let proposalsData: {
    content: ProposalResponse[];
    page: {
      totalElements: number;
      totalPages: number;
      size: number;
      number: number;
    };
  } = {
    content: [],
    page: { totalElements: 0, totalPages: 0, size: 20, number: 0 },
  };
  let stats: ProposalStats | null = null;

  try {
    [proposalsData, stats] = await Promise.all([
      listProposals({ size: 200 }),
      getProposalStats(),
    ]);
  } catch (error) {
    handleApiError(error);
  }

  const activeStatus = (status as ProposalStatus) || "ALL";

  return (
    <div className="space-y-6">
      <PageHeader title="Proposals" count={proposalsData.content.length} />

      {/* ProposalPipelineStats placeholder — implemented in 236B */}

      <ProposalListTable
        proposals={proposalsData.content}
        orgSlug={slug}
        activeStatus={activeStatus}
      />
    </div>
  );
}
