"use client";

import { useRouter } from "next/navigation";
import Link from "next/link";
import type { ReactNode } from "react";
import { AlertTriangle, Circle, Info, OctagonAlert, ShieldAlert, ShieldCheck } from "lucide-react";

import { cn } from "@/lib/utils";
import { Button } from "@b2mash/ui/button";
import { EmptyState } from "@/components/empty-state";
import { RelativeDate } from "@/components/ui/relative-date";
import {
  StatusBadge,
  type StatusTone,
  WIDGET_ROW_CLASS,
  WidgetCard,
} from "@/components/dashboard/widget-parts";
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

// Widget-local severity badge (640B): status tokens + icon + the same uppercase label. Keeps the
// audit log's SeverityPill testid/attribute contract; severity-pill.tsx itself is not touched
// (the audit log and its e2e selector still use it).
const SEVERITY: Record<AuditSeverity, { tone: StatusTone; icon: ReactNode }> = {
  INFO: { tone: "neutral", icon: <Circle /> },
  NOTICE: { tone: "info", icon: <Info /> },
  WARNING: { tone: "warning", icon: <AlertTriangle /> },
  CRITICAL: { tone: "danger", icon: <OctagonAlert /> },
};

function SeverityBadge({ severity }: { severity: AuditSeverity }) {
  const { tone, icon } = SEVERITY[severity] ?? SEVERITY.INFO;
  return (
    <StatusBadge
      tone={tone}
      icon={icon}
      data-testid="severity-pill"
      data-severity={severity}
      className="px-1.5 text-[10px]"
    >
      {severity}
    </StatusBadge>
  );
}

export interface SensitiveEventsWidgetProps {
  orgSlug: string;
  /** Facets over the last 7 days (one row per event type). */
  facets: EventTypeFacet[];
  /** Top-5 most recent CRITICAL/WARNING events in the last 7 days. */
  recent: AuditEventResponse[] | null;
  /**
   * ISO timestamp for the View-all link's `from` query param. Computed once in
   * the parent server component so it does not change between SSR and CSR
   * (which would cause a hydration mismatch on the `<Link href=...>`).
   * Defaults to "30 days ago" if omitted (kept for legacy callers/tests).
   */
  viewAllFromIso?: string;
}

function aggregateBySeverity(facets: EventTypeFacet[]): Record<AuditSeverity, number> {
  const acc: Record<AuditSeverity, number> = {
    INFO: 0,
    NOTICE: 0,
    WARNING: 0,
    CRITICAL: 0,
  };
  for (const facet of facets) {
    // Defensive: ignore unknown severity values (e.g. backend rolls a new
    // severity before the frontend types are regenerated). Without this guard
    // a stray severity would create a new key on `acc` and the widget would
    // render an extra pill or NaN on subsequent reads.
    if (!(facet.severity in acc)) continue;
    acc[facet.severity] = (acc[facet.severity] ?? 0) + facet.count;
  }
  return acc;
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

function buildViewAllHref(orgSlug: string, fromIso: string | undefined): string {
  // Mirrors the `sensitive` preset resolved by components/audit/presets.ts:
  // severities WARNING+CRITICAL. The `from` timestamp is supplied by the
  // parent server component (see `viewAllFromIso` prop) so SSR and CSR agree;
  // when omitted (legacy callers / tests), fall back to the preset and let
  // the audit-log page resolve the date range.
  const params: Record<string, string> = {
    severities: "WARNING,CRITICAL",
    preset: "sensitive",
  };
  if (fromIso) params.from = fromIso;
  const sp = new URLSearchParams(params);
  return `/org/${orgSlug}/settings/audit-log?${sp.toString()}`;
}

function SensitiveEventsWidgetInner({
  orgSlug,
  facets,
  recent,
  viewAllFromIso,
}: SensitiveEventsWidgetProps) {
  const router = useRouter();
  const counts = aggregateBySeverity(facets);
  const events = recent ?? [];
  const viewAllHref = buildViewAllHref(orgSlug, viewAllFromIso);

  return (
    <WidgetCard
      data-testid="sensitive-events-widget"
      title={
        <>
          <ShieldAlert className="size-4" />
          Sensitive events
          <span className="text-muted-foreground text-xs font-normal">Last 7 days</span>
        </>
      }
      contentProps={{ className: "space-y-3" }}
      footer={
        <Button variant="ghost" size="sm" className="text-muted-foreground h-7 text-xs" asChild>
          <Link href={viewAllHref} data-testid="sensitive-events-view-all">
            View all &rarr;
          </Link>
        </Button>
      }
    >
      <div className="flex flex-wrap items-center gap-2" data-testid="sensitive-events-counts">
        {PILL_SEVERITIES.map((sev) => (
          <div
            key={sev}
            className="flex items-center gap-1.5"
            data-testid={`sensitive-count-${sev}`}
          >
            <SeverityBadge severity={sev} />
            <span className="text-foreground font-mono text-xs font-semibold tabular-nums">
              {counts[sev]}
            </span>
          </div>
        ))}
      </div>

      {events.length === 0 ? (
        <div data-testid="sensitive-events-empty">
          <EmptyState
            variant="compact"
            icon={ShieldCheck}
            title="No sensitive events in the last 7 days."
            description={null}
          />
        </div>
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
                    WIDGET_ROW_CLASS,
                    "w-full text-left transition-colors",
                    idx % 2 === 1 && "bg-muted/50"
                  )}
                >
                  <SeverityBadge severity={event.severity} />
                  <div className="min-w-0 flex-1">
                    <p className="text-foreground truncate text-xs">{event.label}</p>
                    <p className="text-muted-foreground truncate text-[10px]">
                      {event.entityType}
                      {event.entityId ? ` · ${event.entityId}` : ""}
                    </p>
                  </div>
                  <span className="text-muted-foreground shrink-0 text-[10px]">
                    <RelativeDate iso={event.occurredAt} />
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </WidgetCard>
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
