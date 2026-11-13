/**
 * Column definitions for `DataTable` (ADR-369, architecture §15.2.4).
 *
 * RSC BOUNDARY (Phase 90 OQ #19) — READ BEFORE CONSUMING:
 * Column defs, cell renderers, `sort.hrefFor`/`sort.onSort`, `rowHref` and `meta.csv` are
 * FUNCTIONS. Next.js 16 throws when a Server Component passes a function to a "use client"
 * component. So `DataTable` is rendered ONLY from a client module: each consumer adds a thin
 * "use client" wrapper (e.g. `MattersDataTable`) that receives the SERIALIZABLE props today's
 * tables take (`slug`, `rows`, `columns` (ids), `customFieldNames`, `sort` ({ key, dir }), `query`,
 * `caption`, …) and builds the column defs (`defineColumns`), `hrefFor` (from `buildSortHref` /
 * `buildClientSortHref`), `rowHref` and `meta.csv` INSIDE that module. Toolbar slots, `empty` and
 * `footer` are pre-rendered ReactNodes from the RSC (allowed). Memoise the column array in the
 * wrapper (`useMemo`).
 *
 * `data` IDENTITY IS NAVIGATION: `DataTable` resets column visibility, row selection and the page
 * index whenever `data` is a new array. Pass the RSC rows through untouched; if the wrapper derives
 * rows (map/filter), `useMemo` the derivation on the RSC array, or every re-render resets the table.
 *
 * Set `enableHiding: false` on the row's name/link column (it is the keyboard path and the e2e row
 * filter) and on any actions column; the "Columns" menu lists only hideable columns that have a
 * `meta.label`.
 *
 * Prefer display columns (`{ id, meta, cell: ({ row }) => … }`) over `createColumnHelper().accessor`:
 * an accessor column is typed `ColumnDef<T, string>`, which does not widen to `ColumnDef<T, unknown>`.
 * A column without its own `header` renders `meta.label` (a sort link, or a sort button for
 * `sort.onSort`, when `meta.sortKey` is set).
 */
import type { ColumnDef, RowData } from "@tanstack/react-table";

/**
 * URL-owned sort state (list pages). `hrefFor` is built by the page's existing `buildSortHref`. `K`
 * is the page's sort-key union (e.g. `MatterSort["key"]`), so `hrefFor` can call `buildSortHref`
 * without a cast; set each sortable column's `meta.sortKey` to a member of `K`.
 */
export interface DataTableUrlSort<K extends string = string> {
  /** The active sort key, or `null` when the list has no active sort. */
  key: K | null;
  dir: "asc" | "desc";
  /** Link target for a header: the page's sort href for that key (all other params kept). */
  hrefFor: (sortKey: K) => string;
}

/**
 * Local-state sort (tables whose rows live in client state, e.g. the profitability cards): the
 * header is a `<button>` calling `onSort(meta.sortKey)`. The kit still never sorts rows itself: the
 * consumer sorts in its own state and passes the sorted array (`useMemo`) as `data`.
 */
export interface DataTableClientSort<K extends string = string> {
  /** The active sort key, or `null` when the table has no active sort. */
  key: K | null;
  dir: "asc" | "desc";
  /** Called with the clicked column's `meta.sortKey`; the consumer decides the new key/dir. */
  onSort: (sortKey: K) => void;
}

/** Sort state of a `DataTable`: URL-owned (`hrefFor`) or local-state (`onSort`). */
export type DataTableSort<K extends string = string> = DataTableUrlSort<K> | DataTableClientSort<K>;

/**
 * Column priority (container queries on the table frame, as today's tables do):
 * - `"high"` (default): always shown;
 * - `"low"`: shown from a 67rem container (`hidden @min-[67rem]:table-cell`, matters "Fee earner");
 * - `"lowest"`: shown from a 7xl container (`hidden @7xl:table-cell`, matters "Work type"/"Opened").
 * The actively sorted column is never hidden by priority.
 */
export type DataTableColumnPriority = "high" | "low" | "lowest";

declare module "@tanstack/react-table" {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars -- TData/TValue are required by the merged interface
  interface ColumnMeta<TData extends RowData, TValue> {
    /** Header text, "Columns" menu label and CSV header. */
    label: string;
    priority?: DataTableColumnPriority;
    /** `"end"` right-aligns the header and cells (money, counts). */
    align?: "start" | "end";
    /**
     * Sort key passed to `sort.hrefFor` / `sort.onSort`; absent → the header is plain text with no
     * `aria-sort`.
     */
    sortKey?: string;
    /** CSV value for this column; absent → the column is never exported. */
    csv?: (row: TData) => string | number | null;
    headerClassName?: string;
    cellClassName?: string;
  }

  // eslint-disable-next-line @typescript-eslint/no-unused-vars -- TData is required by the merged interface
  interface TableMeta<TData extends RowData> {
    /** Set by `DataTable`: the default header reads the current sort from here. */
    dataTableSort?: DataTableSort;
  }
}

export type DataTableColumn<T> = ColumnDef<T, unknown>;

/** Typed identity helper: `defineColumns<MatterListRow>([...])`. */
export function defineColumns<T>(columns: DataTableColumn<T>[]): DataTableColumn<T>[] {
  return columns;
}
