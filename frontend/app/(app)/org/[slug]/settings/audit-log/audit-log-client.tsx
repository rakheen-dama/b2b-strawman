"use client";

import { useCallback, useEffect, useMemo, useRef, useState, useTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { ChevronDown, ChevronRight } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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
  AuditEventsPage,
  AuditEventTypeMetadata,
  AuditSeverity,
} from "@/lib/api/audit-events";
import { SeverityPill } from "@/components/audit/severity-pill";
import { ActorDisplay } from "@/components/audit/actor-display";
import { EntityCell } from "@/components/audit/entity-cell";
import { AuditDetailsViewer } from "@/components/audit/audit-details-viewer";
import {
  PRESET_OPTIONS,
  resolvePreset,
  type PresetName,
} from "@/components/audit/presets";

const ALL_SEVERITIES: AuditSeverity[] = ["INFO", "NOTICE", "WARNING", "CRITICAL"];

const FILTER_KEYS = [
  "from",
  "to",
  "severities",
  "actorId",
  "eventType",
  "entityType",
  "entityId",
  "page",
] as const;

interface AuditLogClientProps {
  slug: string;
  initialEvents: AuditEventsPage;
  initialFilter: AuditEventFilter;
  /**
   * Audit-event metadata catalogue. Used by `applyPreset()` to resolve
   * `group=COMPLIANCE` / `group=SECURITY` to concrete event-type lists.
   * Defaults to `[]` so existing tests that don't supply metadata still work.
   */
  metadata?: AuditEventTypeMetadata[];
}

function isoDateInputValue(iso: string | undefined): string {
  if (!iso) return "";
  // Slice down to YYYY-MM-DD for the <input type="date"> bind
  return iso.length >= 10 ? iso.slice(0, 10) : iso;
}

interface FilterTextInputProps {
  id: string;
  label: string;
  placeholder?: string;
  initialValue: string;
  inputRef: React.RefObject<HTMLInputElement | null>;
  onCommit: (value: string) => void;
}

function FilterTextInput({
  id,
  label,
  placeholder,
  initialValue,
  inputRef,
  onCommit,
}: FilterTextInputProps) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="text-xs font-medium text-slate-700 dark:text-slate-300">
        {label}
      </label>
      <Input
        id={id}
        ref={inputRef}
        type="text"
        placeholder={placeholder}
        defaultValue={initialValue}
        onBlur={(e) => {
          const v = e.target.value.trim();
          if (v !== initialValue) {
            onCommit(v);
          }
        }}
      />
    </div>
  );
}

