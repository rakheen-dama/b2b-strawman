import Link from "next/link";
import { PageHeader } from "@b2mash/ui/page-header";
import { SettingsSection } from "@/components/settings/settings-section";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { CreateRequestTemplateForm } from "@/components/information-requests/create-request-template-form";
import { settingsHref } from "@/lib/settings-nav";

export default async function NewRequestTemplatePage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
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
        <PageHeader title="New Request Template" />
        <p className="text-muted-foreground">
          You do not have permission to create request templates. Only admins and owners can access
          this page.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <Link
        href={settingsHref(slug, "matters", "request-templates")}
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeft className="size-4" />
        Request Templates
      </Link>

      <PageHeader
        title="New Request Template"
        description="Create a template for information requests."
      />

      <SettingsSection>
        <CreateRequestTemplateForm slug={slug} />
      </SettingsSection>
    </div>
  );
}
