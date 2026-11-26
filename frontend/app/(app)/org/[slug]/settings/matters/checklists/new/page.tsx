import Link from "next/link";
import { PageHeader } from "@b2mash/ui/page-header";
import { SettingsSection } from "@/components/settings/settings-section";
import { ChevronLeft } from "lucide-react";
import { CreateChecklistTemplateForm } from "@/components/compliance/CreateChecklistTemplateForm";
import { settingsHref } from "@/lib/settings-nav";

export default async function NewChecklistTemplatePage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  return (
    <div className="space-y-8">
      <Link
        href={settingsHref(slug, "matters", "checklists")}
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeft className="size-4" />
        Checklists
      </Link>

      <PageHeader
        title="New Checklist Template"
        description="Create a checklist template for client onboarding."
      />

      <SettingsSection>
        <CreateChecklistTemplateForm slug={slug} />
      </SettingsSection>
    </div>
  );
}
