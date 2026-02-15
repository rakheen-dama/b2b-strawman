"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useCallback } from "react";
import { SavedViewSelector } from "./SavedViewSelector";
import { CreateViewDialog } from "./CreateViewDialog";
import { Button } from "@/components/ui/button";
import { Plus } from "lucide-react";
import type {
  EntityType,
  SavedViewResponse,
  TagResponse,
  FieldDefinitionResponse,
  CreateSavedViewRequest,
} from "@/lib/types";

interface ViewSelectorClientProps {
  entityType: EntityType;
  views: SavedViewResponse[];
  canCreate: boolean;
  canCreateShared: boolean;
  slug: string;
  allTags: TagResponse[];
  fieldDefinitions: FieldDefinitionResponse[];
  onSave: (req: CreateSavedViewRequest) => Promise<{ success: boolean; error?: string }>;
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
}: ViewSelectorClientProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const currentViewId = searchParams.get("view");

  const handleViewChange = useCallback(
    (viewId: string | null) => {
      const params = new URLSearchParams(searchParams.toString());
      if (viewId) {
        params.set("view", viewId);
      } else {
        params.delete("view");
      }
      const qs = params.toString();
      router.push(qs ? `?${qs}` : "?");
    },
    [router, searchParams],
  );

  return (
    <div className="flex items-center gap-3">
      <SavedViewSelector
        entityType={entityType}
        views={views}
        currentViewId={currentViewId}
        onViewChange={handleViewChange}
        canCreate={false}
      />
      {canCreate && (
        <CreateViewDialog
          slug={slug}
          entityType={entityType}
          allTags={allTags}
          fieldDefinitions={fieldDefinitions}
          canCreateShared={canCreateShared}
          onSave={onSave}
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
