"use client";

import { useCallback, useMemo, useState, useTransition } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { ChevronDown, ChevronRight } from "lucide-react";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { formatComplianceDateWithTime } from "@/lib/format";
import type {
  AuditEventFilter,
  AuditEventResponse,
  AuditEventTypeMetadata,
  AuditEventsPage,
  AuditSeverity,
} from "@/lib/api/audit-events";
import { SeverityPill } from "@/components/audit/severity-pill";
import { ActorDisplay } from "@/components/audit/actor-display";
import { EntityCell } from "@/components/audit/entity-cell";
import { AuditDetailsViewer } from "@/components/audit/audit-details-viewer";

const ALL_SEVERITIES: AuditSeverity[] = [
  "INFO",
  "NOTICE",
  "WARNING",
  "CRITICAL",
];

interface AuditLogClientProps {
  slug: string;
  initialEvents: AuditEventsPage;
  metadata: AuditEventTypeMetadata[];
  initialFilter: AuditEventFilter;
  pageSize: number;
}

function isoDateInputValue(iso: string | undefined): string {
  if (!iso) return "";
  // Slice down to YYYY-MM-DD for the <input type="date"> bind
  return iso.length >= 10 ? iso.slice(0, 10) : iso;
}

export function AuditLogClient({
  slug,
  initialEvents,
  metadata: _metadata,
  initialFilter,
  pageSize,
}: AuditLogClientProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [isPending, startTransition] = useTransition();
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const events = initialEvents;
  const { content, page } = events;
  const currentPage = page.number;
  const totalPages = page.totalPages;

  const updateUrl = useCallback(
    (mutator: (params: URLSearchParams) => void) => {
      const params = new URLSearchParams(searchParams?.toString() ?? "");
      mutator(params);
      const qs = params.toString();
      startTransition(() => {
        router.push(qs ? `?${qs}` : "?", { scroll: false });
      });
    },
    [router, searchParams],
  );

  const setParam = useCallback(
    (key: string, value: string | undefined) => {
      updateUrl((params) => {
        if (value === undefined || value === "") {
          params.delete(key);
        } else {
          params.set(key, value);
        }
        // Filter changes reset pagination
        params.delete("page");
      });
    },
    [updateUrl],
  );

  const toggleSeverity = useCallback(
    (sev: AuditSeverity) => {
      const current = new Set(initialFilter.severities ?? []);
      if (current.has(sev)) {
        current.delete(sev);
      } else {
        current.add(sev);
      }
      const arr = ALL_SEVERITIES.filter((s) => current.has(s));
      setParam("severities", arr.length > 0 ? arr.join(",") : undefined);
    },
    [initialFilter.severities, setParam],
  );

  const goToPage = useCallback(
    (next: number) => {
      updateUrl((params) => {
        if (next <= 0) params.delete("page");
        else params.set("page", String(next));
      });
    },
    [updateUrl],
  );

  const toggleRow = useCallback((id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const clearFilters = useCallback(() => {
    updateUrl((params) => {
      ["from", "to", "severities", "actorId", "eventType", "entityType", "entityId", "page"].forEach(
        (k) => params.delete(k),
      );
    });
  }, [updateUrl]);

  const hasActiveFilters = useMemo(
    () =>
      Boolean(
        initialFilter.from ||
          initialFilter.to ||
          (initialFilter.severities && initialFilter.severities.length > 0) ||
          initialFilter.actorId ||
          initialFilter.eventType ||
          initialFilter.entityType ||
          initialFilter.entityId,
      ),
    [initialFilter],
  );

  const selectedSeverities = useMemo(
    () => new Set(initialFilter.severities ?? []),
    [initialFilter.severities],
  );

  const previousHref = (() => {
    const params = new URLSearchParams(searchParams?.toString() ?? "");
    if (currentPage - 1 <= 0) params.delete("page");
    else params.set("page", String(currentPage - 1));
    const qs = params.toString();
    return qs ? `?${qs}` : `/org/${slug}/settings/audit-log`;
  })();

  const nextHref = (() => {
    const params = new URLSearchParams(searchParams?.toString() ?? "");
    params.set("page", String(currentPage + 1));
    return `?${params.toString()}`;
  })();

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Filters</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            <div className="space-y-1">
              <label
                htmlFor="audit-filter-from"
                className="text-xs font-medium text-slate-700 dark:text-slate-300"
              >
                From
              </label>
              <Input
                id="audit-filter-from"
                type="date"
                value={isoDateInputValue(initialFilter.from)}
                onChange={(e) =>
                  setParam(
                    "from",
                    e.target.value
                      ? new Date(e.target.value + "T00:00:00Z").toISOString()
                      : undefined,
                  )
                }
              />
            </div>
            <div className="space-y-1">
              <label
                htmlFor="audit-filter-to"
                className="text-xs font-medium text-slate-700 dark:text-slate-300"
              >
                To
              </label>
              <Input
                id="audit-filter-to"
                type="date"
                value={isoDateInputValue(initialFilter.to)}
                onChange={(e) =>
                  setParam(
                    "to",
                    e.target.value
                      ? new Date(e.target.value + "T23:59:59Z").toISOString()
                      : undefined,
                  )
                }
              />
            </div>
            <div className="space-y-1">
              <label
                htmlFor="audit-filter-actor"
                className="text-xs font-medium text-slate-700 dark:text-slate-300"
              >
                Actor ID
              </label>
              <Input
                id="audit-filter-actor"
                type="text"
                placeholder="UUID"
                defaultValue={initialFilter.actorId ?? ""}
                onBlur={(e) => {
                  const v = e.target.value.trim();
                  if (v !== (initialFilter.actorId ?? "")) {
                    setParam("actorId", v || undefined);
                  }
                }}
              />
            </div>
            <div className="space-y-1">
              <label
                htmlFor="audit-filter-event-type"
                className="text-xs font-medium text-slate-700 dark:text-slate-300"
              >
                Event type
              </label>
              <Input
                id="audit-filter-event-type"
                type="text"
                placeholder="e.g. security.login.failure"
                defaultValue={initialFilter.eventType ?? ""}
                onBlur={(e) => {
                  const v = e.target.value.trim();
                  if (v !== (initialFilter.eventType ?? "")) {
                    setParam("eventType", v || undefined);
                  }
                }}
              />
            </div>
            <div className="space-y-1">
              <label
                htmlFor="audit-filter-entity-type"
                className="text-xs font-medium text-slate-700 dark:text-slate-300"
              >
                Entity type
              </label>
              <Input
                id="audit-filter-entity-type"
                type="text"
                placeholder="e.g. customer"
                defaultValue={initialFilter.entityType ?? ""}
                onBlur={(e) => {
                  const v = e.target.value.trim();
                  if (v !== (initialFilter.entityType ?? "")) {
                    setParam("entityType", v || undefined);
                  }
                }}
              />
            </div>
            <div className="space-y-1">
              <label
                htmlFor="audit-filter-entity-id"
                className="text-xs font-medium text-slate-700 dark:text-slate-300"
              >
                Entity ID
              </label>
              <Input
                id="audit-filter-entity-id"
                type="text"
                placeholder="UUID"
                defaultValue={initialFilter.entityId ?? ""}
                onBlur={(e) => {
                  const v = e.target.value.trim();
                  if (v !== (initialFilter.entityId ?? "")) {
                    setParam("entityId", v || undefined);
                  }
                }}
              />
            </div>
          </div>

          <div className="mt-4">
            <span className="text-xs font-medium text-slate-700 dark:text-slate-300">
              Severity
            </span>
            <div
              className="mt-2 flex flex-wrap gap-2"
              role="group"
              aria-label="Severity filter"
            >
              {ALL_SEVERITIES.map((sev) => {
                const active = selectedSeverities.has(sev);
                return (
                  <button
                    key={sev}
                    type="button"
                    onClick={() => toggleSeverity(sev)}
                    aria-pressed={active}
                    data-testid={`severity-toggle-${sev}`}
                    className={cn(
                      "rounded-full border px-3 py-1 text-xs transition",
                      active
                        ? "border-teal-600 bg-teal-50 text-teal-700 dark:bg-teal-950 dark:text-teal-300"
                        : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400",
                    )}
                  >
                    {sev}
                  </button>
                );
              })}
            </div>
          </div>

          {hasActiveFilters && (
            <div className="mt-4">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={clearFilters}
                data-testid="audit-clear-filters"
              >
                Clear filters
              </Button>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">
            Events
            <span className="ml-2 font-mono text-xs font-normal text-slate-500">
              {page.totalElements.toLocaleString()} total
            </span>
            {isPending && (
              <span className="ml-2 text-xs text-slate-400">Loading…</span>
            )}
          </CardTitle>
        </CardHeader>
        <CardContent>
          {content.length === 0 ? (
            <p className="py-8 text-center text-sm text-slate-500">
              {hasActiveFilters
                ? "No audit events in this range. Try widening the date range or changing filters."
                : "The audit log is empty. Activity is logged automatically once team members start working in Kazi."}
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-10"></TableHead>
                  <TableHead>Time</TableHead>
                  <TableHead>Severity</TableHead>
                  <TableHead>Event</TableHead>
                  <TableHead>Actor</TableHead>
                  <TableHead>Entity</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {content.map((event: AuditEventResponse) => {
                  const isExpanded = expanded.has(event.id);
                  return (
                    <Row
                      key={event.id}
                      event={event}
                      slug={slug}
                      expanded={isExpanded}
                      onToggle={() => toggleRow(event.id)}
                    />
                  );
                })}
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
              Page <span className="font-medium">{currentPage + 1}</span> of{" "}
              {totalPages}
            </>
          ) : (
            <>No events</>
          )}
        </div>
        <div className="flex items-center gap-2">
          {currentPage > 0 ? (
            <Link
              href={previousHref}
              onClick={(e) => {
                e.preventDefault();
                goToPage(currentPage - 1);
              }}
              className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            >
              Previous
            </Link>
          ) : (
            <span className="rounded-md border border-slate-200 bg-slate-50 px-3 py-1.5 text-slate-400 dark:border-slate-700 dark:bg-slate-800">
              Previous
            </span>
          )}
          {currentPage + 1 < totalPages ? (
            <Link
              href={nextHref}
              onClick={(e) => {
                e.preventDefault();
                goToPage(currentPage + 1);
              }}
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

      {/* Hidden default page-size hint to avoid lint dead-code on prop */}
      <span className="hidden" data-testid="audit-page-size">
        {pageSize}
      </span>
    </div>
  );
}

interface RowProps {
  event: AuditEventResponse;
  slug: string;
  expanded: boolean;
  onToggle: () => void;
}

function Row({ event, slug, expanded, onToggle }: RowProps) {
  return (
    <>
      <TableRow data-testid={`audit-row-${event.id}`}>
        <TableCell>
          <button
            type="button"
            onClick={onToggle}
            aria-label={expanded ? "Collapse details" : "Expand details"}
            aria-expanded={expanded}
            data-testid={`audit-row-toggle-${event.id}`}
            className="text-slate-500 hover:text-slate-700 dark:hover:text-slate-300"
          >
            {expanded ? (
              <ChevronDown className="size-4" />
            ) : (
              <ChevronRight className="size-4" />
            )}
          </button>
        </TableCell>
        <TableCell className="font-mono text-xs whitespace-nowrap text-slate-700 dark:text-slate-300">
          {formatComplianceDateWithTime(event.occurredAt)}
        </TableCell>
        <TableCell>
          <SeverityPill severity={event.severity} />
        </TableCell>
        <TableCell className="text-xs">
          <div className="font-medium text-slate-900 dark:text-slate-100">
            {event.label}
          </div>
          <div className="font-mono text-[10px] text-slate-500">
            {event.eventType}
          </div>
        </TableCell>
        <TableCell>
          <ActorDisplay
            actorDisplayName={event.actorDisplayName}
            actorId={event.actorId}
            actorType={event.actorType}
            source={event.source}
            ipAddress={event.ipAddress}
          />
        </TableCell>
        <TableCell>
          <EntityCell
            entityType={event.entityType}
            entityId={event.entityId}
            slug={slug}
          />
        </TableCell>
      </TableRow>
      {expanded && (
        <TableRow data-testid={`audit-row-details-${event.id}`}>
          <TableCell></TableCell>
          <TableCell colSpan={5} className="bg-slate-50/50 dark:bg-slate-900/30">
            <div className="space-y-3 py-2">
              <AuditDetailsViewer details={event.details} />
              <dl className="flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-slate-500">
                {event.source && (
                  <div>
                    <dt className="inline font-semibold">Source: </dt>
                    <dd className="inline font-mono">{event.source}</dd>
                  </div>
                )}
                {event.ipAddress && (
                  <div>
                    <dt className="inline font-semibold">IP: </dt>
                    <dd className="inline font-mono">{event.ipAddress}</dd>
                  </div>
                )}
                {event.userAgent && (
                  <div>
                    <dt className="inline font-semibold">Agent: </dt>
                    <dd className="inline font-mono">{event.userAgent}</dd>
                  </div>
                )}
              </dl>
            </div>
          </TableCell>
        </TableRow>
      )}
    </>
  );
}
