import type { ReactNode } from "react";
import Link from "next/link";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import type { DataTableServerPagination } from "./define-columns";

interface DataTablePageLinksProps {
  pagination: DataTableServerPagination;
  /** Wraps the pager in `<nav aria-label>` (only where one exists today, e.g. the audit log). */
  label?: string;
}

/**
 * Server-paged pager of `DataTable` (ADR-395), lifted from other-creditors' hand-rolled pager:
 * "Page {n} of {m}" and Previous / Next whose visible text is their accessible name (never the
 * local pager's "Go to …" `aria-label`s). Links (`hrefFor`) with a disabled button at each end, or
 * buttons calling `onPageChange`. Rendered only by `DataTable`, which decides when it shows.
 */
export function DataTablePageLinks({ pagination, label }: DataTablePageLinksProps) {
  const { pageIndex, pageCount } = pagination;
  const pager = (
    <div className="mt-4 flex items-center justify-between border-t pt-4">
      <p className="text-muted-foreground text-sm">
        Page {pageIndex + 1} of {pageCount}
      </p>
      <div className="flex items-center gap-2">
        <PageControl pagination={pagination} target={pageIndex - 1} enabled={pageIndex > 0}>
          <ChevronLeft className="mr-1 size-4" />
          Previous
        </PageControl>
        <PageControl
          pagination={pagination}
          target={pageIndex + 1}
          enabled={pageIndex < pageCount - 1}
        >
          Next
          <ChevronRight className="ml-1 size-4" />
        </PageControl>
      </div>
    </div>
  );
  return label ? <nav aria-label={label}>{pager}</nav> : pager;
}

function PageControl({
  pagination,
  target,
  enabled,
  children,
}: {
  pagination: DataTableServerPagination;
  target: number;
  enabled: boolean;
  children: ReactNode;
}) {
  // A value check, not `"onPageChange" in`: both variants declare the key (one as `?: never`).
  if (pagination.onPageChange !== undefined) {
    const { onPageChange } = pagination;
    return (
      <Button
        type="button"
        variant="outline"
        size="sm"
        disabled={!enabled}
        onClick={() => onPageChange(target)}
      >
        {children}
      </Button>
    );
  }
  if (!enabled) {
    return (
      <Button variant="outline" size="sm" disabled>
        {children}
      </Button>
    );
  }
  return (
    <Button asChild variant="outline" size="sm">
      <Link href={pagination.hrefFor(target)}>{children}</Link>
    </Button>
  );
}
