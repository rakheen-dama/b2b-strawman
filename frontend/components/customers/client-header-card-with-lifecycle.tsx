"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ClientHeaderCard, type ClientHeaderCardProps } from "./client-header-card";
import { TransitionConfirmDialog } from "@/components/compliance/TransitionConfirmDialog";
import { PrerequisiteModal } from "@/components/prerequisite/prerequisite-modal";
import { checkPrerequisitesAction } from "@/lib/actions/prerequisite-actions";
import type { LifecycleStatus } from "@/lib/types";
import type { PrerequisiteCheck, PrerequisiteViolation } from "@/components/prerequisite/types";

interface ClientHeaderCardWithLifecycleProps extends ClientHeaderCardProps {
  /** The target lifecycle status for the smart action button */
  targetLifecycleStatus: LifecycleStatus | null;
}

export function ClientHeaderCardWithLifecycle({
  targetLifecycleStatus,
  ...cardProps
}: ClientHeaderCardWithLifecycleProps) {
  const router = useRouter();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [prereqModalOpen, setPrereqModalOpen] = useState(false);
  const [prereqViolations, setPrereqViolations] = useState<PrerequisiteViolation[]>([]);

  async function handlePrimaryAction() {
    if (!targetLifecycleStatus) return;

    // ONBOARDING -> ACTIVE requires prerequisite check first
    if (
      targetLifecycleStatus === "ACTIVE" &&
      cardProps.lifecycleStatus === "ONBOARDING"
    ) {
      try {
        const check: PrerequisiteCheck = await checkPrerequisitesAction(
          "LIFECYCLE_ACTIVATION",
          "CUSTOMER",
          cardProps.customerId
        );
        if (check.passed) {
          setDialogOpen(true);
        } else {
          setPrereqViolations(check.violations);
          setPrereqModalOpen(true);
        }
      } catch {
        // Fall through to confirm dialog on error
        setDialogOpen(true);
      }
    } else {
      setDialogOpen(true);
    }
  }

  function handleSuccess() {
    router.refresh();
  }

  function handlePrerequisiteFailed(check: PrerequisiteCheck) {
    setPrereqViolations(check.violations);
    setPrereqModalOpen(true);
  }

  function handlePrereqResolved() {
    setPrereqModalOpen(false);
    setDialogOpen(true);
  }

  return (
    <>
      <ClientHeaderCard
        {...cardProps}
        onPrimaryAction={targetLifecycleStatus ? handlePrimaryAction : undefined}
      />

      {targetLifecycleStatus && (
        <TransitionConfirmDialog
          open={dialogOpen}
          onOpenChange={setDialogOpen}
          slug={cardProps.slug}
          customerId={cardProps.customerId}
          targetStatus={targetLifecycleStatus}
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
          entityId={cardProps.customerId}
          slug={cardProps.slug}
          onResolved={handlePrereqResolved}
        />
      )}
    </>
  );
}
