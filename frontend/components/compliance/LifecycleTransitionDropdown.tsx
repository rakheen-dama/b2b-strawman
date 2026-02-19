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

// Valid transitions per status (mirrors LifecycleStatus.java)
const ALLOWED_TRANSITIONS: Record<string, string[]> = {
  PROSPECT: ["ONBOARDING", "ACTIVE"],
  ONBOARDING: ["ACTIVE", "OFFBOARDING"],
  ACTIVE: ["DORMANT", "OFFBOARDING"],
  DORMANT: ["ACTIVE", "OFFBOARDING"],
  OFFBOARDING: ["OFFBOARDED"],
  OFFBOARDED: [],
};

// Human-readable labels for each target (context-aware)
function getTransitionLabel(fromStatus: string, toStatus: string): string {
  if (toStatus === "ONBOARDING") return "Start Onboarding";
  if (toStatus === "ACTIVE" && fromStatus === "PROSPECT") return "Activate";
  if (toStatus === "ACTIVE") return "Reactivate";
  if (toStatus === "DORMANT") return "Mark as Dormant";
  if (toStatus === "OFFBOARDING") return "Offboard Customer";
  if (toStatus === "OFFBOARDED") return "Complete Offboarding";
  return toStatus;
}

const DESTRUCTIVE_TARGETS = new Set(["OFFBOARDING", "OFFBOARDED"]);

interface LifecycleTransitionDropdownProps {
  currentStatus: string;
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
  const [pendingTarget, setPendingTarget] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);

  const transitions = ALLOWED_TRANSITIONS[currentStatus] ?? [];

  if (transitions.length === 0) {
    return null;
  }

  function handleSelect(target: string) {
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