export function AuditLogClient({
  slug,
  initialEvents,
  initialFilter,
  metadata = [],
}: AuditLogClientProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [isPending, startTransition] = useTransition();
  const [expanded, setExpanded] = useState<Set<string>>(new Set());

  const events = initialEvents;
  const { content, page } = events;
  const currentPage = page.number;
  const totalPages = page.totalPages;

  // Refs for the text inputs so that filter actions which fire WITHOUT
  // blurring an in-progress input first (e.g. clicking a severity chip,
  // clicking "Clear filters") can flush the typed value into the URL.
  const actorIdRef = useRef<HTMLInputElement | null>(null);
  const eventTypeRef = useRef<HTMLInputElement | null>(null);
  const entityTypeRef = useRef<HTMLInputElement | null>(null);
  const entityIdRef = useRef<HTMLInputElement | null>(null);

  const buildPath = useCallback(
    (qs: string) => `/org/${slug}/settings/audit-log${qs ? `?${qs}` : ""}`,
    [slug]
  );

  // Read the latest searchParams inside the call rather than capturing it in
  // closure — this avoids concurrent updates clobbering each other when the
  // user fires multiple filter changes back-to-back inside a transition.
  const searchParamsRef = useRef(searchParams);
  useEffect(() => {
    searchParamsRef.current = searchParams;
  }, [searchParams]);

  const updateUrl = useCallback(
    (mutator: (params: URLSearchParams) => void) => {
      const latest = searchParamsRef.current;
      const params = new URLSearchParams(latest?.toString() ?? "");
      mutator(params);
      const qs = params.toString();
      startTransition(() => {
        router.push(buildPath(qs), { scroll: false });
      });
    },
    [router, buildPath]
  );

  // Flush any pending in-progress text input into the URLSearchParams before
  // applying further mutations. Called by handlers that aren't already a blur
  // on a text input (severity toggle, clear, pagination).
  const flushPendingInputs = useCallback(
    (params: URLSearchParams) => {
      const flush = (
        key: string,
        ref: React.RefObject<HTMLInputElement | null>,
        prev: string | undefined
      ) => {
        const node = ref.current;
        if (!node) return;
        const v = node.value.trim();
        if (v === (prev ?? "")) return;
        if (v) params.set(key, v);
        else params.delete(key);
      };
      flush("actorId", actorIdRef, initialFilter.actorId);
      flush("eventType", eventTypeRef, initialFilter.eventType);
      flush("entityType", entityTypeRef, initialFilter.entityType);
      flush("entityId", entityIdRef, initialFilter.entityId);
    },
    [
      initialFilter.actorId,
      initialFilter.eventType,
      initialFilter.entityType,
      initialFilter.entityId,
    ]
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
    [updateUrl]
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
      updateUrl((params) => {
        flushPendingInputs(params);
        if (arr.length > 0) {
          params.set("severities", arr.join(","));
        } else {
          params.delete("severities");
        }
        params.delete("page");
      });
    },
    [initialFilter.severities, updateUrl, flushPendingInputs]
  );

  const goToPage = useCallback(
    (next: number) => {
      updateUrl((params) => {
        flushPendingInputs(params);
        if (next <= 0) params.delete("page");
        else params.set("page", String(next));
      });
    },
    [updateUrl, flushPendingInputs]
  );

  const toggleRow = useCallback((id: string) => {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  // Clear stale expanded ids when the page (or filter set) changes — the
  // event ids on the new page are different. Deps only change on server
  // refresh, so this can't cause a render loop.
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- Resets ephemeral UI selection on page change; not a render loop.
    setExpanded(new Set());
  }, [currentPage]);

  const clearFilters = useCallback(() => {
    updateUrl((params) => {
      // Don't flush pending inputs — the user explicitly asked to clear.
      FILTER_KEYS.forEach((k) => params.delete(k));
    });
  }, [updateUrl]);

  // Epic 506B / 506.9 — preset application. Presets replace (not stack) all
  // filter keys, then set the preset's deltas. All four are URL mutations only;
  // no backend round-trip.
  const applyPreset = useCallback(
    (preset: PresetName) => {
      const def = resolvePreset(preset, metadata);
      updateUrl((params) => {
        FILTER_KEYS.forEach((k) => params.delete(k));
        if (def.from) params.set("from", def.from);
        if (def.severities && def.severities.length > 0) {
          params.set("severities", def.severities.join(","));
        }
        if (def.eventTypes && def.eventTypes.length > 0) {
          // TODO(506B-followup): the backend list endpoint accepts a single
          // `eventType` param. For multi-event-type presets (Compliance,
          // Security, Financial approvals), we narrow to the first match.
          // Full multi-value support requires a backend change (out of scope
          // for 506B per CLAUDE.md "one fix per PR").
          params.set("eventType", def.eventTypes[0]);
        }
      });
    },
    [updateUrl, metadata]
  );

  const hasActiveFilters = useMemo(
    () =>
      Boolean(
        initialFilter.from ||
        initialFilter.to ||
        (initialFilter.severities && initialFilter.severities.length > 0) ||
        initialFilter.actorId ||
        initialFilter.eventType ||
        initialFilter.entityType ||
        initialFilter.entityId
      ),
    [initialFilter]
  );

  const selectedSeverities = useMemo(
    () => new Set(initialFilter.severities ?? []),
    [initialFilter.severities]
  );

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Preset</CardTitle>
        </CardHeader>
        <CardContent>
          <label
            htmlFor="audit-preset-select"
            className="sr-only"
          >
            Filter preset
          </label>
          <select
            id="audit-preset-select"
            data-testid="audit-preset-select"
            defaultValue=""
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
            onChange={(e) => {
              const v = e.target.value as PresetName | "";
              if (v) {
                applyPreset(v);
                // Reset the select so picking the same preset again still
                // fires onChange.
                e.target.value = "";
              }
            }}
          >
            <option value="">— Choose a preset —</option>
            {PRESET_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </CardContent>
      </Card>

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
              {/* UTC-day semantics: we map the picker's local YYYY-MM-DD to
                  the start of that UTC day (00:00:00.000Z). */}
              <Input
                id="audit-filter-from"
                type="date"
                value={isoDateInputValue(initialFilter.from)}
                onChange={(e) =>
                  setParam(
                    "from",
                    e.target.value
                      ? new Date(e.target.value + "T00:00:00.000Z").toISOString()
                      : undefined
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
              {/* UTC-day semantics: pin the upper bound to the very last
                  millisecond of the chosen UTC day so events that occur in
                  the final second of the day are not dropped. */}
              <Input
                id="audit-filter-to"
                type="date"
                value={isoDateInputValue(initialFilter.to)}
                onChange={(e) =>
                  setParam(
                    "to",
                    e.target.value
                      ? new Date(e.target.value + "T23:59:59.999Z").toISOString()
                      : undefined
                  )
                }
              />
            </div>
            <FilterTextInput
              id="audit-filter-actor"
              label="Actor ID"
              placeholder="UUID"
              initialValue={initialFilter.actorId ?? ""}
              inputRef={actorIdRef}
              onCommit={(v) => setParam("actorId", v || undefined)}
            />
            <FilterTextInput
              id="audit-filter-event-type"
              label="Event type"
              placeholder="e.g. security.login.failure"
              initialValue={initialFilter.eventType ?? ""}
              inputRef={eventTypeRef}
              onCommit={(v) => setParam("eventType", v || undefined)}
            />
            <FilterTextInput
              id="audit-filter-entity-type"
              label="Entity type"
              placeholder="e.g. customer"
              initialValue={initialFilter.entityType ?? ""}
              inputRef={entityTypeRef}
              onCommit={(v) => setParam("entityType", v || undefined)}
            />
            <FilterTextInput
              id="audit-filter-entity-id"
              label="Entity ID"
              placeholder="UUID"
              initialValue={initialFilter.entityId ?? ""}
              inputRef={entityIdRef}
              onCommit={(v) => setParam("entityId", v || undefined)}
            />
          </div>

          <div className="mt-4">
            <span className="text-xs font-medium text-slate-700 dark:text-slate-300">Severity</span>
            <div className="mt-2 flex flex-wrap gap-2" role="group" aria-label="Severity filter">
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
                        : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400"
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
            {isPending && <span className="ml-2 text-xs text-slate-400">Loading…</span>}
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
              Page <span className="font-medium">{currentPage + 1}</span> of {totalPages}
            </>
          ) : (
            <>No events</>
          )}
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={currentPage <= 0}
            onClick={() => goToPage(currentPage - 1)}
            className={cn(
              "rounded-md border px-3 py-1.5",
              currentPage > 0
                ? "border-slate-200 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                : "cursor-not-allowed border-slate-200 bg-slate-50 text-slate-400 dark:border-slate-700 dark:bg-slate-800"
            )}
          >
            Previous
          </button>
          <button
            type="button"
            disabled={currentPage + 1 >= totalPages}
            onClick={() => goToPage(currentPage + 1)}
            className={cn(
              "rounded-md border px-3 py-1.5",
              currentPage + 1 < totalPages
                ? "border-slate-200 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
                : "cursor-not-allowed border-slate-200 bg-slate-50 text-slate-400 dark:border-slate-700 dark:bg-slate-800"
            )}
          >
            Next
          </button>
        </div>
      </nav>
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
            {expanded ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
          </button>
        </TableCell>
        <TableCell className="font-mono text-xs whitespace-nowrap text-slate-700 dark:text-slate-300">
          {formatComplianceDateWithTime(event.occurredAt)}
        </TableCell>
        <TableCell>
          <SeverityPill severity={event.severity} />
        </TableCell>
        <TableCell className="text-xs">
          <div className="font-medium text-slate-900 dark:text-slate-100">{event.label}</div>
          <div className="font-mono text-[10px] text-slate-500">{event.eventType}</div>
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
          <EntityCell entityType={event.entityType} entityId={event.entityId} slug={slug} />
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
