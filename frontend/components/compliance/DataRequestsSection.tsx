import Link from "next/link";
import { Badge } from "@b2mash/ui/badge";
import { daysBetween, todayInSA } from "@b2mash/shared/date";
import type { DataRequestResponse } from "@/lib/types";
import {
  UrgentDataRequestsTable,
  type UrgentDataRequestRow,
} from "@/components/compliance/UrgentDataRequestsTable";

interface DataRequestsSectionProps {
  openCount: number;
  urgentRequests: DataRequestResponse[];
  orgSlug: string;
}

/**
 * Calendar days from today in South Africa to the yyyy-MM-dd deadline: 0 is due today, negative is
 * overdue (F-669A-A-4: the same answer on the UTC SSR server and in any browser, at any hour).
 */
function getDaysUntilDeadline(deadline: string): number {
  return daysBetween(todayInSA(), deadline);
}

export function DataRequestsSection({
  openCount,
  urgentRequests,
  orgSlug,
}: DataRequestsSectionProps) {
  // Day counts are computed here on the server and handed to the client table as data (DA-7).
  const rows: UrgentDataRequestRow[] = urgentRequests.map((req) => ({
    id: req.id,
    clientName: req.clientName,
    requestType: req.requestType,
    deadline: req.deadline,
    daysUntil: getDaysUntilDeadline(req.deadline),
    status: req.status,
  }));

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <h2 className="text-foreground text-lg font-semibold">Data Requests</h2>
        {openCount > 0 && (
          <Badge variant="neutral">
            {openCount} open {openCount === 1 ? "request" : "requests"}
          </Badge>
        )}
      </div>
      {rows.length === 0 ? (
        <p className="text-muted-foreground text-sm">No open data requests</p>
      ) : (
        <div className="space-y-3">
          <UrgentDataRequestsTable rows={rows} />
          <Link
            href={`/org/${orgSlug}/compliance/requests`}
            className="inline-block text-sm font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400"
          >
            View All Requests
          </Link>
        </div>
      )}
    </div>
  );
}
