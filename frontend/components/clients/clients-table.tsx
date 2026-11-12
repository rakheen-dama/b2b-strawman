"use client";

import { useMemo, type ReactNode } from "react";
import { DataTable, type DataTableToolbarSlots } from "@/components/data-table/data-table";
import { buildClientSortHref, type ClientSort } from "@/lib/clients-list";
import type { ClientListRow } from "@/lib/types";
import { buildClientColumns } from "./client-columns";

interface ClientsDataTableProps {
  slug: string;
  rows: ClientListRow[];
  /** Resolved column ids (`resolveClientColumns`): client column ids and `cf:<slug>`. */
  columns: string[];
  /** Custom-field slug → field name, for `cf:<slug>` headers. */
  customFieldNames: Record<string, string>;
  sort: ClientSort;
  /** Current URL params (strings only), kept by the sort links. */
  query: Record<string, string>;
  /** Visually hidden table caption. */
  caption: string;
  /**
   * Hide lower-priority columns in narrow containers. Off for a saved view's own column choice;
   * the active sort column is never hidden.
   */
  hideLowPriorityColumns?: boolean;
  /** Pre-rendered toolbar slots (views, search, filters) from the server page. */
  toolbar?: DataTableToolbarSlots;
  /** Pre-rendered empty state (filtered / all-complete / first-run), shown instead of the table. */
  empty: ReactNode;
  /** Pre-rendered footer under the rows ("Showing N clients"). */
  footer?: ReactNode;
}

const getClientRowId = (row: ClientListRow) => row.id;

/**
 * Clients list on `DataTable` (Phase 90 642A, ADR-369 client mode). The server page fetches,
 * searches, filters and sorts; this client wrapper only receives serializable props and builds the
 * function props (columns, `sort.hrefFor`, `rowHref`) itself (OQ #19). The URL keeps owning view,
 * lifecycle, tags, showIncomplete, search, `sortBy` and `sortDir`. The default column set fits its
 * container at every width (priority classes + the flexible Client column), so its header sticks
 * under the floating header; a saved view's own columns may need horizontal scroll, so sticky is
 * off there (sticky ⇔ no horizontal scroll, 639C). Sticky applies only from a 42rem frame; the
 * always-shown set (~32rem) scrolls inside its frame below that.
 */
export function ClientsDataTable({
  slug,
  rows,
  columns,
  customFieldNames,
  sort,
  query,
  caption,
  hideLowPriorityColumns = true,
  toolbar,
  empty,
  footer,
}: ClientsDataTableProps) {
  // Memoised: flexRender renders each cell function as a component, so a new column array would
  // remount every cell (and close an open row menu) on each re-render.
  const tableColumns = useMemo(
    () =>
      buildClientColumns({
        slug,
        columns,
        customFieldNames,
        hideLowPriorityColumns,
        sortKey: sort.key,
      }),
    [slug, columns, customFieldNames, hideLowPriorityColumns, sort.key]
  );

  return (
    <DataTable
      columns={tableColumns}
      data={rows}
      getRowId={getClientRowId}
      sort={{
        key: sort.key,
        dir: sort.dir,
        hrefFor: (key) => buildClientSortHref(slug, query, key, sort),
      }}
      toolbar={toolbar}
      rowHref={(row) => `/org/${slug}/clients/${row.id}`}
      rowTestId="client-row"
      pageSize={null}
      exportCsv={{ filename: "clients.csv" }}
      empty={empty}
      footer={footer}
      stickyHeader={hideLowPriorityColumns}
      caption={caption}
      tableTestId="clients-table"
    />
  );
}
