// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): views/apps/users/list/user-table-toolbar.tsx.
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
import type { ReactNode } from "react";

/** Consumer toolbar slots: pre-rendered nodes (an RSC may render them). */
export interface DataTableToolbarSlots {
  /** Search input (`MatterSearch`, `ClientSearch`). */
  search?: ReactNode;
  /** Filter chips and sheets (`ProjectStatusFilter`, `MatterFiltersSheet`). */
  filters?: ReactNode;
  /** Saved views (`ViewSelectorClient`, unchanged); gets its own sideways-scrolling row. */
  views?: ReactNode;
  /** Page actions, right-aligned before the table's own controls. */
  actions?: ReactNode;
}

interface DataTableToolbarProps extends DataTableToolbarSlots {
  /** The table's own controls ("Columns", "Export CSV"); internal to `DataTable`. */
  controls?: ReactNode;
}

/**
 * Toolbar layout in today's list-page order: the views row (scrolls sideways on narrow screens;
 * `pb-1` keeps the active underline in view), then search, filters and the right-aligned actions.
 * Renders nothing when every slot is empty.
 */
export function DataTableToolbar({
  views,
  search,
  filters,
  actions,
  controls,
}: DataTableToolbarProps) {
  const hasRow = Boolean(search || filters || actions || controls);
  if (!views && !hasRow) return null;

  return (
    <div className="space-y-3">
      {views && <div className="overflow-x-auto pb-1">{views}</div>}
      {hasRow && (
        <div className="flex flex-wrap items-center gap-3">
          {search}
          {filters}
          {(actions || controls) && (
            <div className="ml-auto flex flex-wrap items-center gap-2">
              {actions}
              {controls}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
