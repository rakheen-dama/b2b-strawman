import Link from "next/link";
import { Plus } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api } from "@/lib/api";
import { Button } from "@b2mash/ui/button";
import { PageHeader } from "@b2mash/ui/page-header";
import { ChecklistTemplatesTable } from "./checklists-table";
import type { ChecklistTemplateResponse } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Matters › Checklists tab (Phase 87 S9, ADR-341) — the former checklists settings page's loader. */
export async function renderChecklistsTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
    return (
      <div className="space-y-8">
        <PageHeader title="Checklists" />
        <p className="text-muted-foreground">
          You do not have permission to manage checklist templates. Only admins and owners can
          access this page.
        </p>
      </div>
    );
  }

  let templates: ChecklistTemplateResponse[] = [];

  try {
    templates = await api.get<ChecklistTemplateResponse[]>("/api/checklist-templates");
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      <PageHeader
        title="Checklists"
        description="Manage checklist templates used during client onboarding."
        actions={
          <Link href={`/org/${slug}/settings/matters/checklists/new`}>
            <Button size="sm">
              <Plus className="mr-1.5 size-4" />
              Create Template
            </Button>
          </Link>
        }
      />

      <ChecklistTemplatesTable slug={slug} templates={templates} />
    </div>
  );
}
