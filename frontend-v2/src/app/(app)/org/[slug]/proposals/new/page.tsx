import { handleApiError } from "@/lib/api";
import { api } from "@/lib/api";
import { getCustomers } from "@/app/(app)/org/[slug]/customers/actions";
import { getProjectTemplates } from "@/lib/api/templates";
import type { OrgMember } from "@/lib/types";
import { PageHeader } from "@/components/layout/page-header";
import { ProposalForm } from "@/components/proposals/proposal-form";

interface NewProposalPageProps {
  params: Promise<{ slug: string }>;
}

export default async function NewProposalPage({
  params,
}: NewProposalPageProps) {
  const { slug } = await params;

  let customers, orgMembers, projectTemplates;
  try {
    [customers, orgMembers, projectTemplates] = await Promise.all([
      getCustomers(),
      api.get<OrgMember[]>("/api/members"),
      getProjectTemplates(),
    ]);
  } catch (error) {
    return handleApiError(error);
  }

  return (
    <div className="space-y-6">
      <PageHeader title="New Proposal" backHref={`/org/${slug}/proposals`} />
      <ProposalForm
        mode="create"
        orgSlug={slug}
        customers={customers}
        orgMembers={orgMembers}
        projectTemplates={projectTemplates}
      />
    </div>
  );
}
