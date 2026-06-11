"use client";

import { useState, useCallback } from "react";
import { Check, X, Pencil } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { cn } from "@/lib/utils";
import { SPECIALIST_STRINGS } from "@/components/assistant/specialist-strings";
import { approveInvocation, rejectInvocation } from "@/lib/api/assistant-specialists";

// ---- Types ----

export interface PolishEdit {
  timeEntryId: string;
  beforeText: string;
  afterText: string;
}

export interface LineGroup {
  description: string;
  hours: number;
  sourceTimeEntryIds: string[];
}

export interface BillingDiffProps {
  invocationId: string;
  kind: "BillingPolishPayload" | "BillingGroupingPayload";
  invoiceId: string;
  /** For polish mode */
  edits?: PolishEdit[];
  /** For grouping mode */
  groups?: LineGroup[];
  onApproved?: () => void;
  onRejected?: () => void;
}

type RowDecision = "accept" | "reject" | "edit";

interface RowState {
  decision: RowDecision;
  editedText: string;
}

// ---- Component ----

export function BillingDiff({
  invocationId,
  kind,
  invoiceId,
  edits = [],
  groups = [],
  onApproved,
  onRejected,
}: BillingDiffProps) {
  const isPolish = kind === "BillingPolishPayload";

  // Per-row state for polish mode
  const [rowStates, setRowStates] = useState<Record<string, RowState>>(() => {
    const initial: Record<string, RowState> = {};
    for (const edit of edits) {
      initial[edit.timeEntryId] = { decision: "accept", editedText: edit.afterText };
    }
    return initial;
  });

  const [inFlight, setInFlight] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [showRejectForm, setShowRejectForm] = useState(false);

  const setRowDecision = useCallback((timeEntryId: string, decision: RowDecision) => {
    setRowStates((prev) => ({
      ...prev,
      [timeEntryId]: {
        ...prev[timeEntryId],
        decision,
      },
    }));
  }, []);

  const setRowEditText = useCallback((timeEntryId: string, text: string) => {
    setRowStates((prev) => ({
      ...prev,
      [timeEntryId]: {
        ...prev[timeEntryId],
        editedText: text,
      },
    }));
  }, []);

  const handleApproveAll = useCallback(async () => {
    if (inFlight) return;
    setInFlight(true);
    setError(null);
    try {
      if (isPolish) {
        // Build appliedOutput with only accepted/edited rows
        const acceptedEdits = edits
          .filter((e) => rowStates[e.timeEntryId]?.decision !== "reject")
          .map((e) => ({
            timeEntryId: e.timeEntryId,
            beforeText: e.beforeText,
            afterText: rowStates[e.timeEntryId]?.editedText ?? e.afterText,
          }));

        const appliedOutput = {
          kind: "BillingPolishPayload" as const,
          invoiceId,
          edits: acceptedEdits,
        };
        await approveInvocation(invocationId, appliedOutput);
      } else {
        // Grouping — approve as-is
        const appliedOutput = {
          kind: "BillingGroupingPayload" as const,
          invoiceId,
          groups,
        };
        await approveInvocation(invocationId, appliedOutput);
      }
      onApproved?.();
    } catch (err) {
      console.error("[BillingDiff] approve failed", err);
      setError("Failed to approve changes. Please try again.");
    } finally {
      setInFlight(false);
    }
  }, [inFlight, isPolish, edits, rowStates, invoiceId, invocationId, groups, onApproved]);

  const handleReject = useCallback(async () => {
    if (inFlight || !rejectReason.trim()) return;
    setInFlight(true);
    setError(null);
    try {
      await rejectInvocation(invocationId, rejectReason.trim());
      onRejected?.();
    } catch (err) {
      console.error("[BillingDiff] reject failed", err);
      setError("Failed to reject changes. Please try again.");
    } finally {
      setInFlight(false);
    }
  }, [inFlight, invocationId, rejectReason, onRejected]);

  return (
    <div
      data-testid="billing-diff"
      className="space-y-4 rounded-lg border border-slate-200 p-4 dark:border-slate-800"
    >
      <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
        {isPolish ? SPECIALIST_STRINGS.billingDiffTitle : SPECIALIST_STRINGS.billingGroupingTitle}
      </h3>

      {/* Polish mode — per-row before/after diff */}
      {isPolish && edits.length > 0 && (
        <div className="space-y-1">
          {/* Header */}
          <div className="grid grid-cols-[1fr_1fr_auto] gap-3 pb-1 text-xs font-medium text-slate-500 dark:text-slate-400">
            <span>Before</span>
            <span>After</span>
            <span className="w-24 text-center">Actions</span>
          </div>

          {edits.map((edit) => {
            const state = rowStates[edit.timeEntryId];
            const isEditing = state?.decision === "edit";
            const isRejected = state?.decision === "reject";

            return (
              <div
                key={edit.timeEntryId}
                data-testid="diff-row"
                data-time-entry-id={edit.timeEntryId}
                className={cn(
                  "grid grid-cols-[1fr_1fr_auto] gap-3 rounded border border-slate-100 p-2 text-xs dark:border-slate-800",
                  isRejected && "opacity-50"
                )}
              >
                {/* Before text */}
                <span className="rounded bg-red-50 px-2 py-1.5 font-mono text-[11px] text-red-800 line-through dark:bg-red-950/40 dark:text-red-300">
                  {edit.beforeText}
                </span>

                {/* After text — inline edit when in edit mode */}
                {isEditing ? (
                  <textarea
                    data-testid="edit-textarea"
                    className="rounded border border-teal-300 bg-white px-2 py-1.5 font-mono text-[11px] text-slate-900 focus:ring-1 focus:ring-teal-500 focus:outline-none dark:border-teal-700 dark:bg-slate-900 dark:text-slate-100"
                    value={state.editedText}
                    onChange={(e) => setRowEditText(edit.timeEntryId, e.target.value)}
                    rows={2}
                  />
                ) : (
                  <span
                    className={cn(
                      "rounded px-2 py-1.5 font-mono text-[11px]",
                      isRejected
                        ? "bg-slate-50 text-slate-500 dark:bg-slate-900 dark:text-slate-500"
                        : "bg-green-50 text-green-800 dark:bg-green-950/40 dark:text-green-300"
                    )}
                  >
                    {state?.editedText ?? edit.afterText}
                  </span>
                )}

                {/* Row controls */}
                <div className="flex w-24 items-center justify-center gap-1">
                  <button
                    type="button"
                    aria-label={SPECIALIST_STRINGS.billingDiffAccept}
                    title={SPECIALIST_STRINGS.billingDiffAccept}
                    disabled={inFlight}
                    className={cn(
                      "rounded p-1 transition-colors",
                      state?.decision === "accept"
                        ? "bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400"
                        : "text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800"
                    )}
                    onClick={() => setRowDecision(edit.timeEntryId, "accept")}
                  >
                    <Check className="size-3.5" />
                  </button>
                  <button
                    type="button"
                    aria-label={SPECIALIST_STRINGS.billingDiffReject}
                    title={SPECIALIST_STRINGS.billingDiffReject}
                    disabled={inFlight}
                    className={cn(
                      "rounded p-1 transition-colors",
                      state?.decision === "reject"
                        ? "bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400"
                        : "text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800"
                    )}
                    onClick={() => setRowDecision(edit.timeEntryId, "reject")}
                  >
                    <X className="size-3.5" />
                  </button>
                  <button
                    type="button"
                    aria-label={SPECIALIST_STRINGS.billingDiffEdit}
                    title={SPECIALIST_STRINGS.billingDiffEdit}
                    disabled={inFlight}
                    className={cn(
                      "rounded p-1 transition-colors",
                      state?.decision === "edit"
                        ? "bg-teal-100 text-teal-700 dark:bg-teal-900/40 dark:text-teal-400"
                        : "text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800"
                    )}
                    onClick={() => setRowDecision(edit.timeEntryId, "edit")}
                  >
                    <Pencil className="size-3.5" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Grouping mode — list of proposed line groups */}
      {!isPolish && groups.length > 0 && (
        <div className="space-y-2">
          {groups.map((group, idx) => (
            <div
              key={group.description}
              data-testid="group-row"
              className="rounded border border-slate-100 p-3 dark:border-slate-800"
            >
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                  {group.description}
                </span>
                <span className="text-xs text-slate-500">
                  {group.hours}h &middot;{" "}
                  {group.sourceTimeEntryIds.length === 1
                    ? "1 entry"
                    : `${group.sourceTimeEntryIds.length} entries`}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Error */}
      {error && (
        <p role="alert" className="text-xs text-red-600">
          {error}
        </p>
      )}

      {/* Reject form */}
      {showRejectForm && (
        <div data-testid="reject-form" className="space-y-2">
          <textarea
            data-testid="reject-reason"
            className="w-full rounded border border-slate-200 px-3 py-2 text-sm text-slate-900 focus:ring-1 focus:ring-teal-500 focus:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
            placeholder="Reason for rejection..."
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
            rows={2}
          />
          <div className="flex gap-2">
            <Button
              variant="destructive"
              size="sm"
              onClick={handleReject}
              disabled={inFlight || !rejectReason.trim()}
            >
              {inFlight ? "Rejecting..." : "Confirm Reject"}
            </Button>
            <Button
              variant="plain"
              size="sm"
              onClick={() => setShowRejectForm(false)}
              disabled={inFlight}
            >
              Cancel
            </Button>
          </div>
        </div>
      )}

      {/* Footer actions */}
      {!showRejectForm && (
        <div className="flex gap-2">
          <Button
            variant="accent"
            size="sm"
            onClick={handleApproveAll}
            disabled={inFlight}
            data-testid="approve-all-btn"
          >
            {inFlight ? "Approving..." : SPECIALIST_STRINGS.billingDiffApproveAll}
          </Button>
          <Button
            variant="plain"
            size="sm"
            onClick={() => setShowRejectForm(true)}
            disabled={inFlight}
            data-testid="reject-all-btn"
          >
            {SPECIALIST_STRINGS.billingDiffRejectAll}
          </Button>
        </div>
      )}
    </div>
  );
}
