"use client";

import { useState } from "react";
import { ChevronDown, ChevronRight } from "lucide-react";

import {
  AuditTimelineTab,
  useAuditTabVisible,
} from "@/components/audit/audit-timeline-tab";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

export interface AuditHistoryDisclosureProps {
  entityType: string;
  entityId: string;
  /** Visible label for the disclosure trigger. Defaults to "Audit history". */
  title?: string;
  /** Optional className applied to the outer Card. */
  className?: string;
}

/**
 * Capability-gated, lazy-mounted disclosure wrapping `<AuditTimelineTab>`.
 *
 * - Renders `null` when the viewer lacks `TEAM_OVERSIGHT` (defense-in-depth
 *   on top of `<AuditTimelineTab>`'s own gating).
 * - The wrapped `<AuditTimelineTab>` is mounted ONLY after the trigger has
 *   been expanded at least once, so the audit-events fetch never fires for
 *   viewers who do not open the disclosure.
 *
 * Used by:
 *   - `<ClosureHistorySection>` (per-row audit timeline for matter_closure
 *     entries with `overrideUsed=true`).
 *   - Proposal detail page (inline disclosure for proposal-level audit).
 */
export function AuditHistoryDisclosure({
  entityType,
  entityId,
  title = "Audit history",
  className,
}: AuditHistoryDisclosureProps) {
  const visible = useAuditTabVisible();
  const [open, setOpen] = useState(false);
  const [hasOpened, setHasOpened] = useState(false);

  if (!visible) {
    return null;
  }

  return (
    <Card
      data-testid="audit-history-disclosure"
      data-entity-type={entityType}
      data-entity-id={entityId}
      className={cn("shadow-sm", className)}
    >
      <Collapsible
        open={open}
        onOpenChange={(next) => {
          setOpen(next);
          if (next) setHasOpened(true);
        }}
      >
        <CardHeader>
          <CollapsibleTrigger
            data-testid="audit-history-disclosure-trigger"
            className="flex w-full items-center justify-between gap-2 text-left text-slate-700 transition-colors hover:text-slate-900 dark:text-slate-200 dark:hover:text-slate-50"
          >
            <CardTitle className="font-display text-slate-900 dark:text-slate-100">
              {title}
            </CardTitle>
            {open ? (
              <ChevronDown className="size-4 text-slate-500" aria-hidden="true" />
            ) : (
              <ChevronRight className="size-4 text-slate-500" aria-hidden="true" />
            )}
          </CollapsibleTrigger>
        </CardHeader>
        <CollapsibleContent>
          <CardContent>
            {hasOpened ? (
              <AuditTimelineTab entityType={entityType} entityId={entityId} />
            ) : null}
          </CardContent>
        </CollapsibleContent>
      </Collapsible>
    </Card>
  );
}
