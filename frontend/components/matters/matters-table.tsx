"use client";

import { useMemo, type ReactNode } from "react";
import { DataTable, type DataTableToolbarSlots } from "@/components/data-table/data-table";
import { buildSortHref, type MatterSort } from "@/lib/matters-list";
import type { MatterListRow } from "@/lib/types";
import { buildMatterColumns } from "./matter-columns";

interface MattersDataTableProps {
  slug: string;
  rows: MatterListRow[];
  /** Resolved column ids (`resolveMatterColumns`): matter column ids and `cf:<slug>`. */
  columns: string[];
  /** Custom-field slug → field name, for `cf:<slug>` headers. */
  customFieldNames: Record<string, string>;
  sort: MatterSort;
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
  /** Pre-rendered empty state (filtered vs first-run), shown instead of the table. */
  empty: ReactNode;
  /** Pre-rendered footer under the rows ("Showing N matters"). */
  footer?: ReactNode;
}

const getMatterRowId = (row: MatterListRow) => row.id;

/**
 * Matters list on `DataTable` (Phase 90 641A, ADR-369 client mode). The server page fetches,
 * searches, filters and sorts; this client wrapper only receives serializable props and builds the
 * function props (columns, `sort.hrefFor`, `rowHref`) itself (OQ #19). The URL keeps owning view,
 * status, tags, search, `sort` and `dir`. `stickyHeader` is off: the matters table needs horizontal
 * scroll at 1024 and 1440 (sticky ⇔ no horizontal scroll, 639C).
 */
export function MattersDataTable({
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
}: MattersDataTableProps) {
  // Memoised: flexRender renders each cell function as a component, so a new column array would
  // remount every cell (and close an open row menu) on each re-render.
  const tableColumns = useMemo(
    () => buildMatterColumns({ slug, columns, customFieldNames, hideLowPriorityColumns }),
    [slug, columns, customFieldNames, hideLowPriorityColumns]
  );

  return (
    <DataTable
      columns={tableColumns}
      data={rows}
      getRowId={getMatterRowId}
      sort={{
        key: sort.key,
        dir: sort.dir,
        hrefFor: (key) => buildSortHref(slug, query, key, sort),
      }}
      toolbar={toolbar}
      rowHref={(row) => `/org/${slug}/matters/${row.id}`}
      rowTestId="matter-row"
      pageSize={null}
      exportCsv={{ filename: "matters.csv" }}
      empty={empty}
      footer={footer}
      stickyHeader={false}
      caption={caption}
      tableTestId="matters-table"
    />
  );
}
