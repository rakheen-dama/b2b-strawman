import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { getAuthContext } from "@/lib/auth";
import { getTemplateDetail } from "@/lib/api";
import { TemplateEditorClient } from "@/components/templates/TemplateEditorClient";
import type { TemplateDetailResponse } from "@/lib/types";

export default async function TemplateEditorPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await getAuthContext();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let template: TemplateDetailResponse;
  try {
    template = await getTemplateDetail(id);
  } catch {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings/templates`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Templates
        </Link>
        <p className="text-sm text-destructive">
          Failed to load template. It may have been deleted or you don&apos;t
          have access.
        </p>
      </div>
    );
  }

  const readOnly = !isAdmin || template.source === "PLATFORM";

  return (
    <TemplateEditorClient
      slug={slug}
      template={template}
      readOnly={readOnly}
    />
  );
}
