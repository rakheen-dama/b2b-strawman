import Link from "next/link";
import { invoiceStatusLabel } from "@/components/invoices/status-badge";
import type { InvoiceStatus } from "@/lib/types";

const PILL_CLASS = "rounded-full px-3 py-1 text-sm transition-colors";
const PILL_ACTIVE = "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900";
const PILL_IDLE =
  "bg-slate-100 text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700";

interface FeeNoteStatusFilterProps {
  slug: string;
  statusOptions: InvoiceStatus[];
  /** The validated `?status=`; undefined → "All" is active. */
  activeStatus?: InvoiceStatus;
}

/**
 * The fee notes list's status filter links (server-safe, Phase 91 666A: moved from the page into the
 * DataTable toolbar). Hrefs are unchanged: `/invoices` and `/invoices?status=<STATUS>` (they drop
 * `customerId`, as before).
 */
export function FeeNoteStatusFilter({
  slug,
  statusOptions,
  activeStatus,
}: FeeNoteStatusFilterProps) {
  return (
    <div className="flex flex-wrap items-center gap-3">
      <span className="text-muted-foreground text-sm font-medium">Filter:</span>
      <Link
        href={`/org/${slug}/invoices`}
        className={`${PILL_CLASS} ${!activeStatus ? PILL_ACTIVE : PILL_IDLE}`}
      >
        All
      </Link>
      {statusOptions.map((status) => (
        <Link
          key={status}
          href={`/org/${slug}/invoices?status=${status}`}
          className={`${PILL_CLASS} ${activeStatus === status ? PILL_ACTIVE : PILL_IDLE}`}
        >
          {invoiceStatusLabel(status)}
        </Link>
      ))}
    </div>
  );
}
