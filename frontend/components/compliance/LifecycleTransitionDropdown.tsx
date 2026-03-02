"use client";

import React, { useState } from "react";
import { ChevronDown, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { TransitionConfirmDialog } from "@/components/compliance/TransitionConfirmDialog";
import { PrerequisiteModal } from "@/components/prerequisite/prerequisite-modal";
import { checkPrerequisitesAction } from "@/lib/actions/prerequisite-actions";
import type { LifecycleStatus } from "@/lib/types";
import type {
  PrerequisiteCheck,
  PrerequisiteViolation,
} from "@/components/prerequisite/types";

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

/** Transitions that require a prerequisite check before proceeding */
function requiresPrerequisiteCheck(
  fromStatus: LifecycleStatus,
  toStatus: LifecycleStatus,
): boolean {
  return toStatus === "ACTIVE" && fromStatus === "ONBOARDING";
}

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
  const [checkingPrereqs, setCheckingPrereqs] = useState(false);

  // PrerequisiteModal state
  const [prereqModalOpen, setPrereqModalOpen] = useState(false);
  const [prereqViolations, setPrereqViolations] = useState<PrerequisiteViolation[]>([]);

  const transitions = ALLOWED_TRANSITIONS[currentStatus] ?? [];

  if (transitions.length === 0) {
    return null;
  }

  async function handleSelect(target: LifecycleStatus) {
    setPendingTarget(target);

    if (requiresPrerequisiteCheck(currentStatus, target)) {
      // Run prerequisite check before showing dialog
      setCheckingPrereqs(true);
      try {
        const check: PrerequisiteCheck = await checkPrerequisitesAction(
          "LIFECYCLE_ACTIVATION",
          "CUSTOMER",
          customerId,
        );
        if (check.passed) {
          // Prerequisites met — show confirm dialog
          setDialogOpen(true);
        } else {
          // Prerequisites NOT met — show prerequisite modal
          setPrereqViolations(check.violations);
          setPrereqModalOpen(true);
        }
      } catch {
        // If prerequisite check fails, fall through to normal confirm dialog
        setDialogOpen(true);
      } finally {
        setCheckingPrereqs(false);
      }
    } else {
      setDialogOpen(true);
    }
  }

  function handleSuccess() {
    onTransition?.();
  }

  function handlePrerequisiteFailed(check: PrerequisiteCheck) {
    // Backend returned 422 during transition — open PrerequisiteModal
    setPrereqViolations(check.violations);
    setPrereqModalOpen(true);
  }

  function handlePrereqResolved() {
    // All prerequisites resolved — now proceed with confirm dialog
    setPrereqModalOpen(false);
    setDialogOpen(true);
  }

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="outline" size="sm" disabled={checkingPrereqs}>
            {checkingPrereqs ? (
              <>
                <Loader2 className="mr-1.5 size-3 animate-spin" />
                Checking...
              </>
            ) : (
              <>
                Change Status
                <ChevronDown className="ml-1.5 size-3" />
              </>
            )}
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
          onPrerequisiteFailed={handlePrerequisiteFailed}
        />
      )}

      {prereqModalOpen && (
        <PrerequisiteModal
          open={prereqModalOpen}
          onOpenChange={setPrereqModalOpen}
          context="LIFECYCLE_ACTIVATION"
          violations={prereqViolations}
          entityType="CUSTOMER"
          entityId={customerId}
          slug={slug}
          onResolved={handlePrereqResolved}
        />
      )}
    </>
  );
}
