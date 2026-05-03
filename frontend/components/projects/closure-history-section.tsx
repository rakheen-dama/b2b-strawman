"use client";

import { useState } from "react";
import useSWR from "swr";
import { ChevronDown, ChevronRight } from "lucide-react";

import { fetchClosureLog } from "@/lib/actions/matter-closure";
import type { ClosureLogEntry } from "@/lib/api/matter-closure";
import { AuditTimelineTab } from "@/components/audit/audit-timeline-tab";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { formatDate } from "@/lib/format";

export interface ClosureHistorySectionProps {
  projectId: string;
}

const REASON_LABELS: Record<string, string> = {
  CONCLUDED: "Concluded",
  CLIENT_TERMINATED: "Client terminated",
  REFERRED_OUT: "Referred out",
  OTHER: "Other",
};

function reasonLabel(reason: string): string {
  return REASON_LABELS[reason] ?? reason;
}

/**
 * Closure-history feed for a CLOSED matter. Renders one row per
 * `ClosureLogEntry` with metadata (closedAt, reason, actor) and a
 * lazy-mounted per-row audit timeline (entityType="matter_closure",
 * entityId=closureLogId) that surfaces the `matter.closure.override_used`
 * event emitted by 508A when override was used.
 *
 * Rendered only on CLOSED matters by `<ProjectDetailPage>`.
 */
export function ClosureHistorySection({ projectId }: ClosureHistorySectionProps) {
  const { data, error, isLoading } = useSWR<ClosureLogEntry[]>(
    `closure-log-${projectId}`,
    () => fetchClosureLog(projectId)
  );

  return (
    <Card data-testid="matter-closure-section" className="shadow-sm">
      <CardHeader>
        <CardTitle className="font-display text-slate-900 dark:text-slate-100">
          Closure history
        </CardTitle>
      </CardHeader>
      <CardContent>
        {isLoading && (
          <p
            className="text-sm text-slate-500 dark:text-slate-400"
            data-testid="closure-history-loading"
          >
            Loading closure history…
          </p>
        )}
        {error && !isLoading && (
          <p
            className="text-sm text-red-600 dark:text-red-400"
            data-testid="closure-history-error"
          >
            Could not load closure history.
          </p>
        )}
        {!isLoading && !error && (!data || data.length === 0) && (
          <p
            className="text-sm text-slate-500 dark:text-slate-400"
            data-testid="closure-history-empty"
          >
            No closure history recorded for this matter.
          </p>
        )}
        {!isLoading && !error && data && data.length > 0 && (
          <ul className="divide-y divide-slate-200 dark:divide-slate-800">
            {data.map((entry) => (
              <ClosureHistoryRow key={entry.id} entry={entry} />
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function ClosureHistoryRow({ entry }: { entry: ClosureLogEntry }) {
  const [open, setOpen] = useState(false);
  const [hasOpened, setHasOpened] = useState(false);

  return (
    <li className="py-4" data-testid={`closure-row-${entry.id}`}>
      <Collapsible
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (next) setHasOpened(true);
        }}
      >
        <div className="flex flex-wrap items-center gap-3">
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                {formatDate(entry.closedAt)}
              </span>
              <Badge variant="neutral" data-testid={`closure-row-reason-${entry.id}`}>
                {reasonLabel(entry.reason)}
              </Badge>
              {entry.overrideUsed && (
                <Badge variant="warning" data-testid={`closure-row-override-${entry.id}`}>
                  Override used
                </Badge>
              )}
              {entry.reopenedAt && (
                <Badge variant="outline">Reopened {formatDate(entry.reopenedAt)}</Badge>
              )}
            </div>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Closed by {entry.closedBy}
            </p>
            {entry.overrideUsed && entry.overrideJustification && (
              <p
                className="mt-2 text-sm text-slate-700 dark:text-slate-300"
                data-testid={`closure-row-justification-${entry.id}`}
              >
                <span className="font-medium">Justification: </span>
                {entry.overrideJustification}
              </p>
            )}
          </div>
          <CollapsibleTrigger
            data-testid={`closure-audit-toggle-${entry.id}`}
            className="inline-flex shrink-0 items-center gap-1 rounded-md border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 transition-colors hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:bg-slate-800"
          >
            {open ? (
              <ChevronDown className="size-3.5" aria-hidden="true" />
            ) : (
              <ChevronRight className="size-3.5" aria-hidden="true" />
            )}
            {open ? "Hide audit" : "View audit"}
          </CollapsibleTrigger>
        </div>
        <CollapsibleContent>
          <div className="mt-3 rounded-md border border-slate-200 bg-slate-50/50 p-3 dark:border-slate-800 dark:bg-slate-900/40">
            {hasOpened ? (
              entry.overrideUsed ? (
                <AuditTimelineTab entityType="matter_closure" entityId={entry.id} />
              ) : (
                <p
                  className="text-sm text-slate-500 dark:text-slate-400"
                  data-testid={`closure-row-no-override-${entry.id}`}
                >
                  No override events for this closure.
                </p>
              )
            ) : null}
          </div>
        </CollapsibleContent>
      </Collapsible>
    </li>
  );
}
