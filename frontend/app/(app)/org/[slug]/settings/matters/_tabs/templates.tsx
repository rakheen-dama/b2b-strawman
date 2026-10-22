import Link from "next/link";
import { Plus } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Button } from "@b2mash/ui/button";
import { TemplateList } from "@/components/templates/TemplateList";
import { getProjectTemplates } from "@/lib/api/templates";
import type { ProjectTemplateResponse } from "@/lib/api/templates";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Matters › Templates tab (Phase 87 S9, ADR-341) — the former project-templates settings page's loader. */
export async function renderTemplatesTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  const isAdmin = caps.isAdmin || caps.isOwner;

  let templates: ProjectTemplateResponse[] = [];

  try {
    templates = await getProjectTemplates();
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Matter Templates
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Create and manage reusable matter blueprints to standardize matter structure.
        </p>
      </div>

      {isAdmin && (
        <div className="flex justify-end">
          <Link href={`/org/${slug}/settings/matters/templates/new`}>
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
