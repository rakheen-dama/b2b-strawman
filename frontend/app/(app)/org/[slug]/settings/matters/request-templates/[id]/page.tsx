import Link from "next/link";
import { PageHeader } from "@b2mash/ui/page-header";
import { SettingsSection } from "@/components/settings/settings-section";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getRequestTemplate } from "@/lib/api/information-requests";
import { TemplateSourceBadge } from "@/components/information-requests/template-source-badge";
import { EditRequestTemplateForm } from "@/components/information-requests/edit-request-template-form";
import { settingsHref } from "@/lib/settings-nav";

export default async function EditRequestTemplatePage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <Link
          href={settingsHref(slug, "matters", "request-templates")}
          className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
        >
          <ChevronLeft className="size-4" />
          Request Templates
        </Link>
        <p className="text-muted-foreground">
          You do not have permission to edit request templates.
        </p>
      </div>
    );
  }

  const template = await getRequestTemplate(id);

  return (
    <div className="space-y-8">
      <Link
        href={settingsHref(slug, "matters", "request-templates")}
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeft className="size-4" />
        Request Templates
      </Link>

      <PageHeader title={template.source === "PLATFORM" ? template.name : "Edit Template"}>
        <TemplateSourceBadge source={template.source} />
      </PageHeader>

      <SettingsSection>
        <EditRequestTemplateForm slug={slug} template={template} />
      </SettingsSection>
    </div>
  );
}
