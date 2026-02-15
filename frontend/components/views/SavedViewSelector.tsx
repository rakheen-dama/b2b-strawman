"use client";

import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { Eye, Plus } from "lucide-react";
import type { EntityType, SavedViewResponse } from "@/lib/types";

interface SavedViewSelectorProps {
  entityType: EntityType;
  views: SavedViewResponse[];
  currentViewId: string | null;
  onViewChange: (viewId: string | null) => void;
  canCreate: boolean;
  onCreateClick?: () => void;
}

export function SavedViewSelector({
  views,
  currentViewId,
  onViewChange,
  canCreate,
  onCreateClick,
}: SavedViewSelectorProps) {
  return (
    <div
      className="flex items-center gap-3"
      data-testid="saved-view-selector"
    >
      <Eye className="size-4 text-slate-400" />
      <Tabs
        value={currentViewId ?? "all"}
        onValueChange={(val) => onViewChange(val === "all" ? null : val)}
      >
        <TabsList variant="line">
          <TabsTrigger value="all">All</TabsTrigger>
          {views.map((view) => (
            <TabsTrigger key={view.id} value={view.id}>
              {view.name}
              {view.shared && (
                <span className="ml-1 text-xs text-slate-400" title="Shared view">
                  (shared)
                </span>
              )}
            </TabsTrigger>
          ))}
        </TabsList>
      </Tabs>

      {canCreate && (
        <Button
          variant="outline"
          size="sm"
          className="h-7 gap-1 text-xs"
          onClick={onCreateClick}
        >
          <Plus className="size-3" />
          Save View
        </Button>
      )}
    </div>
  );
}
