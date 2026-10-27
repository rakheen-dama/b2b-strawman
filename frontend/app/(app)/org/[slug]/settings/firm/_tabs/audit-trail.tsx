import { ApiError } from "@/lib/api/client";
import {
  getAuditMetadata,
  listAuditEvents,
  listFacetActors,
  listFacetEntityTypes,
  listFacetEventTypes,
  type ActorFacet,
  type AuditEventFilter,
  type AuditEventsPage,
  type AuditEventTypeMetadata,
  type AuditSeverity,
  type EntityTypeFacet,
  type EventTypeFacet,
} from "@/lib/api/audit-events";
import { AuditLogClient } from "./audit-log-client";
import { ExportDropdown } from "@/components/audit/export-dropdown";
import type { SettingsTabProps } from "@/components/settings/settings-family-page";

const PAGE_SIZE = 50;
const VALID_SEVERITIES: ReadonlySet<AuditSeverity> = new Set([
  "INFO",
  "NOTICE",
  "WARNING",
  "CRITICAL",
]);

/**
 * Facet lower bound when the list has no `from`: the list is then all-time, so the filter options
 * must be too (the facet endpoints otherwise default to the last 30 days).
 */
const FACETS_ALL_TIME_FROM = "1970-01-01T00:00:00Z";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * `actorId` / `entityId` are UUIDs on the backend (a non-UUID is a 400 that would fail the whole
 * settings page). A hand-edited or stale non-UUID value is dropped — treated as no filter.
 */
function uuidOrUndefined(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed && UUID_PATTERN.test(trimmed) ? trimmed : undefined;
}

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
    actorId: uuidOrUndefined(sp.actorId),
    eventType: sp.eventType || undefined,
    entityType: sp.entityType || undefined,
    entityId: uuidOrUndefined(sp.entityId),
  };
}

/**
 * Firm › Audit trail tab (Phase 87 S9, ADR-341) — the former settings/audit-log page's loader. The
 * filters ride on the family page's query string next to `tab=audit-trail` (repeated keys: first
 * value wins).
 */
export async function renderAuditTrailTab({ slug, searchParams }: SettingsTabProps) {
  const sp = Object.fromEntries(
    Object.entries(searchParams).map(([k, v]) => [k, Array.isArray(v) ? v[0] : v])
  ) as SearchParams;
  const filter = parseFilter(sp);

  // Epic 506B: fetch metadata server-side so client-side preset resolution can
  // map `group=COMPLIANCE` / `group=SECURITY` to event-type lists without an
  // extra round trip. Failures are non-fatal (preset list still renders;
  // group-based presets just resolve to no event types).
  // 625D: the Actor / Entity type / Event type filter options come from the audit facets over the
  // list's own range; a facet failure only empties that filter's options.
  const facetRange = { from: filter.from ?? FACETS_ALL_TIME_FROM, to: filter.to };
  let events: AuditEventsPage;
  let metadata: AuditEventTypeMetadata[] = [];
  let actorFacets: ActorFacet[] = [];
  let entityTypeFacets: EntityTypeFacet[] = [];
  let eventTypeFacets: EventTypeFacet[] = [];
  try {
    [events, metadata, actorFacets, entityTypeFacets, eventTypeFacets] = await Promise.all([
      listAuditEvents(filter),
      getAuditMetadata().catch(() => [] as AuditEventTypeMetadata[]),
      listFacetActors(facetRange).catch(() => [] as ActorFacet[]),
      listFacetEntityTypes(facetRange).catch(() => [] as EntityTypeFacet[]),
      listFacetEventTypes(facetRange).catch(() => [] as EventTypeFacet[]),
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
        actorFacets={actorFacets}
        entityTypeFacets={entityTypeFacets}
        eventTypeFacets={eventTypeFacets}
      />
    </div>
  );
}
