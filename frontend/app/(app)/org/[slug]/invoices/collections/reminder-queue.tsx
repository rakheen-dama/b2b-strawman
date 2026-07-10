"use client";

import { useMemo, useState, useTransition } from "react";
import DOMPurify from "dompurify";
import { ChevronDown, ChevronRight, Inbox } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { ExecutionGateCard } from "@/components/ai/execution-gate-card";
import type { AiGateListItem } from "@/lib/api/ai";
import { batchApproveGatesAction, rejectReminderGateAction } from "./actions";

/**
 * Serializable preview of a gate's `proposedAction`, resolved server-side by the RSC
 * page (the gate LIST DTO carries no `proposedAction`; only the DETAIL DTO does). The
 * approver reviews exactly the drafted subject/body that will be sent — `bodyHtml` is
 * sanitized server-side at gate creation (ReminderHtmlSanitizer).
 */
export interface ReminderPreview {
  subject: string | null;
  bodyHtml: string | null;
  bodyText: string | null;
  stage: string | null;
  invoiceId: string | null;
  customerId: string | null;
}

interface ReminderQueueProps {
  slug: string;
  gates: AiGateListItem[];
  /** gateId -> drafted-content preview resolved from the gate detail's proposedAction. */
  previews: Record<string, ReminderPreview>;
}

interface Disposition {
  outcome: string; // "APPROVED_EXECUTED" | "FAILED"
  error: string | null;
}

