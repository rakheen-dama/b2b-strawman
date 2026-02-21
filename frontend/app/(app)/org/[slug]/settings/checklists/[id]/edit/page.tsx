import Link from "next/link";
import { redirect } from "next/navigation";
import { ChevronLeft } from "lucide-react";
import { getAuthContext } from "@/lib/auth";
import { getChecklistTemplateDetail } from "../../queries";
import { EditChecklistTemplateForm } from "@/components/compliance/EditChecklistTemplateForm";
import type { ChecklistTemplateResponse } from "@/lib/types";

export default async function EditChecklistTemplatePage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await getAuthContext();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  if (!isAdmin) {
    redirect(`/org/${slug}/settings/checklists/${id}`);
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
          href={`/org/${slug}/settings/checklists`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Checklists
        </Link>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Template Not Found
        </h1>
        <p className="text-slate-600 dark:text-slate-400">
          The checklist template you are looking for does not exist or has been removed.
        </p>
      </div>
    );
  }

  // Only ORG_CUSTOM templates can be edited
  if (template.source !== "ORG_CUSTOM") {
    redirect(`/org/${slug}/settings/checklists/${id}`);
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings/checklists/${id}`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Back to Template
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Edit Checklist Template
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Update the checklist template details and items.
        </p>
      </div>

      <EditChecklistTemplateForm slug={slug} template={template} />
    </div>
  );
}
