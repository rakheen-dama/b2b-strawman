"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@b2mash/ui/button";
import { InterestRunWizard } from "@/components/trust/InterestRunWizard";
import { DeleteInterestRunDialog } from "@/components/trust/DeleteInterestRunDialog";
import { LpffRateDialog } from "@/components/trust/LpffRateDialog";
import type { InterestRun } from "@/lib/types/trust";

interface InterestPageClientProps {
  accountId: string;
  variant: "wizard" | "rate";
  canApprove?: boolean;
  currency?: string;
  /** The viewer's members.id — hides self-approval in the wizard (630B). */
  memberId?: string | null;
}

export function InterestPageClient({
  accountId,
  variant,
  canApprove,
  currency,
  memberId,
}: InterestPageClientProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);

  function handleSuccess() {
    router.refresh();
  }

  if (variant === "wizard") {
    return (
      <>
        <Button onClick={() => setOpen(true)}>New Interest Run</Button>
        {/* The header mount is MANAGE_TRUST-gated in page.tsx, so canManage is true here. */}
        <InterestRunWizard
          accountId={accountId}
          open={open}
          onOpenChange={setOpen}
          onSuccess={handleSuccess}
          canApprove={canApprove ?? false}
          canManage
          currency={currency ?? "ZAR"}
          memberId={memberId ?? null}
        />
      </>
    );
  }

  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        Add Rate
      </Button>
      <LpffRateDialog
        accountId={accountId}
        open={open}
        onOpenChange={setOpen}
        onSuccess={handleSuccess}
      />
    </>
  );
}

// ── Row actions (630B, G88-F39 / §14.12 #2) ─────────────────────────

type RowActionLabel = "Continue" | "Review & approve" | "Review" | "Post";

interface RowActionInput {
  hasManageTrust: boolean;
  canApproveTrust: boolean;
  memberId: string | null;
}

/** §14.3.2 action table (ADR-362). The backend stays the authority for every transition. */
function rowAction(run: InterestRun, input: RowActionInput): RowActionLabel | null {
  if (run.status === "DRAFT") {
    if (!(run.allocationCount > 0)) return input.hasManageTrust ? "Continue" : null;
    const isCreator = !!input.memberId && run.createdBy === input.memberId;
    return input.canApproveTrust && !isCreator ? "Review & approve" : "Review";
  }
  if (run.status === "APPROVED") return input.hasManageTrust ? "Post" : null;
  return null; // POSTED — the lifecycle is complete
}

interface InterestRunRowActionsProps extends RowActionInput {
  run: InterestRun;
  accountId: string;
  currency: string;
}

/**
 * The Actions cell of one interest-run row: re-opens the wizard on the persisted run and offers
 * "Delete draft" on DRAFT rows. No `*Trigger asChild` here — each dialog owns its own trigger.
 */
export function InterestRunRowActions({
  run,
  accountId,
  currency,
  memberId,
  hasManageTrust,
  canApproveTrust,
}: InterestRunRowActionsProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const action = rowAction(run, { hasManageTrust, canApproveTrust, memberId });
  const isCreator = !!memberId && run.createdBy === memberId;
  const awaitingOtherMember = run.status === "DRAFT" && run.allocationCount > 0 && isCreator;
  // Any MANAGE_TRUST member, creator or not — the two-person rule governs approval, not abandonment.
  const canDelete = run.status === "DRAFT" && hasManageTrust;

  // `open`: a refresh while the wizard is open (e.g. approved by a member without MANAGE_TRUST)
  // must not unmount it mid-dialog.
  if (!action && !canDelete && !open) {
    return <span className="text-slate-400 dark:text-slate-500">—</span>;
  }

  return (
    <div className="flex flex-col items-start gap-1">
      <div className="flex items-center gap-2">
        {action && (
          <Button type="button" size="sm" variant="outline" onClick={() => setOpen(true)}>
            {action}
          </Button>
        )}
        {canDelete && (
          <DeleteInterestRunDialog
            runId={run.id}
            periodStart={run.periodStart}
            periodEnd={run.periodEnd}
          />
        )}
      </div>
      {awaitingOtherMember && (
        <p
          className="text-xs text-slate-500 dark:text-slate-400"
          data-testid={`awaiting-approval-${run.id}`}
        >
          Awaiting approval by another member
        </p>
      )}
      {(action || open) && (
        <InterestRunWizard
          accountId={accountId}
          open={open}
          onOpenChange={setOpen}
          onSuccess={() => router.refresh()}
          canApprove={canApproveTrust}
          canManage={hasManageTrust}
          currency={currency}
          memberId={memberId}
          initialRun={run}
        />
      )}
    </div>
  );
}