function formatStage(stage: string | null): string | null {
  if (!stage) return null;
  return stage
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export function ReminderQueue({ slug, gates, previews }: ReminderQueueProps) {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [dispositions, setDispositions] = useState<Record<string, Disposition>>({});
  const [batchError, setBatchError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  const selectedCount = selectedIds.size;

  const gatesById = useMemo(() => {
    const map: Record<string, AiGateListItem> = {};
    for (const gate of gates) map[gate.id] = gate;
    return map;
  }, [gates]);

  function toggleSelect(gateId: string, checked: boolean) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) next.add(gateId);
      else next.delete(gateId);
      return next;
    });
  }

  function toggleExpand(gateId: string) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(gateId)) next.delete(gateId);
      else next.add(gateId);
      return next;
    });
  }

  function handleBatchApprove() {
    if (selectedCount === 0) return;
    const ids = Array.from(selectedIds);
    startTransition(async () => {
      setBatchError(null);
      const result = await batchApproveGatesAction(slug, ids);
      if (!result.success) {
        setBatchError(result.error || "Failed to approve the selected reminders.");
        return;
      }
      setDispositions((prev) => {
        const next = { ...prev };
        for (const d of result.results ?? []) {
          next[d.gateId] = { outcome: d.outcome, error: d.error };
        }
        return next;
      });
      setSelectedIds(new Set());
    });
  }

  // Single-approve wrapper for a queue card — reuses the batch endpoint with one id
  // and translates the disposition into the {success, error} shape ExecutionGateCard expects.
  async function handleSingleApprove(gateId: string, notes?: string) {
    const result = await batchApproveGatesAction(slug, [gateId], notes);
    if (!result.success) {
      return { success: false, error: result.error };
    }
    const disposition = result.results?.find((d) => d.gateId === gateId);
    if (disposition) {
      setDispositions((prev) => ({
        ...prev,
        [gateId]: { outcome: disposition.outcome, error: disposition.error },
      }));
    }
    if (disposition && disposition.outcome !== "APPROVED_EXECUTED") {
      return { success: false, error: disposition.error || "The reminder could not be sent." };
    }
    return { success: true };
  }

  async function handleReject(gateId: string, notes?: string) {
    return rejectReminderGateAction(slug, gateId, notes);
  }

  if (gates.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-slate-200 px-6 py-12 text-center dark:border-slate-800">
        <Inbox className="mb-3 size-10 text-slate-300 dark:text-slate-600" />
        <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">
          No reminders awaiting approval
        </h3>
        <p className="mt-1 max-w-sm text-sm text-slate-500 dark:text-slate-400">
          Drafted collection reminders will appear here for review before they are sent.
        </p>
      </div>
    );
  }

  const dispositionEntries = Object.entries(dispositions);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm text-slate-600 dark:text-slate-400">
          {gates.length} reminder{gates.length === 1 ? "" : "s"} awaiting approval
        </p>
        <Button
          type="button"
          variant="accent"
          size="sm"
          disabled={selectedCount === 0 || isPending}
          onClick={handleBatchApprove}
        >
          Approve selected ({selectedCount})
        </Button>
      </div>

      {batchError && <p className="text-sm text-red-600 dark:text-red-400">{batchError}</p>}

      {dispositionEntries.length > 0 && (
        <div className="space-y-2 rounded-lg border border-slate-200 p-3 dark:border-slate-800">
          <p className="text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
            Last batch result
          </p>
          <ul className="space-y-1.5">
            {dispositionEntries.map(([gateId, disposition]) => {
              const preview = previews[gateId];
              const approved = disposition.outcome === "APPROVED_EXECUTED";
              const label = preview?.subject || `Reminder ${gateId.slice(0, 8)}`;
              return (
                <li key={gateId} className="flex items-start gap-2 text-sm">
                  <Badge variant={approved ? "success" : "destructive"}>
                    {approved ? "Approved" : "Failed"}
                  </Badge>
                  <span className="text-slate-700 dark:text-slate-300">
                    {label}
                    {!approved && disposition.error ? ` — ${disposition.error}` : ""}
                  </span>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      <ul className="space-y-3">
        {gates.map((gate) => {
          const preview = previews[gate.id];
          const expanded = expandedIds.has(gate.id);
          const selected = selectedIds.has(gate.id);
          const disposition = dispositions[gate.id];
          const stageLabel = formatStage(preview?.stage ?? null);
          const subject = preview?.subject || "Collection reminder";
          return (
            <li key={gate.id} className="rounded-lg border border-slate-200 dark:border-slate-800">
              <div className="flex items-center gap-3 p-3">
                <Checkbox
                  checked={selected}
                  onCheckedChange={(value) => toggleSelect(gate.id, value === true)}
                  disabled={isPending}
                  aria-label={`Select reminder: ${subject}`}
                />
                <button
                  type="button"
                  onClick={() => toggleExpand(gate.id)}
                  className="flex flex-1 items-center gap-2 text-left"
                  aria-expanded={expanded}
                >
                  {expanded ? (
                    <ChevronDown className="size-4 shrink-0 text-slate-400" />
                  ) : (
                    <ChevronRight className="size-4 shrink-0 text-slate-400" />
                  )}
                  <span className="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
                    {subject}
                  </span>
                  {stageLabel && <Badge variant="neutral">{stageLabel}</Badge>}
                </button>
                {disposition && (
                  <Badge
                    variant={
                      disposition.outcome === "APPROVED_EXECUTED" ? "success" : "destructive"
                    }
                  >
                    {disposition.outcome === "APPROVED_EXECUTED" ? "Approved" : "Failed"}
                  </Badge>
                )}
              </div>

              {expanded && (
                <div className="space-y-3 border-t border-slate-200 p-3 dark:border-slate-800">
                  {/* Drafted content preview — the approver reviews exactly what will be
                      sent. bodyHtml is server-sanitized (ReminderHtmlSanitizer); fall back
                      to plain text when it is absent. */}
                  <div className="rounded-md bg-slate-50 p-3 dark:bg-slate-900/50">
                    <p className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                      Subject
                    </p>
                    <p className="mt-0.5 text-sm text-slate-900 dark:text-slate-100">
                      {preview?.subject || "—"}
                    </p>
                    <p className="mt-3 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                      Message
                    </p>
                    {preview?.bodyHtml ? (
                      <div
                        className="mt-1 space-y-2 text-sm text-slate-700 dark:text-slate-300 [&_a]:text-teal-600 [&_p]:my-1"
                        // eslint-disable-next-line react/no-danger -- bodyHtml is defense-in-depth sanitized: server-side at gate creation (ReminderHtmlSanitizer) and again client-side here (DOMPurify), because the content is AI-drafted from customer/invoice data (prompt-injection surface).
                        dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(preview.bodyHtml) }}
                      />
                    ) : (
                      <p className="mt-1 text-sm whitespace-pre-wrap text-slate-700 dark:text-slate-300">
                        {preview?.bodyText || "No drafted message available."}
                      </p>
                    )}
                  </div>

                  <ExecutionGateCard
                    gate={gatesById[gate.id]}
                    slug={slug}
                    onApprove={handleSingleApprove}
                    onReject={handleReject}
                  />
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
