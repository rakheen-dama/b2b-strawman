// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): views/apps/users/list/user-table.tsx and user-table-columns.tsx (select column).
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
"use client";
// RSC boundary (Phase 90 OQ #19): DataTable takes function props (columns, sort.hrefFor/onSort,
// rowHref, getRowCanExpand, renderExpandedRow, meta.csv), so render it ONLY from a "use client"
// module — a thin per-page wrapper that receives serializable props and builds the functions
// itself. See define-columns.tsx.

import { Fragment, useMemo, useState, type MouseEvent, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import {
  flexRender,
  getCoreRowModel,
  getPaginationRowModel,
  useReactTable,
  type ColumnMeta,
  type HeaderContext,
  type PaginationState,
  type RowSelectionState,
  type VisibilityState,
} from "@tanstack/react-table";
import { Skeleton } from "@b2mash/ui/skeleton";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { DataTableColumnHeader } from "./data-table-column-header";
import { DataTableCsvButton } from "./data-table-csv";
import { DataTablePagination } from "./data-table-pagination";
import { DataTableToolbar, type DataTableToolbarSlots } from "./data-table-toolbar";
import { DataTableViewOptions } from "./data-table-view-options";
import type { DataTableColumn, DataTableSort } from "./define-columns";

export type { DataTableSort } from "./define-columns";
export type { DataTableToolbarSlots } from "./data-table-toolbar";

export interface DataTableProps<T, K extends string = string> {
  /** Built with `defineColumns<T>()` inside the consumer's client module. */
  columns: DataTableColumn<T>[];
  /**
   * Rows already searched, filtered and SORTED by the RSC; rendered in this order.
   *
   * A new `data` array identity means "navigation": it resets column visibility, row selection and
   * the page index. Pass the RSC rows through untouched; `useMemo` any derivation (map/filter) on
   * the RSC array, or every re-render of the wrapper resets the table.
   */
  data: T[];
  getRowId: (row: T) => string;
  /**
   * Sort state; `aria-sort` on the active `th`. URL-owned (`hrefFor`): headers link to
   * `hrefFor(meta.sortKey)`. Local-state (`onSort`): headers are buttons calling
   * `onSort(meta.sortKey)`. Either way `data` arrives already sorted; the kit never sorts rows. `K` is
   * inferred from `sort.key`, so `hrefFor`/`onSort` receive the consumer's own sort-key type.
   */
  sort?: DataTableSort<K>;
  toolbar?: DataTableToolbarSlots;
  /** "Columns" menu (default on). Local state; resets when `data` changes (navigation). */
  columnVisibility?: boolean;
  /** Row click opens this href unless the click lands on a link, button, input or menu. */
  rowHref?: (row: T) => string;
  /** Forwarded to every body `<tr>` (`matter-row`, `client-row`); never on skeleton rows. */
  rowTestId?: string;
  /**
   * `null` (default) renders every row; a positive integer paginates locally with a page-size
   * select. Any other value (0, negative, `NaN`, fractional) is treated as `null`.
   */
  pageSize?: number | null;
  /**
   * Checkbox column whose selection only scopes the CSV export (no bulk actions). Turning it off
   * clears the selection, so the export falls back to the rendered rows.
   */
  selectable?: boolean;
  /** "Export CSV" of the visible columns × the selected (else rendered) rows, client-side. */
  exportCsv?: { filename: string };
  /** Rendered INSTEAD of the table (no `<table>`, no footer) when not loading and `data` is empty. */
  empty: ReactNode;
  /** Renders 8 skeleton rows (and never the `empty` slot). */
  loading?: boolean;
  /** Under the table, only when rows are rendered (e.g. "Showing N matters"). */
  footer?: ReactNode;
  /**
   * Header cells stick under the floating app header while the PAGE scrolls (default on). A sticky
   * header needs the page to be its scroll container, so the table container drops its horizontal
   * scroll: sticky and horizontal scroll are mutually exclusive. Sticky applies only from a 42rem
   * frame (`@2xl`); below that the table always scrolls inside its frame and the header does not
   * stick. Enable it only for a column set that fits a >= 42rem frame (clients' always-shown set is
   * about 32rem); pass `false` for a table that must scroll sideways at wider frames too (many
   * columns); column `priority` still applies.
   */
  stickyHeader?: boolean;
  /** Visually hidden `<caption>` (the table's accessible name). */
  caption?: string;
  /** Forwarded to the `<table>` element (e.g. `transactions-table`). */
  tableTestId?: string;
  /**
   * Which rows have a detail row (e.g. a per-currency breakdown). A cell toggles it with
   * `row.getToggleExpandedHandler()` and reads `row.getIsExpanded()` / `row.getCanExpand()`, so the
   * memoised columns need no state of their own. The expanded state is keyed by `getRowId` and
   * survives a new `data` array (a client re-sort or a refetch), unlike visibility/selection/page.
   */
  getRowCanExpand?: (row: T) => boolean;
  /**
   * Content of the detail row rendered under an expanded row: one full-width cell (`colSpan` = the
   * visible columns), no `rowTestId`, never opens `rowHref`.
   */
  renderExpandedRow?: (row: T) => ReactNode;
  /**
   * Draws the table frame: card background, border and rounded corners (default on). Pass `false`
   * when the table already sits inside a bordered surface (e.g. `<Card><CardContent>`) to avoid a
   * double border; the `@container` that column `priority` measures is kept either way.
   */
  frame?: boolean;
}

/** Clicks on these (or inside them) keep their own behaviour instead of opening the row. */
const INTERACTIVE_SELECTOR =
  'a, button, input, select, textarea, label, [role="menu"], [role="menuitem"], [role="menuitemcheckbox"], [role="menuitemradio"], [data-row-click="ignore"]';

/** Container-query thresholds of today's matters table (`matters-table.tsx`). */
const PRIORITY_CLASS = {
  low: "hidden @min-[67rem]:table-cell",
  lowest: "hidden @7xl:table-cell",
} as const;

const SKELETON_ROWS = 8;
const DEFAULT_PAGE_SIZE = 25;
const SELECT_COLUMN_ID = "select";

/** `pageSize` honours positive integers only; anything else means "no pagination" (`null`). */
function normalisePageSize(pageSize: number | null | undefined): number | null {
  return typeof pageSize === "number" && Number.isInteger(pageSize) && pageSize > 0
    ? pageSize
    : null;
}

/**
 * From a 42rem frame (`@2xl`, measured on the `@container` frame) the page scrolls, so the
 * container must not be a scroll container (see `stickyHeader`). Below that the base
 * `overflow-x-auto` of `ui/table.tsx` stays in force: narrow frames scroll the table in place.
 */
const STICKY_CONTAINER_CLASS = "@2xl:overflow-x-visible";
/**
 * The floating header is `sticky top-2` and `--app-header-height` tall, so its bottom edge is at
 * header height + 0.5rem. Opaque card background; z-10 stays under the header's z-30. The inset
 * shadow redraws the header rule, which a collapsed-border `tr` cannot carry while stuck. Positioning
 * applies from a 42rem frame only, matching `STICKY_CONTAINER_CLASS`.
 */
const STICKY_HEAD_CLASS =
  "bg-card @2xl:sticky @2xl:top-[calc(var(--app-header-height)+0.5rem)] @2xl:z-10 @2xl:shadow-[inset_0_-1px_0_var(--border)]";

/** Default header for columns without their own `header`: the label, linked when sortable. */
function renderDefaultHeader<T>({ column, table }: HeaderContext<T, unknown>) {
  return <DataTableColumnHeader column={column} sort={table.options.meta?.dataTableSort} />;
}

const DEFAULT_COLUMN = { header: renderDefaultHeader };

function selectColumn<T>(): DataTableColumn<T> {
  return {
    id: SELECT_COLUMN_ID,
    enableHiding: false,
    header: ({ table }) => (
      <Checkbox
        aria-label="Select all rows"
        checked={
          table.getIsAllRowsSelected()
            ? true
            : table.getIsSomeRowsSelected()
              ? "indeterminate"
              : false
        }
        onCheckedChange={(value) => table.toggleAllRowsSelected(value === true)}
      />
    ),
    cell: ({ row }) => (
      <Checkbox
        aria-label="Select row"
        checked={row.getIsSelected()}
        onCheckedChange={(value) => row.toggleSelected(value === true)}
      />
    ),
  };
}

type Meta<T> = ColumnMeta<T, unknown> | undefined;

/**
 * Client-mode list table on TanStack Table 8.21.3 (ADR-369, architecture §15.2.4 / §15.3.3).
 *
 * The URL (or, for a local-state table, the consumer) owns view, filters, search and sort: TanStack
 * runs with `manualSorting` and never sorts or filters. TanStack owns only ephemeral state — column
 * visibility, row selection, page index — and all three reset when `data` changes identity (every
 * RSC navigation sends a new array). Expanded detail rows (`getRowCanExpand`) are keyed by row id
 * and deliberately survive a new `data` array.
 *
 * Markup is the semantic `ui/table` (`table`/`row`/`columnheader`/`cell`; never `role="grid"`). The
 * frame uses tokens and is NOT a `data-slot="card"` (finance specs count cards). Narrow screens use
 * column `priority`; there is no card-list mode.
 */
export function DataTable<T, K extends string = string>({
  columns,
  data,
  getRowId,
  sort,
  toolbar,
  columnVisibility: showColumnMenu = true,
  rowHref,
  rowTestId,
  pageSize: pageSizeProp = null,
  selectable = false,
  exportCsv,
  empty,
  loading = false,
  footer,
  stickyHeader = true,
  caption,
  tableTestId,
  frame = true,
  getRowCanExpand,
  renderExpandedRow,
}: DataTableProps<T, K>) {
  const router = useRouter();
  const pageSize = normalisePageSize(pageSizeProp);
  const paginated = pageSize !== null;

  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
  const [pagination, setPagination] = useState<PaginationState>({
    pageIndex: 0,
    pageSize: pageSize ?? DEFAULT_PAGE_SIZE,
  });

  // Reset the ephemeral state on navigation: adjust state while rendering when the props change
  // (React's "storing information from previous renders"; a setState in an effect is a lint error).
  // Turning `selectable` off clears the selection too, so the CSV export cannot use a stale one.
  const [seen, setSeen] = useState({ data, pageSize, selectable });
  const navigated = seen.data !== data || seen.pageSize !== pageSize;
  if (navigated || seen.selectable !== selectable) {
    setSeen({ data, pageSize, selectable });
    setRowSelection({});
    if (navigated) {
      setColumnVisibility({});
      setPagination((current) => ({
        pageIndex: 0,
        pageSize: seen.pageSize !== pageSize ? (pageSize ?? DEFAULT_PAGE_SIZE) : current.pageSize,
      }));
    }
  }

  const allColumns = useMemo(
    () => (selectable ? [selectColumn<T>(), ...columns] : columns),
    [selectable, columns]
  );

  // eslint-disable-next-line react-hooks/incompatible-library -- TanStack returns a mutable instance; DataTable owns its state and re-renders on every change
  const table = useReactTable({
    data,
    columns: allColumns,
    defaultColumn: DEFAULT_COLUMN,
    getRowId: (row) => getRowId(row),
    getCoreRowModel: getCoreRowModel(),
    getPaginationRowModel: paginated ? getPaginationRowModel() : undefined,
    // Rows arrive sorted by the server; pagination is bypassed entirely when `pageSize` is null.
    manualSorting: true,
    manualPagination: !paginated,
    autoResetPageIndex: false,
    enableRowSelection: selectable,
    state: { columnVisibility, rowSelection, ...(paginated ? { pagination } : {}) },
    onColumnVisibilityChange: setColumnVisibility,
    onRowSelectionChange: setRowSelection,
    onPaginationChange: setPagination,
    // `expanded` stays TanStack's own (uncontrolled) state, keyed by row id: no row model here
    // auto-resets it, and this pins that a new `data` array keeps the open detail rows.
    getRowCanExpand: getRowCanExpand ? (row) => getRowCanExpand(row.original) : undefined,
    autoResetExpanded: false,
    // Headers only call `hrefFor`/`onSort` with a column's `meta.sortKey`, which the consumer draws
    // from `K`.
    meta: { dataTableSort: sort as DataTableSort | undefined },
  });

  const isEmpty = !loading && data.length === 0;
  const showRows = !isEmpty && !loading;

  function priorityClass(meta: Meta<T>): string | undefined {
    if (!meta?.priority || meta.priority === "high") return undefined;
    // The actively sorted column is never hidden.
    if (sort?.key != null && meta.sortKey === sort.key) return undefined;
    return PRIORITY_CLASS[meta.priority];
  }

  function ariaSort(meta: Meta<T>): "ascending" | "descending" | "none" | undefined {
    if (!sort || !meta?.sortKey) return undefined;
    if (sort.key !== meta.sortKey) return "none";
    return sort.dir === "asc" ? "ascending" : "descending";
  }

  function openRow(event: MouseEvent<HTMLTableRowElement>, href: string) {
    if (event.defaultPrevented || event.button !== 0) return;
    // React bubbles events from portals (dialogs, popovers, menus opened from a cell) through the
    // component tree to this row: only clicks inside the row's own DOM open it.
    const target = event.target;
    if (!(target instanceof Element) || !event.currentTarget.contains(target)) return;
    if (target.closest(INTERACTIVE_SELECTOR)) return;
    // Do not navigate when the user is selecting text in the row.
    if (window.getSelection()?.toString()) return;
    router.push(href);
  }

  const canHideColumns =
    showColumnMenu &&
    table.getAllLeafColumns().some((column) => column.getCanHide() && column.columnDef.meta?.label);
  const controls =
    !isEmpty && (canHideColumns || exportCsv) ? (
      <>
        {canHideColumns && <DataTableViewOptions table={table} />}
        {exportCsv && (
          <DataTableCsvButton table={table} filename={exportCsv.filename} disabled={loading} />
        )}
      </>
    ) : undefined;

  const visibleColumns = table.getVisibleLeafColumns();

  return (
    <div data-slot="data-table" className="space-y-3">
      <DataTableToolbar {...toolbar} controls={controls} />
      {isEmpty ? (
        empty
      ) : (
        // `@container`: the priority classes measure this frame, not the viewport.
        <div className={cn("@container", frame && "bg-card rounded-lg border")}>
          <Table
            data-testid={tableTestId}
            aria-busy={loading || undefined}
            containerClassName={stickyHeader ? STICKY_CONTAINER_CLASS : undefined}
          >
            {caption && <TableCaption className="sr-only">{caption}</TableCaption>}
            <TableHeader>
              {table.getHeaderGroups().map((headerGroup) => (
                <TableRow key={headerGroup.id} className="hover:bg-transparent">
                  {headerGroup.headers.map((header) => {
                    const meta = header.column.columnDef.meta;
                    return (
                      <TableHead
                        key={header.id}
                        scope="col"
                        aria-sort={ariaSort(meta)}
                        className={cn(
                          meta?.align === "end" && "text-right",
                          priorityClass(meta),
                          stickyHeader && STICKY_HEAD_CLASS,
                          meta?.headerClassName
                        )}
                      >
                        {header.isPlaceholder
                          ? null
                          : flexRender(header.column.columnDef.header, header.getContext())}
                      </TableHead>
                    );
                  })}
                </TableRow>
              ))}
            </TableHeader>
            <TableBody>
              {loading
                ? Array.from({ length: SKELETON_ROWS }, (_, index) => (
                    <TableRow key={`loading-${index}`} className="hover:bg-transparent">
                      {visibleColumns.map((column) => (
                        <TableCell key={column.id} className={priorityClass(column.columnDef.meta)}>
                          <Skeleton className="h-4 w-full" />
                        </TableCell>
                      ))}
                    </TableRow>
                  ))
                : table.getRowModel().rows.map((row) => (
                    <Fragment key={row.id}>
                      <TableRow
                        data-testid={rowTestId}
                        data-state={row.getIsSelected() ? "selected" : undefined}
                        className={cn(rowHref && "cursor-pointer")}
                        onClick={
                          rowHref ? (event) => openRow(event, rowHref(row.original)) : undefined
                        }
                      >
                        {row.getVisibleCells().map((cell) => {
                          const meta = cell.column.columnDef.meta;
                          return (
                            <TableCell
                              key={cell.id}
                              // A near-miss on a selection checkbox must not open the row.
                              data-row-click={
                                cell.column.id === SELECT_COLUMN_ID ? "ignore" : undefined
                              }
                              className={cn(
                                meta?.align === "end" && "text-right",
                                priorityClass(meta),
                                meta?.cellClassName
                              )}
                            >
                              {flexRender(cell.column.columnDef.cell, cell.getContext())}
                            </TableCell>
                          );
                        })}
                      </TableRow>
                      {renderExpandedRow && row.getIsExpanded() && (
                        <TableRow
                          data-slot="data-table-expanded-row"
                          className="bg-muted/50 hover:bg-muted/50"
                        >
                          <TableCell colSpan={row.getVisibleCells().length}>
                            {renderExpandedRow(row.original)}
                          </TableCell>
                        </TableRow>
                      )}
                    </Fragment>
                  ))}
            </TableBody>
          </Table>
        </div>
      )}
      {showRows && paginated && <DataTablePagination table={table} />}
      {showRows && footer}
    </div>
  );
}
