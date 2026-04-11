"use client";

import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import type { PrescriptionTracker, PrescriptionStatus } from "@/lib/types";
import { CreatePrescriptionDialog } from "./create-prescription-dialog";
import { InterruptDialog } from "./interrupt-dialog";

interface PrescriptionTabProps {
  trackers: PrescriptionTracker[];
  slug: string;
  onRefresh: () => void;
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

function daysRemaining(prescriptionDate: string): number {
  const target = new Date(prescriptionDate);
  const now = new Date();
  const diff = target.getTime() - now.getTime();
  return Math.ceil(diff / (1000 * 60 * 60 * 24));
}

export function PrescriptionTab({ trackers, slug, onRefresh }: PrescriptionTabProps) {
  const [interruptTarget, setInterruptTarget] = useState<string | null>(null);

  return (
    <div data-testid="prescription-tab">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-sm font-medium text-slate-700 dark:text-slate-300">
          Prescription Trackers
        </h3>
        <CreatePrescriptionDialog slug={slug} onSuccess={onRefresh} />
      </div>

      {trackers.length === 0 ? (
        <div className="rounded-lg border border-slate-200 p-8 text-center dark:border-slate-800">
          <p className="text-sm text-slate-500 dark:text-slate-400">
            No prescription trackers found.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-slate-200 dark:border-slate-800">
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Matter
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Client
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Type
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Cause of Action
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Prescription Date
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Status
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Days Left
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {trackers.map((tracker) => {
                const remaining = daysRemaining(tracker.prescriptionDate);
                const canInterrupt = tracker.status === "RUNNING" || tracker.status === "WARNED";

                return (
                  <tr
                    key={tracker.id}
                    className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/50"
                  >
                    <td className="px-4 py-3 text-sm font-medium text-slate-950 dark:text-slate-50">
                      {tracker.projectName}
                    </td>
                    <td className="px-4 py-3 text-sm text-slate-700 dark:text-slate-300">
                      {tracker.customerName}
                    </td>
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
                        {remaining > 0 ? remaining : "\u2014"}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      {canInterrupt && (
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setInterruptTarget(tracker.id)}
                        >
                          Interrupt
                        </Button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {interruptTarget && (
        <InterruptDialog
          slug={slug}
          prescriptionId={interruptTarget}
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
