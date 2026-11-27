import { BellRing } from "lucide-react";
import { EmptyState } from "@/components/empty-state";
import { CollectionsHistoryDataTable } from "@/components/collections/collections-history-data-table";
import type { CollectionActivityResponse } from "@/lib/api/collections";

interface CollectionsHistoryTableProps {
  activities: CollectionActivityResponse[];
  slug: string;
  /** Copy shown when there are no activity rows. */
  emptyMessage?: string;
}

/**
 * Presentational, read-only chase-history ledger shared by the invoice-detail
 * (591C.1) and customer-detail (591C.2) surfaces. Renders one row per
 * collection activity — stage, status (+ humanised reason), days-overdue at the
 * time of action, proposal/update timestamps, and a link to the AI reviews
 * queue when the row is a PROPOSED reminder still awaiting approval.
 *
 * Server-compatible: no hooks, handlers, or browser APIs — must NOT carry
 * "use client". Data is fetched in the consuming RSC pages and passed as props;
 * the table itself is the `DataTable` client module (Phase 91 666B), which gets
 * the empty state pre-rendered here (no icon reference crosses the boundary).
 */
export function CollectionsHistoryTable({
  activities,
  slug,
  emptyMessage = "No collection activity.",
}: CollectionsHistoryTableProps) {
  return (
    <CollectionsHistoryDataTable
      activities={activities}
      slug={slug}
      empty={
        <div data-testid="collections-history-empty">
          <EmptyState variant="inline" icon={BellRing} title={emptyMessage} description={null} />
        </div>
      }
    />
  );
}
