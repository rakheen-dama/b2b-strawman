"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { ArrowRight } from "lucide-react";

import type {
  Customer,
  LifecycleStatus,
  ChecklistInstanceResponse,
  LifecycleHistoryEntry,
} from "@/lib/types";
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { LifecycleStepper } from "@/components/customers/lifecycle-stepper";
import { ChecklistPanel } from "@/components/customers/checklist-panel";
import { transitionLifecycle } from "@/app/(app)/org/[slug]/customers/actions";

/** Valid transitions from each status. */
const VALID_TRANSITIONS: Record<LifecycleStatus, LifecycleStatus[]> = {
  PROSPECT: ["ONBOARDING"],
  ONBOARDING: ["ACTIVE"],
  ACTIVE: ["DORMANT", "OFFBOARDING"],
  DORMANT: ["ACTIVE", "OFFBOARDING"],
  OFFBOARDING: ["OFFBOARDED"],
  OFFBOARDED: [],
};

interface LifecycleTabProps {
  customer: Customer;
  checklist: ChecklistInstanceResponse | null;
  history: LifecycleHistoryEntry[];
}

export function LifecycleTab({
  customer,
  checklist,
  history,
}: LifecycleTabProps) {
  const router = useRouter();
  const [isPending, setIsPending] = React.useState(false);
  const currentStatus = customer.lifecycleStatus ?? "PROSPECT";
  const validTargets = VALID_TRANSITIONS[currentStatus] ?? [];

  async function handleTransition(target: LifecycleStatus) {
    setIsPending(true);
    try {
      await transitionLifecycle(customer.id, target);
      toast.success(`Customer moved to ${target.toLowerCase()}`);
      router.refresh();
    } catch {
      toast.error("Failed to transition customer");
    } finally {
      setIsPending(false);
    }
  }

  return (
    <div className="space-y-6">
      {/* Stepper */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Lifecycle Journey</CardTitle>
        </CardHeader>
        <CardContent>
          <LifecycleStepper currentStatus={currentStatus} />

          {/* Transition buttons */}
          {validTargets.length > 0 && (
            <div className="mt-6 flex items-center gap-3 border-t border-slate-100 pt-4">
              <span className="text-sm text-slate-500">Move to:</span>
              {validTargets.map((target) => (
                <Button
                  key={target}
                  variant="outline"
                  size="sm"
                  disabled={isPending}
                  onClick={() => handleTransition(target)}
                >
                  {target.charAt(0) + target.slice(1).toLowerCase()}
                  <ArrowRight className="ml-1 size-3" />
                </Button>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Checklist (for Onboarding stage) */}
      {(currentStatus === "ONBOARDING" || checklist) && (
        <ChecklistPanel checklist={checklist} />
      )}

      {/* Lifecycle History */}
      {history.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Lifecycle History</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-3">
              {history.map((entry) => (
                <li
                  key={entry.id}
                  className={cn(
                    "flex items-start justify-between gap-4 rounded-md px-3 py-2 text-sm",
                    "odd:bg-slate-50/50"
                  )}
                >
                  <div className="flex items-center gap-2">
                    <StatusBadge
                      status={
                        (entry.details?.["targetStatus"] as string) ??
                        entry.eventType
                      }
                    />
                    <span className="text-slate-600">
                      {entry.eventType.replace(/_/g, " ").toLowerCase()}
                    </span>
                  </div>
                  <time className="shrink-0 text-xs text-slate-400">
                    {formatDate(entry.occurredAt)}
                  </time>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
