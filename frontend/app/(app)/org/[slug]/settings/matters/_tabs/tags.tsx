import { Plus } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { PageHeader } from "@b2mash/ui/page-header";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getTags } from "@/lib/api";
import { HelpTip } from "@/components/help-tip";
import { TagDialog } from "@/components/tags/TagDialog";
import { TagsContent } from "./tags-content";
import type { TagResponse } from "@/lib/types";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

/** Matters › Tags tab (Phase 87 S9, ADR-341) — the former tags settings page's loader. */
export async function renderTagsTab({ slug }: SettingsTabProps) {
  const caps = await fetchMyCapabilities();

  const isAdmin = caps.isAdmin || caps.isOwner;

  let tags: TagResponse[] = [];
  try {
    tags = await getTags();
  } catch {
    // Non-fatal: show empty state on API failure
  }

  return (
    <div className="space-y-8">
      <PageHeader
        title={
          <span className="inline-flex items-center gap-2">
            Tags
            <HelpTip code="tags.overview" />
          </span>
        }
        description="Manage tags for matters, tasks, and clients."
        actions={
          isAdmin && (
            <TagDialog slug={slug}>
              <Button size="sm">
                <Plus className="mr-1.5 size-4" />
                Add Tag
              </Button>
            </TagDialog>
          )
        }
      />

      <TagsContent slug={slug} tags={tags} canManage={isAdmin} />
    </div>
  );
}
