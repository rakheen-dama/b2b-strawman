// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): views/datatables/column-visibility/datatable-column-visibilty.tsx.
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
"use client";

import type { Table } from "@tanstack/react-table";
import { Columns3 } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

interface DataTableViewOptionsProps<T> {
  table: Table<T>;
}

/**
 * "Columns" menu: one checkbox item per hideable column with a `meta.label`. The state is local to
 * the table and resets on navigation; a saved view's `columns` still decide the default set.
 */
export function DataTableViewOptions<T>({ table }: DataTableViewOptionsProps<T>) {
  const columns = table
    .getAllLeafColumns()
    .filter((column) => column.getCanHide() && column.columnDef.meta?.label);
  if (columns.length === 0) return null;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="outline" size="sm">
          <Columns3 aria-hidden="true" />
          Columns
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel>Show columns</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {columns.map((column) => (
          <DropdownMenuCheckboxItem
            key={column.id}
            checked={column.getIsVisible()}
            onCheckedChange={(value) => column.toggleVisibility(value === true)}
            // Keep the menu open so several columns can be toggled in one go.
            onSelect={(event) => event.preventDefault()}
          >
            {column.columnDef.meta?.label}
          </DropdownMenuCheckboxItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
