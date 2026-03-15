import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { redirect } from "next/navigation";
import { TemplateEditor } from "@/components/templates/TemplateEditor";
import { getProjectTemplate } from "@/lib/api/templates";
import { getTags, getFieldDefinitions } from "@/lib/api";
import { listRequestTemplates } from "@/lib/api/information-requests";
import type { RequestTemplateResponse } from "@/lib/api/information-requests";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { TagResponse, FieldDefinitionResponse } from "@/lib/types";

export default async function TemplateEditorPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const caps = await fetchMyCapabilities();

  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    redirect(`/org/${slug}/settings/project-templates`);
  }

  let template: ProjectTemplateResponse | undefined;
  let tags: TagResponse[] = [];
  let availableCustomerFields: FieldDefinitionResponse[] = [];
  let requestTemplates: RequestTemplateResponse[] = [];
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

  try {
    availableCustomerFields = await getFieldDefinitions("CUSTOMER");
    availableCustomerFields = availableCustomerFields.filter((f) => f.active);
  } catch {
    // Non-fatal: required fields section won't render
  }

  try {
    requestTemplates = await listRequestTemplates(true);
  } catch {
    // Non-fatal: request template dropdown will show no options
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

      <TemplateEditor slug={slug} template={template} availableTags={tags} availableCustomerFields={availableCustomerFields} availableRequestTemplates={requestTemplates.map((rt) => ({ id: rt.id, name: rt.name }))} />
    </div>
  );
}
