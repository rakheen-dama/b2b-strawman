import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { getTags } from "@/lib/api";
import { HelpTip } from "@/components/help-tip";
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
      <div>
        <h1 className="font-display flex items-center gap-2 text-3xl text-slate-950 dark:text-slate-50">
          Tags
          <HelpTip code="tags.overview" />
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Manage tags for projects, tasks, and customers.
        </p>
      </div>

      <TagsContent slug={slug} tags={tags} canManage={isAdmin} />
    </div>
  );
}
