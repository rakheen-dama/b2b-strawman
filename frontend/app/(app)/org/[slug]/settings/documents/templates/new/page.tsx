import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { PageHeader } from "@b2mash/ui/page-header";
import { SettingsSection } from "@/components/settings/settings-section";
import { CreateTemplateForm } from "@/components/templates/CreateTemplateForm";
import { settingsHref } from "@/lib/settings-nav";

export default async function NewTemplatePage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;

  return (
    <div className="space-y-8">
      <Link
        href={settingsHref(slug, "documents", "templates")}
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeft className="size-4" />
        Templates
      </Link>

      <PageHeader title="New Template" description="Create a custom document template." />

      <SettingsSection>
        <CreateTemplateForm slug={slug} />
      </SettingsSection>
    </div>
  );
}
