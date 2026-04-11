import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getRequestTemplate } from "@/lib/api/information-requests";
import { TemplateSourceBadge } from "@/components/information-requests/template-source-badge";
import { EditRequestTemplateForm } from "@/components/information-requests/edit-request-template-form";

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
          href={`/org/${slug}/settings/request-templates`}
          className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ChevronLeft className="size-4" />
          Request Templates
        </Link>
        <p className="text-slate-600 dark:text-slate-400">
          You do not have permission to edit request templates.
        </p>
      </div>
    );
  }

  const template = await getRequestTemplate(id);

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings/request-templates`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Request Templates
      </Link>

      <div className="flex items-center gap-3">
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          {template.source === "PLATFORM" ? template.name : "Edit Template"}
        </h1>
        <TemplateSourceBadge source={template.source} />
      </div>

      <EditRequestTemplateForm slug={slug} template={template} />
    </div>
  );
}
