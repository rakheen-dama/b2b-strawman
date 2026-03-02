"use client";

import { useState, useCallback } from "react";
import { Loader2 } from "lucide-react";
import { PrerequisiteModal } from "@/components/prerequisite/prerequisite-modal";
import { checkPrerequisitesAction } from "@/lib/actions/prerequisite-actions";
import type {
  PrerequisiteContext,
  PrerequisiteViolation,
} from "@/components/prerequisite/types";
import type { EntityType } from "@/lib/types";

interface PrerequisiteGatedActionProps {
  context: PrerequisiteContext;
  entityType: EntityType;
  entityId: string;
  slug: string;
  onAction: () => void;
  children: React.ReactNode;
}

export function PrerequisiteGatedAction({
  context,
  entityType,
  entityId,
  slug,
  onAction,
  children,
}: PrerequisiteGatedActionProps) {
  const [checking, setChecking] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [violations, setViolations] = useState<PrerequisiteViolation[]>([]);

  const handleClick = useCallback(async () => {
    setChecking(true);
    try {
      const check = await checkPrerequisitesAction(context, entityType, entityId);
      if (check.passed) {
        onAction();
      } else {
        setViolations(check.violations);
        setModalOpen(true);
      }
    } catch {
      // Fail-open: proceed to action if check throws
      onAction();
    } finally {
      setChecking(false);
    }
  }, [context, entityType, entityId, onAction]);

  function handleResolved() {
    setModalOpen(false);
    onAction();
  }

  return (
    <>
      <span
        onClick={checking ? undefined : handleClick}
        style={{ display: "contents" }}
        aria-busy={checking}
      >
        {children}
      </span>
      {checking && (
        <span className="sr-only">
          <Loader2 className="animate-spin" aria-label="Checking prerequisites..." />
        </span>
      )}
      {modalOpen && (
        <PrerequisiteModal
          open={modalOpen}
          onOpenChange={setModalOpen}
          context={context}
          violations={violations}
          entityType={entityType}
          entityId={entityId}
          slug={slug}
          onResolved={handleResolved}
        />
      )}
    </>
  );
}
