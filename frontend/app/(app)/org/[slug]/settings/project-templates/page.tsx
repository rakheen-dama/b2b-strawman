import Link from "next/link";
import { ChevronLeft, Plus } from "lucide-react";
import { getAuthContext } from "@/lib/auth";
import { Button } from "@/components/ui/button";
import { TemplateList } from "@/components/templates/TemplateList";
import { getProjectTemplates } from "@/lib/api/templates";
import type { ProjectTemplateResponse } from "@/lib/api/templates";

export default async function ProjectTemplatesSettingsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let templates: ProjectTemplateResponse[] = [];

  try {
    templates = await getProjectTemplates();
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      <Link
        href={`/org/${slug}/settings`}
        className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
      >
        <ChevronLeft className="size-4" />
        Settings
      </Link>

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Project Templates
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Create and manage reusable project blueprints to standardize project structure.
        </p>
      </div>

      {isAdmin && (
        <div className="flex justify-end">
          <Link href={`/org/${slug}/settings/project-templates/new`}>
            <Button size="sm">
              <Plus className="mr-1.5 size-4" />
              Create Template
            </Button>
          </Link>
        </div>
      )}

      <TemplateList slug={slug} templates={templates} canManage={isAdmin} />
    </div>
  );
}
