import Link from "next/link";
import { ChevronLeft, Pencil } from "lucide-react";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { PageHeader } from "@b2mash/ui/page-header";
import { SettingsSection } from "@/components/settings/settings-section";
import { getChecklistTemplateDetail } from "../queries";
import { ChecklistItemsTable } from "./checklist-items-table";
import type { ChecklistTemplateResponse } from "@/lib/types";
import { settingsHref } from "@/lib/settings-nav";

export default async function ChecklistTemplateDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const caps = await fetchMyCapabilities();
  const isAdmin = caps.isAdmin || caps.isOwner;

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
          href={settingsHref(slug, "matters", "checklists")}
          className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
        >
          <ChevronLeft className="size-4" />
          Checklists
        </Link>
        <PageHeader title="Template Not Found" />
        <p className="text-muted-foreground">
          The checklist template you are looking for does not exist or has been removed.
        </p>
      </div>
    );
  }

  const sortedItems = [...template.items].sort((a, b) => a.sortOrder - b.sortOrder);

  return (
    <div className="space-y-8">
      <Link
        href={settingsHref(slug, "matters", "checklists")}
        className="text-muted-foreground hover:text-foreground inline-flex items-center gap-1 text-sm"
      >
        <ChevronLeft className="size-4" />
        Checklists
      </Link>

      <PageHeader
        title={template.name}
        description={template.description || undefined}
        actions={
          isAdmin &&
          template.source === "ORG_CUSTOM" && (
            <Link href={`/org/${slug}/settings/matters/checklists/${id}/edit`}>
              <Button size="sm">
                <Pencil className="mr-1.5 size-4" />
                Edit
              </Button>
            </Link>
          )
        }
      >
        {/* Metadata badges */}
        <Badge variant={template.source === "ORG_CUSTOM" ? "success" : "neutral"}>
          {template.source === "PLATFORM" ? "Platform" : "Custom"}
        </Badge>
        <Badge variant="neutral">{template.clientType}</Badge>
        <Badge variant={template.active ? "success" : "destructive"}>
          {template.active ? "Active" : "Inactive"}
        </Badge>
        {template.autoInstantiate && <Badge variant="neutral">Auto-instantiate</Badge>}
        {template.packId && <Badge variant="neutral">Pack: {template.packId}</Badge>}
      </PageHeader>

      {template.source === "PLATFORM" && (
        <p className="text-muted-foreground text-sm">
          This is a platform template and cannot be edited. Clone it to create a customizable copy.
        </p>
      )}

      <SettingsSection title={`Checklist Items (${sortedItems.length})`}>
        <ChecklistItemsTable items={sortedItems} />
      </SettingsSection>
    </div>
  );
}
