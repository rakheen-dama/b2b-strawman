import Link from "next/link";
import { Plus } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Button } from "@b2mash/ui/button";
import { PageHeader } from "@b2mash/ui/page-header";
import { RequestTemplatesTable } from "./request-templates-table";
import { listRequestTemplates, type RequestTemplateResponse } from "@/lib/api/information-requests";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Matters › Request templates tab (Phase 87 S9, ADR-341) — the former request-templates settings page's loader. */
export async function renderRequestTemplatesTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

  if (!isAdmin) {
    return (
      <div className="space-y-8">
        <PageHeader title="Request Templates" />
        <p className="text-muted-foreground">
          You do not have permission to manage request templates. Only admins and owners can access
          this page.
        </p>
      </div>
    );
  }

  let templates: RequestTemplateResponse[] = [];

  try {
    templates = await listRequestTemplates();
  } catch {
    // Non-fatal: show empty state
  }

  return (
    <div className="space-y-8">
      <PageHeader
        title="Request Templates"
        description="Create and manage reusable information request templates."
        actions={
          <Link href={`/org/${slug}/settings/matters/request-templates/new`}>
            <Button size="sm">
              <Plus className="mr-1.5 size-4" />
              New Template
            </Button>
          </Link>
        }
      />

      <RequestTemplatesTable slug={slug} templates={templates} />
    </div>
  );
}
