import Link from "next/link";
import { PageHeader } from "@b2mash/ui/page-header";
import { SettingsSection } from "@/components/settings/settings-section";
import { redirect } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getChecklistTemplateDetail } from "../../queries";
import { EditChecklistTemplateForm } from "@/components/compliance/EditChecklistTemplateForm";
import type { ChecklistTemplateResponse } from "@/lib/types";
import { settingsHref } from "@/lib/settings-nav";

export default async function EditChecklistTemplatePage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    redirect(`/org/${slug}/settings/matters/checklists/${id}`);
  }

  let template: ChecklistTemplateResponse | null = null;
  let notFound = false;

  try {
    template = await getChecklistTemplateDetail(id);
  } catch (error) {
    const err = error as { status?: number };
    if (err?.status === 404) {
      notFound = true;
    }
  }

  if (notFound || !template) {
    return (
      <div className="space-y-8">
        <Link
          href={settingsHref(slug, "matters", "checklists")}
          className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
        >
          <ChevronLeft className="size-4" />
          Checklists
        </Link>
        <PageHeader title="Template Not Found" />
        <p className="text-muted-foreground">
          The checklist template you are looking for does not exist or has been removed.
        </p>
      </div>
    );
  }

  // Only ORG_CUSTOM templates can be edited
  if (template.source !== "ORG_CUSTOM") {
    redirect(`/org/${slug}/settings/matters/checklists/${id}`);
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings/matters/checklists/${id}`}
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeft className="size-4" />
        Back to Template
      </Link>

      <PageHeader
        title="Edit Checklist Template"
        description="Update the checklist template details and items."
      />

      <SettingsSection>
        <EditChecklistTemplateForm slug={slug} template={template} />
      </SettingsSection>
    </div>
  );
}
