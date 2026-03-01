import { handleApiError } from "@/lib/api";
import { PageHeader } from "@/components/layout/page-header";
import { ProposalListTable } from "@/components/proposals/proposal-list-table";
import { ProposalPipelineStats } from "@/components/proposals/proposal-pipeline-stats";
import { listProposals, getProposalStats } from "./proposal-actions";
import type {
  ProposalResponse,
  ProposalStats,
  ProposalStatus,
} from "./proposal-actions";

const VALID_STATUSES: ProposalStatus[] = [
  "DRAFT",
  "SENT",
  "ACCEPTED",
  "DECLINED",
  "EXPIRED",
];

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

  const activeStatus: ProposalStatus | "ALL" = VALID_STATUSES.includes(
    status as ProposalStatus,
  )
    ? (status as ProposalStatus)
    : "ALL";

  try {
    [proposalsData, stats] = await Promise.all([
      // Cap at 200 for initial load; server-side filtering added in 236B
      listProposals({
        size: 200,
        ...(activeStatus !== "ALL" && { status: activeStatus }),
      }),
      getProposalStats(),
    ]);
  } catch (error) {
    return handleApiError(error);
  }

  return (
    <div className="space-y-6">
      <PageHeader title="Proposals" count={proposalsData.content.length} />

      {stats && <ProposalPipelineStats stats={stats} />}

      <ProposalListTable
        proposals={proposalsData.content}
        orgSlug={slug}
        activeStatus={activeStatus}
      />
    </div>
  );
}
