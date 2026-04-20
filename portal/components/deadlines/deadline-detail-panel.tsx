"use client";

import { useEffect } from "react";
import { X } from "lucide-react";
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { PortalDeadline } from "@/lib/api/deadlines";

const SOURCE_ENTITY_LABEL: Record<string, string> = {
  FILING_SCHEDULE: "Filing schedule",
  COURT_DATE: "Court date",
  PRESCRIPTION_TRACKER: "Prescription",
  CUSTOM_FIELD_DATE: "Custom date",
};

const STATUS_BADGE_CLASS: Record<string, string> = {
  UPCOMING:
    "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
  DUE_SOON:
    "bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300",
  OVERDUE: "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300",
  COMPLETED:
    "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
  CANCELLED:
    "bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-400",
};

function formatStatus(status: string): string {
  return status.replace(/_/g, " ");
}

interface DeadlineDetailPanelProps {
  open: boolean;
  deadline: PortalDeadline | null;
  onClose: () => void;
}

/**
 * Slide-in right panel showing a deadline's label, due date, status badge,
 * sanitised description and source-entity reference.
 *
 * Portal's `components/ui/` has no Sheet/Dialog primitive, so this is a
 * hand-rolled fixed-position drawer. Backdrop click and `Escape` close it.
 *
 * The panel renders conditionally off `open`: when `open === false` it
 * animates off-screen but remains in the DOM (so an exit animation completes).
 * When `deadline === null` the panel body renders nothing — callers typically
 * set `open={false}` at the same time.
 */
export function DeadlineDetailPanel({
  open,
  deadline,
  onClose,
}: DeadlineDetailPanelProps) {
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  const statusClass = deadline
    ? STATUS_BADGE_CLASS[deadline.status] ?? STATUS_BADGE_CLASS.UPCOMING
    : STATUS_BADGE_CLASS.UPCOMING;
  const sourceLabel = deadline
    ? SOURCE_ENTITY_LABEL[deadline.sourceEntity] ?? deadline.sourceEntity
    : "";

  return (
    <>
      {/* Backdrop */}
      <div
        aria-hidden={!open}
        onClick={onClose}
        data-testid="deadline-detail-backdrop"
        className={cn(
          "fixed inset-0 z-40 bg-slate-950/30 transition-opacity",
          open ? "opacity-100" : "pointer-events-none opacity-0",
        )}
      />
      {/* Panel */}
      <aside
        role="dialog"
        aria-modal="true"
        aria-label={deadline?.label ?? "Deadline details"}
        data-state={open ? "open" : "closed"}
        data-testid="deadline-detail-panel"
        className={cn(
          "fixed inset-y-0 right-0 z-50 flex w-full max-w-md flex-col border-l border-slate-200 bg-white shadow-xl transition-transform",
          open ? "translate-x-0" : "translate-x-full",
        )}
      >
        <header className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
          <h2 className="text-base font-semibold text-slate-900">
            Deadline details
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close panel"
            className="rounded-md p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
          >
            <X className="size-4" />
          </button>
        </header>
        <div className="flex-1 overflow-y-auto p-4">
          {deadline && (
            <dl className="space-y-4">
              <div>
                <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Label
                </dt>
                <dd className="mt-1 text-sm font-medium text-slate-900">
                  {deadline.label}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Due date
                </dt>
                <dd className="mt-1 font-mono text-sm tabular-nums text-slate-900">
                  {formatDate(deadline.dueDate)}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Status
                </dt>
                <dd className="mt-1">
                  <span
                    className={cn(
                      "inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium",
                      statusClass,
                    )}
                  >
                    {formatStatus(deadline.status)}
                  </span>
                </dd>
              </div>
              {deadline.descriptionSanitised && (
                <div>
                  <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Description
                  </dt>
                  <dd className="mt-1 whitespace-pre-wrap text-sm text-slate-700">
                    {deadline.descriptionSanitised}
                  </dd>
                </div>
              )}
              <div>
                <dt className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Source
                </dt>
                <dd className="mt-1 text-sm text-slate-700">
                  From: {sourceLabel}
                </dd>
              </div>
            </dl>
          )}
        </div>
      </aside>
    </>
  );
}
