"use client";

import { useCallback, useEffect, useState } from "react";
import { History } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@b2mash/ui/button";
import { EmptyState } from "@/components/empty-state";
import { RelativeDate } from "@/components/ui/relative-date";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { SeverityPill } from "@/components/audit/severity-pill";
import { ActorDisplay } from "@/components/audit/actor-display";
import { AuditDetailsViewer } from "@/components/audit/audit-details-viewer";
import { fetchEntityAuditPage } from "@/lib/actions/audit-events";
import type { AuditEventResponse } from "@/lib/api/audit-events";

export interface AuditTimelineProps {
  entityType: string;
  entityId: string;
  initialPageSize?: number;
  /**
   * Reserved for a future filter strip (Epic 507B). The compact tabbed
   * timeline currently never enables filters; the prop is plumbed so callers
   * can opt-in once filtering ships.
   */
  showFilters?: boolean;
  severityPillSize?: "sm" | "md";
}

/**
 * Chronological vertical timeline of audit events for a single entity.
 *
 * Backend: `GET /api/audit-events/{entityType}/{entityId}` (Epic 507A backend),
 * gated by `TEAM_OVERSIGHT`. Rendered top→bottom DESC by occurredAt.
 *
 * Pagination: appends pages via a "Load more" button.
 *
 * Click a row to expand the details viewer + metadata footer.
 */
export function AuditTimeline({
  entityType,
  entityId,
  initialPageSize = 20,
  // showFilters is reserved for future use — see Epic 507B. The prop is
  // accepted so call sites can be future-proofed without churn.
  showFilters: _showFilters = false,
  severityPillSize = "sm",
}: AuditTimelineProps) {
  const [events, setEvents] = useState<AuditEventResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  // Load the first page on mount / when entity identity changes.
  useEffect(() => {
    let cancelled = false;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- Intentionally reset state when the entity identity changes; the request is async, so the state must be cleared before the new fetch resolves.
    setIsLoading(true);
    setError(null);
    setEvents([]);
    setPage(0);
    setExpandedId(null);
    fetchEntityAuditPage(entityType, entityId, 0, initialPageSize)
      .then((res) => {
        if (cancelled) return;
        setEvents(res.content);
        setTotalPages(res.page.totalPages);
        setPage(res.page.number);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setError(err instanceof Error ? err.message : "Failed to load audit events");
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [entityType, entityId, initialPageSize]);

  const handleLoadMore = useCallback(async () => {
    setIsLoadingMore(true);
    setError(null);
    try {
      const next = page + 1;
      const res = await fetchEntityAuditPage(entityType, entityId, next, initialPageSize);
      setEvents((prev) => [...prev, ...res.content]);
      setPage(res.page.number);
      setTotalPages(res.page.totalPages);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load more audit events");
    } finally {
      setIsLoadingMore(false);
    }
  }, [entityType, entityId, page, initialPageSize]);

  if (isLoading) {
    return (
      <div
        className="py-12 text-center text-sm text-slate-500"
        data-testid="audit-timeline-loading"
      >
        Loading audit events…
      </div>
    );
  }

  if (error) {
    return (
      <div
        className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300"
        data-testid="audit-timeline-error"
      >
        {error}
      </div>
    );
  }

  if (events.length === 0) {
    return (
      <EmptyState
        icon={History}
        title="No audit events"
        description={`No audit events for this ${entityType}`}
      />
    );
  }

  const hasMore = page + 1 < totalPages;

  return (
    <div className="space-y-2" data-testid="audit-timeline">
      <ol className="space-y-0">
        {events.map((row, idx) => {
          const isExpanded = expandedId === row.id;
          return (
            <li
              key={row.id}
              data-testid="audit-timeline-row"
              data-event-id={row.id}
              className={cn(
                "border-b border-slate-100 dark:border-slate-800",
                idx % 2 === 1 && "bg-slate-50/50 dark:bg-slate-900/40"
              )}
            >
              <button
                type="button"
                onClick={() => setExpandedId(isExpanded ? null : row.id)}
                aria-expanded={isExpanded}
                className={cn(
                  "flex w-full items-center gap-3 px-2 py-2 text-left text-sm",
                  "hover:bg-slate-50 dark:hover:bg-slate-900",
                  "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-teal-500"
                )}
              >
                <SeverityPill severity={row.severity} size={severityPillSize} />
                <span className="flex-1 truncate text-slate-800 dark:text-slate-200">
                  {row.label}
                </span>
                <span className="hidden shrink-0 sm:inline-block">
                  <ActorDisplay
                    actorDisplayName={row.actorDisplayName}
                    actorId={row.actorId}
                    actorType={row.actorType}
                    source={row.source}
                    ipAddress={row.ipAddress}
                  />
                </span>
                <TooltipProvider>
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <span className="shrink-0 cursor-help text-xs text-slate-500">
                        <RelativeDate iso={row.occurredAt} />
                      </span>
                    </TooltipTrigger>
                    <TooltipContent>
                      <span className="font-mono text-[11px]">{row.occurredAt}</span>
                    </TooltipContent>
                  </Tooltip>
                </TooltipProvider>
              </button>

              {isExpanded && (
                <div
                  className="border-t border-slate-100 bg-slate-50/50 px-3 py-3 dark:border-slate-800 dark:bg-slate-900/30"
                  data-testid="audit-timeline-row-expanded"
                >
                  <AuditDetailsViewer details={row.details} />
                  <dl className="mt-3 grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-[11px] text-slate-500">
                    <dt className="font-semibold opacity-70">Occurred:</dt>
                    <dd className="font-mono break-all">{row.occurredAt}</dd>
                    {row.source && (
                      <>
                        <dt className="font-semibold opacity-70">Source:</dt>
                        <dd className="font-mono break-all">{row.source}</dd>
                      </>
                    )}
                    {row.ipAddress && (
                      <>
                        <dt className="font-semibold opacity-70">IP:</dt>
                        <dd className="font-mono break-all">{row.ipAddress}</dd>
                      </>
                    )}
                    {row.userAgent && (
                      <>
                        <dt className="font-semibold opacity-70">User Agent:</dt>
                        <dd className="font-mono break-all">{row.userAgent}</dd>
                      </>
                    )}
                  </dl>
                </div>
              )}
            </li>
          );
        })}
      </ol>

      {hasMore && (
        <div className="flex justify-center pt-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleLoadMore}
            disabled={isLoadingMore}
            data-testid="audit-timeline-load-more"
          >
            {isLoadingMore ? "Loading…" : "Load more"}
          </Button>
        </div>
      )}
    </div>
  );
}
