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
  currency?: string;
}

export function InterestPageClient({ accountId, variant, currency }: InterestPageClientProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);

  function handleSuccess() {
    router.refresh();
  }

  if (variant === "wizard") {
    return (
      <>
        <Button onClick={() => setOpen(true)}>New Interest Run</Button>
        <InterestRunWizard
          accountId={accountId}
          open={open}
          onOpenChange={setOpen}
          currency={currency ?? "ZAR"}
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

// ── Row actions (630B, G88-F39 / §14.12 #2; Phase 92 645A) ──────────

type RowActionLabel = "Continue" | "Review";

/**
 * §14.3.2 action table (ADR-362) without approve and post: a calculated run is never approved or
 * posted (Phase 92 645A, section 86(5)) and the backend refuses both. A never-calculated draft is
 * continued by a MANAGE_TRUST member; a calculated draft can be reviewed by anyone on the page.
 */
function rowAction(run: InterestRun, hasManageTrust: boolean): RowActionLabel | null {
  if (run.status !== "DRAFT") return null;
  if (!(run.allocationCount > 0)) return hasManageTrust ? "Continue" : null;
  return "Review";
}

interface InterestRunRowActionsProps {
  run: InterestRun;
  accountId: string;
  currency: string;
  hasManageTrust: boolean;
}

/**
 * The Actions cell of one interest-run row: re-opens the wizard on the persisted run and offers
 * "Delete draft" on DRAFT rows. No `*Trigger asChild` here — each dialog owns its own trigger.
 */
export function InterestRunRowActions({
  run,
  accountId,
  currency,
  hasManageTrust,
}: InterestRunRowActionsProps) {
  const [open, setOpen] = useState(false);
  const action = rowAction(run, hasManageTrust);
  // Any MANAGE_TRUST member may abandon a draft.
  const canDelete = run.status === "DRAFT" && hasManageTrust;

  // `open`: a refresh while the wizard is open must not unmount it mid-dialog.
  if (!action && !canDelete && !open) {
    return <span className="text-slate-400 dark:text-slate-500">—</span>;
  }

  return (
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
      {(action || open) && (
        <InterestRunWizard
          accountId={accountId}
          open={open}
          onOpenChange={setOpen}
          currency={currency}
          initialRun={run}
        />
      )}
    </div>
  );
}
