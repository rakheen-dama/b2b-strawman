"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useCallback } from "react";
import { SavedViewSelector } from "./SavedViewSelector";
import { CreateViewDialog } from "./CreateViewDialog";
import { Button } from "@b2mash/ui/button";
import { Plus } from "lucide-react";
import type {
  EntityType,
  SavedViewResponse,
  TagResponse,
  FieldDefinitionResponse,
  CreateSavedViewRequest,
} from "@/lib/types";
import {
  isSystemViewId,
  resolveViewSelection,
  systemViewToFilters,
  type SystemView,
} from "@/lib/constants/system-views";

interface ViewSelectorClientProps {
  entityType: EntityType;
  views: SavedViewResponse[];
  canCreate: boolean;
  canCreateShared: boolean;
  slug: string;
  allTags: TagResponse[];
  fieldDefinitions: FieldDefinitionResponse[];
  onSave: (req: CreateSavedViewRequest) => Promise<{ success: boolean; error?: string }>;
  /**
   * Code-defined system views (615C), listed before the saved views. Optional: callers that omit
   * it keep the "All" tab behaviour unchanged.
   */
  systemViews?: readonly SystemView[];
  /** The system view selected when the URL has no `view` param. */
  defaultViewId?: string;
  /**
   * URL params dropped when switching to a saved view. For params the list ignores on the
   * saved-view path (the matters page passes `["tags"]`): left in the URL they would filter
   * nothing, yet count as an active filter the user cannot see or clear.
   */
  clearOnSavedView?: readonly string[];
}

export function ViewSelectorClient({
  entityType,
  views,
  canCreate,
  canCreateShared,
  slug,
  allTags,
  fieldDefinitions,
  onSave,
  systemViews,
  defaultViewId,
  clearOnSavedView,
}: ViewSelectorClientProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const currentViewId = searchParams.get("view");

  const handleViewChange = useCallback(
    (viewId: string | null) => {
      const params = new URLSearchParams(searchParams.toString());
      if (viewId) {
        params.set("view", viewId);
        if (!isSystemViewId(viewId)) {
          for (const key of clearOnSavedView ?? []) params.delete(key);
        }
      } else {
        params.delete("view");
      }
      const qs = params.toString();
      router.push(qs ? `?${qs}` : "?");
    },
    [router, searchParams, clearOnSavedView]
  );

  // F-4: "Save view" while a system view is selected starts from that view's params.
  const selectedSystemView =
    systemViews && systemViews.length > 0
      ? (() => {
          const selection = resolveViewSelection(
            systemViews,
            views,
            currentViewId,
            defaultViewId ?? systemViews[0].id
          );
          return selection.kind === "system" ? selection.view : null;
        })()
      : null;

  return (
    <div className="flex items-center gap-3">
      <SavedViewSelector
        entityType={entityType}
        views={views}
        currentViewId={currentViewId}
        onViewChange={handleViewChange}
        canCreate={false}
        systemViews={systemViews}
        defaultViewId={defaultViewId}
      />
      {canCreate && (
        <CreateViewDialog
          slug={slug}
          entityType={entityType}
          allTags={allTags}
          fieldDefinitions={fieldDefinitions}
          canCreateShared={canCreateShared}
          onSave={onSave}
          initialFilters={selectedSystemView ? systemViewToFilters(selectedSystemView) : undefined}
          initialFiltersLabel={selectedSystemView?.name}
        >
          <Button variant="outline" size="sm" className="h-7 gap-1 text-xs">
            <Plus className="size-3" />
            Save View
          </Button>
        </CreateViewDialog>
      )}
    </div>
  );
}
