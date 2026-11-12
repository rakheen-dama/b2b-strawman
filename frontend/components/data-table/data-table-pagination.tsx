// Adapted from admincn (shadcn-nextjs-admincn-admin-template 1.0.0): views/apps/users/list/user-pagination.tsx and views/datatables/page-size-selectior (datatable-invoice.tsx).
// Used under the purchased admincn licence (founder-confirmed 2026-11-11); translated Base UI → Radix per ADR-368.
"use client";

import { useId } from "react";
import type { Table } from "@tanstack/react-table";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Label } from "@b2mash/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const PAGE_SIZE_OPTIONS = [10, 25, 50, 100];

interface DataTablePaginationProps<T> {
  table: Table<T>;
}

/**
 * Page-size `Select` + Previous/Next + "Page X of Y". Rendered by `DataTable` only when the page
 * opts into a `pageSize`; the page index is local state (nothing is written to the URL, ADR-369).
 */
export function DataTablePagination<T>({ table }: DataTablePaginationProps<T>) {
  const id = useId();
  const { pageIndex, pageSize } = table.getState().pagination;
  const options = Array.from(new Set([...PAGE_SIZE_OPTIONS, pageSize])).sort((a, b) => a - b);
  const pageCount = Math.max(table.getPageCount(), 1);

  return (
    <div className="flex items-center justify-between gap-3 max-sm:flex-col">
      <div className="flex items-center gap-2">
        <Label htmlFor={id} className="text-muted-foreground font-normal">
          Rows per page
        </Label>
        <Select
          value={String(pageSize)}
          onValueChange={(value) => table.setPageSize(Number(value))}
        >
          <SelectTrigger id={id} size="sm" className="w-fit">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {options.map((option) => (
              <SelectItem key={option} value={String(option)}>
                {option}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <p className="text-muted-foreground text-sm" aria-live="polite">
        Page {pageIndex + 1} of {pageCount}
      </p>
      <div className="flex items-center gap-1">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => table.previousPage()}
          disabled={!table.getCanPreviousPage()}
          aria-label="Go to previous page"
        >
          <ChevronLeft aria-hidden="true" />
          <span className="max-sm:hidden">Previous</span>
        </Button>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => table.nextPage()}
          disabled={!table.getCanNextPage()}
          aria-label="Go to next page"
        >
          <span className="max-sm:hidden">Next</span>
          <ChevronRight aria-hidden="true" />
        </Button>
      </div>
    </div>
  );
}
