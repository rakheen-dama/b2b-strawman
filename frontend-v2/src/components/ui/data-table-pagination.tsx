"use client";

import * as React from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

interface PageInfo {
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

interface DataTablePaginationProps {
  page: PageInfo;
  onPageChange?: (page: number) => void;
  className?: string;
}

function DataTablePagination({
  page,
  onPageChange,
  className,
}: DataTablePaginationProps) {
  const { totalElements, totalPages, size, number: currentPage } = page;

  const start = totalElements === 0 ? 0 : currentPage * size + 1;
  const end = Math.min((currentPage + 1) * size, totalElements);

  // Build page numbers to display (show up to 5 pages around current)
  const pageNumbers = React.useMemo(() => {
    if (totalPages <= 7) {
      return Array.from({ length: totalPages }, (_, i) => i);
    }

    const pages: (number | "ellipsis")[] = [];
    pages.push(0);

    const rangeStart = Math.max(1, currentPage - 1);
    const rangeEnd = Math.min(totalPages - 2, currentPage + 1);

    if (rangeStart > 1) pages.push("ellipsis");
    for (let i = rangeStart; i <= rangeEnd; i++) {
      pages.push(i);
    }
    if (rangeEnd < totalPages - 2) pages.push("ellipsis");

    pages.push(totalPages - 1);
    return pages;
  }, [totalPages, currentPage]);

  return (
    <div
      className={cn(
        "flex items-center justify-between px-2 py-3",
        className
      )}
    >
      <p className="text-sm text-slate-500 dark:text-slate-400">
        Showing{" "}
        <span className="font-medium text-slate-700 dark:text-slate-200">
          {start}
        </span>
        {totalElements > 0 && (
          <>
            -
            <span className="font-medium text-slate-700 dark:text-slate-200">
              {end}
            </span>
          </>
        )}{" "}
        of{" "}
        <span className="font-medium text-slate-700 dark:text-slate-200">
          {totalElements}
        </span>
      </p>

      <div className="flex items-center gap-1">
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={() => onPageChange?.(currentPage - 1)}
          disabled={currentPage === 0}
          aria-label="Previous page"
        >
          <ChevronLeft className="size-4" />
        </Button>

        {pageNumbers.map((p, idx) =>
          p === "ellipsis" ? (
            <span
              key={`ellipsis-${idx}`}
              className="px-1 text-sm text-slate-400"
            >
              ...
            </span>
          ) : (
            <Button
              key={p}
              variant={p === currentPage ? "secondary" : "ghost"}
              size="icon-sm"
              onClick={() => onPageChange?.(p)}
              aria-label={`Page ${p + 1}`}
              aria-current={p === currentPage ? "page" : undefined}
            >
              {p + 1}
            </Button>
          )
        )}

        <Button
          variant="ghost"
          size="icon-sm"
          onClick={() => onPageChange?.(currentPage + 1)}
          disabled={currentPage >= totalPages - 1}
          aria-label="Next page"
        >
          <ChevronRight className="size-4" />
        </Button>
      </div>
    </div>
  );
}

export { DataTablePagination, type DataTablePaginationProps, type PageInfo };
