"use client";

import { useState, useCallback } from "react";
import { Check, X, Pencil } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { SPECIALIST_STRINGS } from "@/components/assistant/specialist-strings";
import { approveInvocation, rejectInvocation } from "@/lib/api/assistant-specialists";

// ---- Types ----

export interface IntakeFieldDiffProps {
  invocationId: string;
  contextEntityType: string;
  contextEntityId: string;
  proposedFields: Record<string, unknown>;
  currentFields?: Record<string, unknown>;
  extractionPath: "TEXT" | "VISION";
  popiaFlaggedFields: string[];
  validationFlags: string[];
  onApproved?: () => void;
  onRejected?: () => void;
}

type RowDecision = "accept" | "reject" | "edit";

interface RowState {
  decision: RowDecision;
  editedValue: string;
}

// ---- Helpers ----

function formatFieldName(slug: string): string {
  return slug.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

function toDisplayValue(value: unknown): string {
  if (value === null || value === undefined) return "";
  if (typeof value === "string") return value;
  return String(value);
}

function getValidationWarning(flag: string): string {
  switch (flag) {
    case "POSSIBLE_INJECTION_DETECTED":
      return SPECIALIST_STRINGS.intakeInjectionWarning;
    case "RSA_ID_CHECKSUM_FAIL":
      return SPECIALIST_STRINGS.intakeRsaIdChecksumWarning;
    default:
      return flag.replace(/_/g, " ");
  }
}

// ---- Component ----

export function IntakeFieldDiff({
  invocationId,
  contextEntityType,
  contextEntityId,
  proposedFields,
  currentFields = {},
  extractionPath,
  popiaFlaggedFields,
  validationFlags,
  onApproved,
  onRejected,
}: IntakeFieldDiffProps) {
  const fieldSlugs = Object.keys(proposedFields);

  const [rowStates, setRowStates] = useState<Record<string, RowState>>(() => {
    const initial: Record<string, RowState> = {};
    for (const slug of fieldSlugs) {
      initial[slug] = {
        decision: "accept",
        editedValue: toDisplayValue(proposedFields[slug]),
      };
    }
    return initial;
  });

  const [inFlight, setInFlight] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [showRejectForm, setShowRejectForm] = useState(false);

  const setRowDecision = useCallback((slug: string, decision: RowDecision) => {
    setRowStates((prev) => ({
      ...prev,
      [slug]: { ...prev[slug], decision },
    }));
  }, []);

  const setRowEditValue = useCallback((slug: string, value: string) => {
    setRowStates((prev) => ({
      ...prev,
      [slug]: { ...prev[slug], editedValue: value },
    }));
  }, []);

  const handleApproveAll = useCallback(async () => {
    if (inFlight) return;
    setInFlight(true);
    setError(null);
    try {
      const acceptedFields: Record<string, unknown> = {};
      for (const slug of fieldSlugs) {
        const state = rowStates[slug];
        if (state?.decision !== "reject") {
          acceptedFields[slug] = state?.editedValue ?? proposedFields[slug];
        }
      }

      const appliedOutput = {
        kind: "IntakeExtractionPayload" as const,
        contextEntityType,
        contextEntityId,
        proposedFields: acceptedFields,
        extractionPath,
        popiaFlaggedFields,
        validationFlags,
      };
      await approveInvocation(invocationId, appliedOutput);
      onApproved?.();
    } catch (err) {
      console.error("[IntakeFieldDiff] approve failed", err);
      setError("Failed to approve changes. Please try again.");
    } finally {
      setInFlight(false);
    }
  }, [
    inFlight,
    fieldSlugs,
    rowStates,
    proposedFields,
    contextEntityType,
    contextEntityId,
    extractionPath,
    popiaFlaggedFields,
    validationFlags,
    invocationId,
    onApproved,
  ]);

  const handleReject = useCallback(async () => {
    if (inFlight || !rejectReason.trim()) return;
    setInFlight(true);
    setError(null);
    try {
      await rejectInvocation(invocationId, rejectReason.trim());
      onRejected?.();
    } catch (err) {
      console.error("[IntakeFieldDiff] reject failed", err);
      setError("Failed to reject changes. Please try again.");
    } finally {
      setInFlight(false);
    }
  }, [inFlight, invocationId, rejectReason, onRejected]);

  const popiaSet = new Set(popiaFlaggedFields);

  // Empty state
  if (fieldSlugs.length === 0) {
    return (
      <div
        data-testid="intake-field-diff"
        className="rounded-lg border border-slate-200 p-4 dark:border-slate-800"
      >
        <p className="text-sm text-slate-500">{SPECIALIST_STRINGS.intakeEmptyState}</p>
      </div>
    );
  }

  return (
    <div
      data-testid="intake-field-diff"
      className="space-y-4 rounded-lg border border-slate-200 p-4 dark:border-slate-800"
    >
      {/* Header */}
      <div className="flex items-center gap-3">
        <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">
          {SPECIALIST_STRINGS.intakeDiffTitle}
        </h3>
        <span
          data-testid="extraction-path-badge"
          className={cn(
            "inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold tracking-wide uppercase",
            extractionPath === "VISION"
              ? "bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-300"
              : "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400"
          )}
        >
          {extractionPath === "VISION"
            ? SPECIALIST_STRINGS.intakeVisionBadge
            : SPECIALIST_STRINGS.intakeTextBadge}
        </span>
      </div>

      {/* Validation flag banners */}
      {validationFlags.length > 0 && (
        <div className="space-y-1">
          {validationFlags.map((flag) => (
            <div
              key={flag}
              data-testid="validation-warning"
              className="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-300"
            >
              {getValidationWarning(flag)}
            </div>
          ))}
        </div>
      )}

      {/* Per-field diff rows */}
      <div className="space-y-1">
        <div className="grid grid-cols-[1fr_1fr_1fr_auto] gap-3 pb-1 text-xs font-medium text-slate-500 dark:text-slate-400">
          <span>Field</span>
          <span>Current</span>
          <span>Proposed</span>
          <span className="w-24 text-center">Actions</span>
        </div>

        {fieldSlugs.map((slug) => {
          const state = rowStates[slug];
          const isEditing = state?.decision === "edit";
          const isRejected = state?.decision === "reject";
          const currentValue = toDisplayValue(currentFields[slug]);
          const proposedValue = toDisplayValue(proposedFields[slug]);
          const isPopia = popiaSet.has(slug);

          return (
            <div
              key={slug}
              data-testid="diff-row"
              data-field-slug={slug}
              className={cn(
                "grid grid-cols-[1fr_1fr_1fr_auto] gap-3 rounded border border-slate-100 p-2 text-xs dark:border-slate-800",
                isRejected && "opacity-50"
              )}
            >
              <span className="flex items-center gap-1.5 font-medium text-slate-700 dark:text-slate-300">
                {formatFieldName(slug)}
              </span>

              <span className="rounded bg-slate-50 px-2 py-1.5 font-mono text-[11px] text-slate-600 dark:bg-slate-900 dark:text-slate-400">
                {currentValue || "—"}
              </span>

              {isEditing ? (
                <textarea
                  data-testid="edit-textarea"
                  className="rounded border border-teal-300 bg-white px-2 py-1.5 font-mono text-[11px] text-slate-900 focus:ring-1 focus:ring-teal-500 focus:outline-none dark:border-teal-700 dark:bg-slate-900 dark:text-slate-100"
                  value={state.editedValue}
                  onChange={(e) => setRowEditValue(slug, e.target.value)}
                  rows={2}
                />
              ) : (
                <span
                  className={cn(
                    "flex items-center gap-1.5 rounded px-2 py-1.5 font-mono text-[11px]",
                    isRejected
                      ? "bg-slate-50 text-slate-500 dark:bg-slate-900 dark:text-slate-500"
                      : "bg-green-50 text-green-800 dark:bg-green-950/40 dark:text-green-300"
                  )}
                >
                  {state?.editedValue ?? proposedValue}
                  {isPopia && (
                    <span
                      data-testid="popia-badge"
                      className="inline-flex items-center rounded-full bg-amber-100 px-1.5 py-0.5 text-[9px] font-semibold tracking-wide text-amber-700 uppercase dark:bg-amber-900/40 dark:text-amber-400"
                    >
                      {SPECIALIST_STRINGS.intakePopiaBadge}
                    </span>
                  )}
                </span>
              )}

              <div className="flex w-24 items-center justify-center gap-1">
                <button
                  type="button"
                  aria-label={SPECIALIST_STRINGS.intakeDiffAccept}
                  title={SPECIALIST_STRINGS.intakeDiffAccept}
                  disabled={inFlight}
                  className={cn(
                    "rounded p-1 transition-colors",
                    state?.decision === "accept"
                      ? "bg-green-100 text-green-700 dark:bg-green-900/40 dark:text-green-400"
                      : "text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800"
                  )}
                  onClick={() => setRowDecision(slug, "accept")}
                >
                  <Check className="size-3.5" />
                </button>
                <button
                  type="button"
                  aria-label={SPECIALIST_STRINGS.intakeDiffReject}
                  title={SPECIALIST_STRINGS.intakeDiffReject}
                  disabled={inFlight}
                  className={cn(
                    "rounded p-1 transition-colors",
                    state?.decision === "reject"
                      ? "bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400"
                      : "text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800"
                  )}
                  onClick={() => setRowDecision(slug, "reject")}
                >
                  <X className="size-3.5" />
                </button>
                <button
                  type="button"
                  aria-label={SPECIALIST_STRINGS.intakeDiffEdit}
                  title={SPECIALIST_STRINGS.intakeDiffEdit}
                  disabled={inFlight}
                  className={cn(
                    "rounded p-1 transition-colors",
                    state?.decision === "edit"
                      ? "bg-teal-100 text-teal-700 dark:bg-teal-900/40 dark:text-teal-400"
                      : "text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-800"
                  )}
                  onClick={() => setRowDecision(slug, "edit")}
                >
                  <Pencil className="size-3.5" />
                </button>
              </div>
            </div>
          );
        })}
      </div>

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
            {inFlight ? "Approving..." : SPECIALIST_STRINGS.intakeDiffApproveAll}
          </Button>
          <Button
            variant="plain"
            size="sm"
            onClick={() => setShowRejectForm(true)}
            disabled={inFlight}
            data-testid="reject-all-btn"
          >
            {SPECIALIST_STRINGS.intakeDiffRejectAll}
          </Button>
        </div>
      )}
    </div>
  );
}
