import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { ApiError } from "@/lib/api/client";
import {
  listAuditEvents,
  getAuditMetadata,
  type AuditEventFilter,
  type AuditSeverity,
  type AuditEventTypeMetadata,
  type AuditEventsPage,
} from "@/lib/api/audit-events";
import { AuditLogClient } from "./audit-log-client";

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
          VALID_SEVERITIES.has(s as AuditSeverity),
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

export default async function AuditLogPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<SearchParams>;
}) {
  const { slug } = await params;
  const sp = await searchParams;
  const filter = parseFilter(sp);

  const backLink = (
    <Link
      href={`/org/${slug}/settings`}
      className="inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
    >
      <ChevronLeft className="size-4" />
      Settings
    </Link>
  );

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
          {backLink}
          <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
            Audit log
          </h1>
          <p className="text-slate-600 dark:text-slate-400">
            Not authorised. The audit log is only visible to members with the{" "}
            <code className="font-mono text-xs">TEAM_OVERSIGHT</code>{" "}
            capability.
          </p>
        </div>
      );
    }
    throw error;
  }

  return (
    <div className="space-y-8">
      {backLink}

      <div>
        <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
          Audit log
        </h1>
        <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
          Read-only feed of audit events. Filter by date range, severity, actor,
          event type, or entity.
        </p>
      </div>

      <AuditLogClient
        slug={slug}
        initialEvents={events}
        metadata={metadata}
        initialFilter={filter}
        pageSize={PAGE_SIZE}
      />
    </div>
  );
}
