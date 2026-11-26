import type { ReactNode } from "react";
import Link from "next/link";
import { ArrowDown, ArrowUp, ChevronsUpDown } from "lucide-react";
import type { Column } from "@tanstack/react-table";
import { cn } from "@/lib/utils";
import type { DataTableSort } from "./define-columns";

/**
 * Reserved by `DataTable` on a sortable `th` (F1, architecture §18.2.6): the sort icon overflows
 * its label into this right padding, so it adds no width to the column and an active icon takes the
 * same slot as the inactive one (sorting never shifts a column).
 */
export const SORTABLE_HEAD_CLASS = "pr-5";

/**
 * The icon's slot (D-38): anchored to the label, not the cell. The link or button is `relative`
 * and the icon sits 2px after its right edge (`left-full ml-0.5`), so it stays next to its own
 * label however wide the column is. 2px + 14px fits the 20px `SORTABLE_HEAD_CLASS` reserves, also
 * when the label ends at the content edge (right-aligned or column-filling headers).
 */
const SORT_ICON_SLOT_CLASS = "absolute top-1/2 left-full ml-0.5 size-3.5 -translate-y-1/2";

interface DataTableColumnHeaderProps<T> {
  column: Column<T, unknown>;
  sort?: DataTableSort;
  /**
   * Replaces the visible label (e.g. a long label broken over two lines). Its text content must
   * equal `meta.label`, which stays the Columns-menu label, the CSV header and the accessible name.
   */
  children?: ReactNode;
}

/**
 * Default `DataTable` header (architecture §15.8.5): the column's `meta.label` when the column is
 * sortable, as a `next/link` to `sort.hrefFor(meta.sortKey)` (URL-owned sort) or as a
 * `<button type="button">` calling `sort.onSort(meta.sortKey)` (local-state sort). Either way the
 * kit never sorts rows itself: the consumer (URL/RSC or local state) passes them sorted. The icon is
 * `aria-hidden` so the `columnheader` name equals the label; `aria-sort` itself sits on the `<th>`
 * (set by `DataTable`). The inactive icon stays visible at all times at `opacity-40` (no hover-only
 * reveal: sortability must be discoverable without a pointer) but is out of flow, just after the
 * label in the padding `SORTABLE_HEAD_CLASS` reserves; it stays inside the link or button, so
 * clicking it sorts.
 */
export function DataTableColumnHeader<T>({
  column,
  sort,
  children,
}: DataTableColumnHeaderProps<T>) {
  const meta = column.columnDef.meta;
  const content = children ?? meta?.label ?? column.id;
  if (!meta?.sortKey || !sort) return <span>{content}</span>;

  const active = sort.key === meta.sortKey;
  const icon = !active ? (
    <ChevronsUpDown aria-hidden="true" className={cn(SORT_ICON_SLOT_CLASS, "opacity-40")} />
  ) : sort.dir === "asc" ? (
    <ArrowUp aria-hidden="true" className={SORT_ICON_SLOT_CLASS} />
  ) : (
    <ArrowDown aria-hidden="true" className={SORT_ICON_SLOT_CLASS} />
  );

  const className = cn(
    "hover:text-foreground focus-visible:ring-ring/50 relative inline-flex items-center rounded-sm outline-none focus-visible:ring-[3px]",
    active && "text-foreground"
  );

  // Not `"onSort" in sort`: both variants declare `onSort` (the URL one as `?: never`), so only a
  // value check narrows the union.
  if (sort.onSort !== undefined) {
    const { onSort } = sort;
    const sortKey = meta.sortKey;
    return (
      <button type="button" onClick={() => onSort(sortKey)} className={className}>
        {content}
        {icon}
      </button>
    );
  }

  return (
    <Link href={sort.hrefFor(meta.sortKey)} scroll={false} className={className}>
      {content}
      {icon}
    </Link>
  );
}
