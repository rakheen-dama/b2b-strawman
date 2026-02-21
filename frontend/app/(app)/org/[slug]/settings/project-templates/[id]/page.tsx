import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { getAuthContext } from "@/lib/auth";
import { redirect } from "next/navigation";
import { TemplateEditor } from "@/components/templates/TemplateEditor";
import { getProjectTemplate } from "@/lib/api/templates";
import { getTags } from "@/lib/api";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { TagResponse } from "@/lib/types";

export default async function TemplateEditorPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    redirect(`/org/${slug}/settings/project-templates`);
  }

  let template: ProjectTemplateResponse | undefined;
  let tags: TagResponse[] = [];
  let notFound = false;

  if (id !== "new") {
    try {
      template = await getProjectTemplate(id);
    } catch (error: unknown) {
      const err = error as { status?: number };
      if (err?.status === 404) {
        notFound = true;
      } else {
        throw error;
      }
    }
  }

  try {
    tags = await getTags();
  } catch {
    // Non-fatal: tag selector will show "no tags available"
  }

  if (notFound) {
    return (
      <div className="space-y-8">
        <Link
          href={`/org/${slug}/settings/project-templates`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Project Templates
        </Link>
        <p className="text-slate-600 dark:text-slate-400">
          Template not found. It may have been deleted.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings/project-templates`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Project Templates
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          {template ? template.name : "New Project Template"}
        </h1>
      </div>

      <TemplateEditor slug={slug} template={template} availableTags={tags} />
    </div>
  );
}
