"use client";

import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@b2mash/ui/button";
import { Eye, Plus } from "lucide-react";
import { HelpTip } from "@/components/help-tip";
import type { EntityType, SavedViewResponse } from "@/lib/types";
import { selectedViewId, type SystemView } from "@/lib/constants/system-views";

interface SavedViewSelectorProps {
  entityType: EntityType;
  views: SavedViewResponse[];
  currentViewId: string | null;
  onViewChange: (viewId: string | null) => void;
  canCreate: boolean;
  onCreateClick?: () => void;
  /**
   * Code-defined system views (615C), rendered first in fixed order, then a separator, then the
   * saved views. With system views there is no "All" tab: the default system view is selected
   * when the URL has no (known) view. Without them the selector behaves exactly as before.
   * System entries are never editable or deletable — this selector renders no edit/delete menu.
   */
  systemViews?: readonly SystemView[];
  /** Selected when `currentViewId` is absent or unknown. Defaults to the first system view. */
  defaultViewId?: string;
}

export function SavedViewSelector({
  views,
  currentViewId,
  onViewChange,
  canCreate,
  onCreateClick,
  systemViews,
  defaultViewId,
}: SavedViewSelectorProps) {
  const hasSystemViews = !!systemViews && systemViews.length > 0;
  const tabsValue = hasSystemViews
    ? selectedViewId(systemViews, views, currentViewId, defaultViewId ?? systemViews[0].id)
    : (currentViewId ?? "all");

  return (
    <div className="flex items-center gap-3" data-testid="saved-view-selector">
      <div className="flex items-center gap-1">
        <Eye className="size-4 text-slate-400" />
        <HelpTip code="views.saved" />
      </div>
      <Tabs
        value={tabsValue}
        onValueChange={(val) => onViewChange(!hasSystemViews && val === "all" ? null : val)}
      >
        <TabsList variant="line">
          {hasSystemViews ? (
            <>
              {systemViews.map((view) => (
                <TabsTrigger key={view.id} value={view.id} data-system-view="true">
                  {view.name}
                </TabsTrigger>
              ))}
              {views.length > 0 && (
                <span
                  aria-hidden="true"
                  data-testid="view-selector-separator"
                  className="mx-1 h-4 w-px self-center bg-slate-200 dark:bg-slate-700"
                />
              )}
            </>
          ) : (
            <TabsTrigger value="all">All</TabsTrigger>
          )}
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
        <Button variant="outline" size="sm" className="h-7 gap-1 text-xs" onClick={onCreateClick}>
          <Plus className="size-3" />
          Save View
        </Button>
      )}
    </div>
  );
}
