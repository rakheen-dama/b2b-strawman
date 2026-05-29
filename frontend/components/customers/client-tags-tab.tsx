"use client";

import { TagInput } from "@/components/tags/TagInput";
import type { TagResponse } from "@/lib/types";

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface ClientTagsTabProps {
  entityId: string;
  tags: TagResponse[];
  allTags: TagResponse[];
  editable: boolean;
  canInlineCreate: boolean;
  slug: string;
}

// ---------------------------------------------------------------------------
// Component
// ---------------------------------------------------------------------------

export function ClientTagsTab({
  entityId,
  tags,
  allTags,
  editable,
  canInlineCreate,
  slug,
}: ClientTagsTabProps) {
  return (
    <div data-testid="client-tags-tab" className="space-y-2">
      <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
        Tags
      </p>
      <TagInput
        entityType="CUSTOMER"
        entityId={entityId}
        tags={tags}
        allTags={allTags}
        editable={editable}
        canInlineCreate={canInlineCreate}
        slug={slug}
      />
    </div>
  );
}
