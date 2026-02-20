"use client";

import React, { useState } from "react";
import { ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { TransitionConfirmDialog } from "@/components/compliance/TransitionConfirmDialog";
import type { LifecycleStatus } from "@/lib/types";

// Valid transitions per status (mirrors LifecycleStatus.java)
const ALLOWED_TRANSITIONS: Record<LifecycleStatus, LifecycleStatus[]> = {
  PROSPECT: ["ONBOARDING"],
  ONBOARDING: ["ACTIVE", "OFFBOARDING"],
  ACTIVE: ["DORMANT", "OFFBOARDING"],
  DORMANT: ["ACTIVE", "OFFBOARDING"],
  OFFBOARDING: ["OFFBOARDED"],
  OFFBOARDED: ["ACTIVE"],
};

// Human-readable labels for each target (context-aware)
function getTransitionLabel(fromStatus: LifecycleStatus, toStatus: LifecycleStatus): string {
  if (toStatus === "ONBOARDING") return "Start Onboarding";
  if (toStatus === "ACTIVE" && (fromStatus === "PROSPECT" || fromStatus === "ONBOARDING"))
    return "Activate";
  if (toStatus === "ACTIVE") return "Reactivate";
  if (toStatus === "DORMANT") return "Mark as Dormant";
  if (toStatus === "OFFBOARDING") return "Offboard Customer";
  if (toStatus === "OFFBOARDED") return "Complete Offboarding";
  return toStatus;
}

const DESTRUCTIVE_TARGETS = new Set<LifecycleStatus>(["OFFBOARDING", "OFFBOARDED"]);

interface LifecycleTransitionDropdownProps {
  currentStatus: LifecycleStatus;
  customerId: string;
  slug: string;
  onTransition?: () => void;
}

export function LifecycleTransitionDropdown({
  currentStatus,
  customerId,
  slug,
  onTransition,
}: LifecycleTransitionDropdownProps) {
  const [pendingTarget, setPendingTarget] = useState<LifecycleStatus | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);

  const transitions = ALLOWED_TRANSITIONS[currentStatus] ?? [];

  if (transitions.length === 0) {
    return null;
  }

  function handleSelect(target: LifecycleStatus) {
    setPendingTarget(target);
    setDialogOpen(true);
  }

  function handleSuccess() {
    onTransition?.();
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" size="sm">
            Change Status
            <ChevronDown className="ml-1.5 size-3" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {transitions.map((target, idx) => {
            const isDestructive = DESTRUCTIVE_TARGETS.has(target);
            const prevIsNot = idx > 0 && !DESTRUCTIVE_TARGETS.has(transitions[idx - 1]);
            return (
              <React.Fragment key={target}>
                {isDestructive && prevIsNot && <DropdownMenuSeparator />}
                <DropdownMenuItem
                  variant={isDestructive ? "destructive" : "default"}
                  onClick={() => handleSelect(target)}
                >
                  {getTransitionLabel(currentStatus, target)}
                </DropdownMenuItem>
              </React.Fragment>
            );
          })}
        </DropdownMenuContent>
      </DropdownMenu>

      {pendingTarget && (
        <TransitionConfirmDialog
          open={dialogOpen}
          onOpenChange={setDialogOpen}
          slug={slug}
          customerId={customerId}
          targetStatus={pendingTarget}
          onSuccess={handleSuccess}
        />
      )}
    </>
  );
}
