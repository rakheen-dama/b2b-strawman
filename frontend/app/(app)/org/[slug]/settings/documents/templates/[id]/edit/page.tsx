import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getTemplateDetail } from "@/lib/api";
import { TemplateEditorClient } from "@/components/templates/TemplateEditorClient";
import type { TemplateDetailResponse } from "@/lib/types";
import { settingsHref } from "@/lib/settings-nav";

export default async function TemplateEditorPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  let template: TemplateDetailResponse;
  try {
    template = await getTemplateDetail(id);
  } catch {
    return (
      <div className="space-y-8">
        <Link
          href={settingsHref(slug, "documents", "templates")}
          className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
        >
          <ChevronLeft className="size-4" />
          Templates
        </Link>
        <p className="text-destructive text-sm">
          Failed to load template. It may have been deleted or you don&apos;t have access.
        </p>
      </div>
    );
  }

  const readOnly = !isAdmin || template.source === "PLATFORM";

  // Phase 91 665E: the editor owns this page's h1 (TemplateEditorClient) — documented PageHeader
  // exception (§18.3.5 sub-page table), carried to 670A.
  return <TemplateEditorClient slug={slug} template={template} readOnly={readOnly} />;
}
