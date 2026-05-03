"use client";

import { useRouter } from "next/navigation";
import Link from "next/link";
import { ShieldAlert } from "lucide-react";

import { cn } from "@/lib/utils";
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { RelativeDate } from "@/components/ui/relative-date";
import { SeverityPill } from "@/components/audit/severity-pill";
import { CAPABILITIES, RequiresCapability } from "@/lib/capabilities";
import type { AuditEventResponse, AuditSeverity, EventTypeFacet } from "@/lib/api/audit-events";

// Severities surfaced as count pills. INFO is intentionally excluded
// (architecture §12.7.1).
const PILL_SEVERITIES: ReadonlyArray<Exclude<AuditSeverity, "INFO">> = [
  "NOTICE",
  "WARNING",
  "CRITICAL",
];

const ROW_WINDOW_MS = 60_000; // ±1 minute around the event timestamp.
const SENSITIVE_PRESET_LOOKBACK_DAYS = 30;

export interface SensitiveEventsWidgetProps {
  orgSlug: string;
  /** Facets over the last 7 days (one row per event type). */
  facets: EventTypeFacet[];
  /** Top-5 most recent CRITICAL/WARNING events in the last 7 days. */
  recent: AuditEventResponse[] | null;
}

function aggregateBySeverity(facets: EventTypeFacet[]): Record<AuditSeverity, number> {
  const acc: Record<AuditSeverity, number> = {
    INFO: 0,
    NOTICE: 0,
    WARNING: 0,
    CRITICAL: 0,
  };
  for (const facet of facets) {
    acc[facet.severity] = (acc[facet.severity] ?? 0) + facet.count;
  }
  return acc;
}

function isoDaysAgo(days: number): string {
  return new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();
}

function buildRowHref(orgSlug: string, event: AuditEventResponse): string {
  const occurred = new Date(event.occurredAt).getTime();
  const from = new Date(occurred - ROW_WINDOW_MS).toISOString();
  const to = new Date(occurred + ROW_WINDOW_MS).toISOString();
  const sp = new URLSearchParams({
    eventType: event.eventType,
    from,
    to,
  });
  return `/org/${orgSlug}/settings/audit-log?${sp.toString()}`;
}

function buildViewAllHref(orgSlug: string): string {
  // Mirrors the `sensitive` preset resolved by components/audit/presets.ts:
  // last 30 days, severities WARNING+CRITICAL.
  const sp = new URLSearchParams({
    severities: "WARNING,CRITICAL",
    from: isoDaysAgo(SENSITIVE_PRESET_LOOKBACK_DAYS),
    preset: "sensitive",
  });
  return `/org/${orgSlug}/settings/audit-log?${sp.toString()}`;
}

function SensitiveEventsWidgetInner({ orgSlug, facets, recent }: SensitiveEventsWidgetProps) {
  const router = useRouter();
  const counts = aggregateBySeverity(facets);
  const events = recent ?? [];
  const viewAllHref = buildViewAllHref(orgSlug);

  return (
    <Card data-testid="sensitive-events-widget">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <ShieldAlert className="size-4" />
          Sensitive events
          <span className="text-xs font-normal text-slate-500">Last 7 days</span>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 pt-0">
        <div className="flex flex-wrap items-center gap-2" data-testid="sensitive-events-counts">
          {PILL_SEVERITIES.map((sev) => (
            <div
              key={sev}
              className="flex items-center gap-1.5"
              data-testid={`sensitive-count-${sev}`}
            >
              <SeverityPill severity={sev} size="sm" />
              <span className="font-mono text-xs font-semibold tabular-nums">{counts[sev]}</span>
            </div>
          ))}
        </div>

        {events.length === 0 ? (
          <p className="text-xs text-slate-500 italic" data-testid="sensitive-events-empty">
            No sensitive events in the last 7 days.
          </p>
        ) : (
          <ul className="space-y-0">
            {events.map((event, idx) => {
              const href = buildRowHref(orgSlug, event);
              return (
                <li key={event.id}>
                  <button
                    type="button"
                    data-testid={`sensitive-row-${event.id}`}
                    onClick={() => router.push(href)}
                    className={cn(
                      "flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left transition-colors hover:bg-slate-50 dark:hover:bg-slate-900",
                      idx % 2 === 1 && "bg-slate-50/50 dark:bg-slate-900/50"
                    )}
                  >
                    <SeverityPill severity={event.severity} size="sm" />
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-xs text-slate-700 dark:text-slate-300">
                        {event.label}
                      </p>
                      <p className="truncate text-[10px] text-slate-500 dark:text-slate-400">
                        {event.entityType}
                        {event.entityId ? ` · ${event.entityId}` : ""}
                      </p>
                    </div>
                    <span className="shrink-0 text-[10px] text-slate-400 dark:text-slate-500">
                      <RelativeDate iso={event.occurredAt} />
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>
      <CardFooter className="pt-0">
        <Button variant="ghost" size="sm" className="h-7 text-xs text-slate-500" asChild>
          <Link href={viewAllHref} data-testid="sensitive-events-view-all">
            View all &rarr;
          </Link>
        </Button>
      </CardFooter>
    </Card>
  );
}

/**
 * Capability-gated dashboard widget surfacing recent legally-sensitive audit
 * events. Members without the `TEAM_OVERSIGHT` capability see nothing.
 */
export function SensitiveEventsWidget(props: SensitiveEventsWidgetProps) {
  return (
    <RequiresCapability cap={CAPABILITIES.TEAM_OVERSIGHT} fallback={null}>
      <SensitiveEventsWidgetInner {...props} />
    </RequiresCapability>
  );
}
