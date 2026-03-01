import { getProposalDetail } from "../proposal-actions";
import { handleApiError } from "@/lib/api";
import { ProposalDetailClient } from "@/components/proposals/proposal-detail-client";

interface ProposalDetailPageProps {
  params: Promise<{ slug: string; id: string }>;
}

export default async function ProposalDetailPage({
  params,
}: ProposalDetailPageProps) {
  const { slug, id } = await params;

  let proposal;
  try {
    proposal = await getProposalDetail(id);
  } catch (error) {
    handleApiError(error);
  }

  return <ProposalDetailClient proposal={proposal} orgSlug={slug} />;
}
