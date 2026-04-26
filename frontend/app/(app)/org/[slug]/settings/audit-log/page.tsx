import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { ApiError } from "@/lib/api/client";
import { listAuditEvents, type AuditEventResponse } from "@/lib/api/audit-events";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatComplianceDateWithTime } from "@/lib/format";

const PAGE_SIZE = 50;

function resolveActor(event: AuditEventResponse): string {
  if (event.actorId) return event.actorId;
  if (event.actorType) return `(${event.actorType.toLowerCase()})`;
  return "system";
}

export default async function AuditLogPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ page?: string }>;
}) {
  const { slug } = await params;
  const { page: pageParam } = await searchParams;
  const requestedPage = Math.max(0, parseInt(pageParam ?? "0", 10) || 0);

  const backLink = (
    <Link
      href={`/org/${slug}/settings`}
      className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
    >
      <ChevronLeft className="size-4" />
      Settings
    </Link>
  );

  let events;
  try {
    events = await listAuditEvents({ page: requestedPage, size: PAGE_SIZE });
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      return (
        <div className="space-y-8">
          {backLink}
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Audit log</h1>
          <p className="text-slate-600 dark:text-slate-400">
            Not authorised. The audit log is only visible to members with the{" "}
            <code className="font-mono text-xs">TEAM_OVERSIGHT</code> capability.
          </p>
        </div>
      );
    }
    throw error;
  }

  const { content, page } = events;
  const currentPage = page.number;
  const totalPages = page.totalPages;
  const hasPrevious = currentPage > 0;
  const hasNext = currentPage + 1 < totalPages;
  const previousHref = `/org/${slug}/settings/audit-log?page=${currentPage - 1}`;
  const nextHref = `/org/${slug}/settings/audit-log?page=${currentPage + 1}`;

  return (
    <div className="space-y-8">
      {backLink}

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Audit log</h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Read-only feed of audit events. Filters, exports, and faceted search are coming in a
          later release.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">
            Events
            <span className="ml-2 font-mono text-xs font-normal text-slate-500">
              {page.totalElements.toLocaleString()} total
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          {content.length === 0 ? (
            <p className="text-sm text-slate-500">No audit events recorded yet.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Occurred At</TableHead>
                  <TableHead>Actor Type</TableHead>
                  <TableHead>Actor</TableHead>
                  <TableHead>Event Type</TableHead>
                  <TableHead>Entity Type</TableHead>
                  <TableHead>Entity ID</TableHead>
                  <TableHead>Details</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {content.map((event) => (
                  <TableRow key={event.id}>
                    <TableCell className="font-mono text-xs text-slate-700 dark:text-slate-300">
                      {formatComplianceDateWithTime(event.occurredAt)}
                    </TableCell>
                    <TableCell className="text-xs text-slate-600 dark:text-slate-400">
                      {event.actorType ?? "—"}
                    </TableCell>
                    <TableCell className="font-mono text-xs">{resolveActor(event)}</TableCell>
                    <TableCell className="text-xs">{event.eventType}</TableCell>
                    <TableCell className="text-xs">{event.entityType}</TableCell>
                    <TableCell className="font-mono text-xs text-slate-600 dark:text-slate-400">
                      {event.entityId ?? "—"}
                    </TableCell>
                    <TableCell className="max-w-md whitespace-normal">
                      {event.details && Object.keys(event.details).length > 0 ? (
                        <details className="text-xs">
                          <summary className="cursor-pointer text-slate-500 hover:text-slate-700 dark:hover:text-slate-300">
                            View
                          </summary>
                          <pre className="mt-2 max-h-64 overflow-auto rounded bg-slate-50 p-2 font-mono text-[11px] whitespace-pre-wrap text-slate-700 dark:bg-slate-900 dark:text-slate-300">
                            {JSON.stringify(event.details, null, 2)}
                          </pre>
                        </details>
                      ) : (
                        <span className="text-xs text-slate-400">—</span>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <nav
        aria-label="Audit log pagination"
        className="flex items-center justify-between gap-3 text-sm"
      >
        <div className="text-slate-500">
          {totalPages > 0 ? (
            <>
              Page <span className="font-medium">{currentPage + 1}</span> of {totalPages}
            </>
          ) : (
            <>No events</>
          )}
        </div>
        <div className="flex items-center gap-2">
          {hasPrevious ? (
            <Link
              href={previousHref}
              className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            >
              Previous
            </Link>
          ) : (
            <span className="rounded-md border border-slate-200 bg-slate-50 px-3 py-1.5 text-slate-400 dark:border-slate-700 dark:bg-slate-800">
              Previous
            </span>
          )}
          {hasNext ? (
            <Link
              href={nextHref}
              className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            >
              Next
            </Link>
          ) : (
            <span className="rounded-md border border-slate-200 bg-slate-50 px-3 py-1.5 text-slate-400 dark:border-slate-700 dark:bg-slate-800">
              Next
            </span>
          )}
        </div>
      </nav>
    </div>
  );
}
