import Link from "next/link";
import { redirect } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { auth } from "@clerk/nextjs/server";
import { getTemplateDetail } from "@/lib/api";
import { TemplateEditorForm } from "@/components/templates/TemplateEditorForm";
import type { TemplateDetailResponse } from "@/lib/types";

export default async function TemplateEditorPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await auth();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    redirect(`/org/${slug}/settings/templates`);
  }

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
          Failed to load template. It may have been deleted or you don&apos;t have
          access.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings/templates`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Templates
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Edit Template
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          {template.name}
        </p>
      </div>

      <TemplateEditorForm slug={slug} template={template} />
    </div>
  );
}
