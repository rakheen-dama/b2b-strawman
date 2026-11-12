import type { ReactNode } from "react";
import Link from "next/link";
import { ArrowDown, ArrowUp, ChevronsUpDown } from "lucide-react";
import type { Column } from "@tanstack/react-table";
import { cn } from "@/lib/utils";
import type { DataTableSort } from "./define-columns";

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
 * Default `DataTable` header (architecture §15.8.5): the column's `meta.label`, as a `next/link` to
 * `sort.hrefFor(meta.sortKey)` when the column is sortable. Sorting stays in the URL, so the header
 * never sorts client-side. The icon is `aria-hidden` so the `columnheader` name equals the label;
 * `aria-sort` itself sits on the `<th>` (set by `DataTable`).
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
    <ChevronsUpDown aria-hidden="true" className="size-3.5 opacity-40" />
  ) : sort.dir === "asc" ? (
    <ArrowUp aria-hidden="true" className="size-3.5" />
  ) : (
    <ArrowDown aria-hidden="true" className="size-3.5" />
  );

  return (
    <Link
      href={sort.hrefFor(meta.sortKey)}
      scroll={false}
      className={cn(
        "hover:text-foreground focus-visible:ring-ring/50 inline-flex items-center gap-1 rounded-sm outline-none focus-visible:ring-[3px]",
        active && "text-foreground"
      )}
    >
      {content}
      {icon}
    </Link>
  );
}
