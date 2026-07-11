import Link from "next/link";

interface CollectionsHistoryPaginationProps {
  slug: string;
  customerId: string;
  /** Zero-based current page index. */
  number: number;
  totalPages: number;
}

/**
 * Link-driven pager for the customer chase-history section (591C.2). Kept a
 * presentational server component (no client state) so navigation stays RSC —
 * each control is a plain <Link> carrying `tab=details` so the Details tab stays
 * active after navigation. Renders nothing when there is a single page.
 */
export function CollectionsHistoryPagination({
  slug,
  customerId,
  number,
  totalPages,
}: CollectionsHistoryPaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  const hasPrev = number > 0;
  const hasNext = number < totalPages - 1;
  const hrefFor = (page: number) =>
    `/org/${slug}/customers/${customerId}?tab=details&chasePage=${page}`;

  return (
    <div className="flex items-center justify-between" data-testid="chase-history-pagination">
      {hasPrev ? (
        <Link
          href={hrefFor(number - 1)}
          className="text-sm font-medium text-teal-600 hover:underline dark:text-teal-400"
        >
          Previous
        </Link>
      ) : (
        <span className="text-sm text-slate-400 dark:text-slate-500">Previous</span>
      )}
      <span className="text-xs text-slate-500 dark:text-slate-400">
        Page {number + 1} of {totalPages}
      </span>
      {hasNext ? (
        <Link
          href={hrefFor(number + 1)}
          className="text-sm font-medium text-teal-600 hover:underline dark:text-teal-400"
        >
          Next
        </Link>
      ) : (
        <span className="text-sm text-slate-400 dark:text-slate-500">Next</span>
      )}
    </div>
  );
}
