"use client";

import { useState } from "react";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { daysBetween, todayInSA } from "@b2mash/shared/date";
import { cn } from "@/lib/utils";
import { HelpTip } from "@/components/help-tip";
import type { PrescriptionTracker, PrescriptionStatus } from "@/lib/types";
import { CreatePrescriptionDialog } from "./create-prescription-dialog";
import { InterruptDialog } from "./interrupt-dialog";

/**
 * One matter's prescription trackers (Phase 87 613D, Open Question #17): mounted in the matter's
 * Court dates tab now that the court-calendar page — and its Prescriptions tab — is gone. Diary
 * PRESCRIPTION rows link here (`/matters/{id}?tab=court-dates&prescriptionId=…`).
 */
interface PrescriptionTrackerListProps {
  trackers: PrescriptionTracker[];
  slug: string;
  /** The matter these trackers belong to — new trackers are created on it (selector locked). */
  matterId: string;
  onRefresh: () => void;
  /**
   * MANAGE_LEGAL (or admin / owner). Every prescription write (create, interrupt) needs it
   * (backend @RequiresCapability), so the write affordances fail closed — hidden unless known.
   */
  canManageLegal?: boolean;
}

function statusBadge(status: PrescriptionStatus) {
  switch (status) {
    case "RUNNING":
      return (
        <Badge className="bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300">
          Running
        </Badge>
      );
    case "WARNED":
      return <Badge variant="warning">Warned</Badge>;
    case "INTERRUPTED":
      return <Badge variant="neutral">Interrupted</Badge>;
    case "EXPIRED":
      return <Badge variant="destructive">Expired</Badge>;
    default:
      return <Badge variant="neutral">{status}</Badge>;
  }
}

function prescriptionTypeLabel(type: string): string {
  switch (type) {
    case "GENERAL_3Y":
      return "General (3yr)";
    case "DEBT_6Y":
      return "Debt (6yr)";
    case "MORTGAGE_30Y":
      return "Mortgage (30yr)";
    case "DELICT_3Y":
      return "Delict (3yr)";
    case "CONTRACT_3Y":
      return "Contract (3yr)";
    case "CUSTOM":
      return "Custom";
    default:
      return type;
  }
}

/**
 * Calendar days from today in South Africa to the yyyy-MM-dd last day to serve (the stored
 * prescription date): 0 is the date itself, negative is past it. The same answer on the UTC SSR server and in any browser, at any
 * hour — `new Date("yyyy-MM-dd")` parses as UTC midnight, which overstated the statutory count by
 * one between 00:00 and 02:00 SAST and showed "—" on the date itself.
 */
function daysRemaining(prescriptionDate: string): number {
  return daysBetween(todayInSA(), prescriptionDate);
}

/**
 * The Days left cell. The stored prescription date is the last day to serve (the anniversary minus
 * one day, SA's civilian computation), so on day 0 the backend still reports it live and the cell
 * reads "Today"; it is EXPIRED from the day after. Day 0 stays status-driven: a historic tracker
 * that still stores the anniversary, or an INTERRUPTED one, reads "—".
 */
function daysLeftLabel(remaining: number, status: PrescriptionStatus): string | number {
  if (remaining > 0) return remaining;
  const live = status !== "EXPIRED" && status !== "INTERRUPTED";
  return remaining === 0 && live ? "Today" : "—";
}

const TH =
  "px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400";

export function PrescriptionTrackerList({
  trackers,
  slug,
  matterId,
  onRefresh,
  canManageLegal = false,
}: PrescriptionTrackerListProps) {
  const [interruptTarget, setInterruptTarget] = useState<PrescriptionTracker | null>(null);

  return (
    <div data-testid="prescription-tracker-list">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="flex items-center gap-1.5 text-sm font-medium text-slate-700 dark:text-slate-300">
          Prescription trackers
          <HelpTip code="legal.lastDayToServe" />
        </h3>
        {canManageLegal && (
          <CreatePrescriptionDialog
            slug={slug}
            matterId={matterId}
            triggerLabel="New prescription tracker"
            triggerVariant="outline"
            onSuccess={onRefresh}
          />
        )}
      </div>

      {trackers.length === 0 ? (
        <div className="rounded-lg border border-slate-200 p-8 text-center dark:border-slate-800">
          <p className="text-sm text-slate-500 dark:text-slate-400">
            No prescription trackers on this matter.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className={TH}>Type</th>
                <th className={TH}>Cause of action</th>
                <th className={TH}>Last day to serve</th>
                <th className={TH}>Status</th>
                <th className={cn(TH, "text-right")}>Days left</th>
                {canManageLegal && <th className={cn(TH, "text-right")}>Actions</th>}
              </tr>
            </thead>
            <tbody>
              {trackers.map((tracker) => {
                const remaining = daysRemaining(tracker.prescriptionDate);
                // Interrupt records a past service date, so it stays available once EXPIRED; the
                // dialog (and backend) check that date against the last day to serve.
                const canInterrupt = tracker.status !== "INTERRUPTED";

                return (
                  <tr
                    key={tracker.id}
                    data-testid={`prescription-tracker-row-${tracker.id}`}
                    className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                  >
                    <td className="px-4 py-3">
                      <Badge variant="outline">
                        {prescriptionTypeLabel(tracker.prescriptionType)}
                      </Badge>
                    </td>
                    <td className="px-4 py-3 font-mono text-sm text-slate-600 dark:text-slate-400">
                      {tracker.causeOfActionDate}
                    </td>
                    <td className="px-4 py-3 font-mono text-sm text-slate-600 dark:text-slate-400">
                      {tracker.prescriptionDate}
                    </td>
                    <td className="px-4 py-3">{statusBadge(tracker.status)}</td>
                    <td className="px-4 py-3 text-right">
                      <span
                        className={cn(
                          "text-sm font-medium",
                          remaining <= 30
                            ? "text-red-600"
                            : remaining <= 90
                              ? "text-amber-600"
                              : "text-slate-600 dark:text-slate-400"
                        )}
                      >
                        {daysLeftLabel(remaining, tracker.status)}
                      </span>
                    </td>
                    {canManageLegal && (
                      <td className="px-4 py-3 text-right">
                        {canInterrupt && (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setInterruptTarget(tracker)}
                          >
                            Interrupt
                          </Button>
                        )}
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {canManageLegal && interruptTarget && (
        <InterruptDialog
          slug={slug}
          prescriptionId={interruptTarget.id}
          lastDayToServe={interruptTarget.prescriptionDate}
          open={!!interruptTarget}
          onOpenChange={(v) => {
            if (!v) setInterruptTarget(null);
          }}
          onSuccess={onRefresh}
        />
      )}
    </div>
  );
}
