import { redirect } from "next/navigation";
import { handleApiError } from "@/lib/api";
import { api } from "@/lib/api";
import { getCustomers } from "@/app/(app)/org/[slug]/customers/actions";
import { getProjectTemplates } from "@/lib/api/templates";
import { getProposalDetail } from "@/app/(app)/org/[slug]/proposals/proposal-actions";
import type { OrgMember } from "@/lib/types";
import { PageHeader } from "@/components/layout/page-header";
import { ProposalForm } from "@/components/proposals/proposal-form";

interface EditProposalPageProps {
  params: Promise<{ slug: string; id: string }>;
}

export default async function EditProposalPage({
  params,
}: EditProposalPageProps) {
  const { slug, id } = await params;

  let proposal, customers, orgMembers, projectTemplates;
  try {
    [proposal, customers, orgMembers, projectTemplates] = await Promise.all([
      getProposalDetail(id),
      getCustomers(),
      api.get<OrgMember[]>("/api/members"),
      getProjectTemplates(),
    ]);
  } catch (error) {
    return handleApiError(error);
  }

  // Only DRAFT proposals can be edited
  if (proposal.status !== "DRAFT") {
    redirect(`/org/${slug}/proposals/${id}`);
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Edit Proposal"
        backHref={`/org/${slug}/proposals/${id}`}
      />
      <ProposalForm
        mode="edit"
        orgSlug={slug}
        customers={customers}
        orgMembers={orgMembers}
        projectTemplates={projectTemplates}
        initialData={proposal}
      />
    </div>
  );
}
