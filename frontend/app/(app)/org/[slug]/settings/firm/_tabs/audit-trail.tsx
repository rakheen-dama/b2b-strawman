import { ApiError } from "@/lib/api/client";
import {
  getAuditMetadata,
  listAuditEvents,
  type AuditEventFilter,
  type AuditEventsPage,
  type AuditEventTypeMetadata,
  type AuditSeverity,
} from "@/lib/api/audit-events";
import { AuditLogClient } from "./audit-log-client";
import { ExportDropdown } from "@/components/audit/export-dropdown";
import type { FirmTabProps } from "./types";

const PAGE_SIZE = 50;
const VALID_SEVERITIES: ReadonlySet<AuditSeverity> = new Set([
  "INFO",
  "NOTICE",
  "WARNING",
  "CRITICAL",
]);

type SearchParams = {
  page?: string;
  from?: string;
  to?: string;
  severities?: string;
  actorId?: string;
  eventType?: string;
  entityType?: string;
  entityId?: string;
};

function parseFilter(sp: SearchParams): AuditEventFilter {
  const requestedPage = Math.max(0, parseInt(sp.page ?? "0", 10) || 0);
  const severities = sp.severities
    ? (sp.severities
        .split(",")
        .map((s) => s.trim().toUpperCase())
        .filter((s): s is AuditSeverity =>
          VALID_SEVERITIES.has(s as AuditSeverity)
        ) as AuditSeverity[])
    : undefined;
  return {
    page: requestedPage,
    size: PAGE_SIZE,
    from: sp.from || undefined,
    to: sp.to || undefined,
    severities: severities && severities.length > 0 ? severities : undefined,
    actorId: sp.actorId || undefined,
    eventType: sp.eventType || undefined,
    entityType: sp.entityType || undefined,
    entityId: sp.entityId || undefined,
  };
}

/**
 * Firm › Audit trail tab (Phase 87 S9, ADR-341) — the former settings/audit-log page's loader. The
 * filters ride on the family page's query string next to `tab=audit-trail` (repeated keys: first
 * value wins).
 */
export async function renderAuditTrailTab({ slug, searchParams }: FirmTabProps) {
  const sp = Object.fromEntries(
    Object.entries(searchParams).map(([k, v]) => [k, Array.isArray(v) ? v[0] : v])
  ) as SearchParams;
  const filter = parseFilter(sp);

  // Epic 506B: fetch metadata server-side so client-side preset resolution can
  // map `group=COMPLIANCE` / `group=SECURITY` to event-type lists without an
  // extra round trip. Failures are non-fatal (preset list still renders;
  // group-based presets just resolve to no event types).
  let events: AuditEventsPage;
  let metadata: AuditEventTypeMetadata[] = [];
  try {
    [events, metadata] = await Promise.all([
      listAuditEvents(filter),
      getAuditMetadata().catch(() => [] as AuditEventTypeMetadata[]),
    ]);
  } catch (error) {
    if (error instanceof ApiError && error.status === 403) {
      return (
        <div className="space-y-8">
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

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">Audit log</h1>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            Read-only feed of audit events. Filter by date range, severity, actor, event type, or
            entity.
          </p>
        </div>
        <ExportDropdown filter={filter} />
      </div>

      <AuditLogClient
        slug={slug}
        initialEvents={events}
        initialFilter={filter}
        metadata={metadata}
      />
    </div>
  );
}
