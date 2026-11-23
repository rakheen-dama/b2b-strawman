"use client";

import type { MouseEvent } from "react";
import { AlertTriangle } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";

/** Phase 92 residual §1.5 wording (ruled 2026-11-23). */
export const UNSETTLED_SUBMIT_NOTICE =
  "A submit of this entry may already have been recorded — check the ledger before submitting again.";

/** The escape action's label — the backend's in-progress 409 names it ("choose 'Submit as a new entry'"). */
export const SUBMIT_AS_NEW_LABEL = "Submit as a new entry";

/** The line that makes the escape a deliberate step taken after the ledger was checked. */
export const SUBMIT_AS_NEW_PROMPT = "Only if you have checked the ledger and it was not recorded:";

interface UnsettledSubmitNoticeProps {
  /** `useIdempotencyKey(...).unsettled`: an earlier submit of this form has an unknown outcome. */
  show: boolean;
  /** A one-line inline form, for a row action with no dialog (approve). */
  inline?: boolean;
  /**
   * The escape action (§1.5 ruling): `useIdempotencyKey(...).discardUnsettled`, or a callback that
   * discards and re-runs the action when the notice is not inside the form. Inside a form, the
   * button then submits that form, which now takes a fresh key. Omitted → no action.
   */
  onSubmitAsNew?: () => void;
}

/**
 * Phase 92 residual §1.5: an earlier submit of this trust entry ended with its outcome unknown (the
 * response was lost, the page was reloaded mid-request, or the backend answered "still in
 * progress"). A plain resubmit of the same entry reuses its Idempotency-Key, so it cannot post
 * twice. The person checks the ledger; only if the entry is not there do they choose "Submit as a
 * new entry", which forgets the key and submits with a fresh one — a deliberate second step, never
 * automatic. A warning, never a block.
 */
export function UnsettledSubmitNotice({
  show,
  inline = false,
  onSubmitAsNew,
}: UnsettledSubmitNoticeProps) {
  if (!show) return null;

  function handleSubmitAsNew(event: MouseEvent<HTMLButtonElement>) {
    const form = event.currentTarget.form;
    onSubmitAsNew?.();
    form?.requestSubmit();
  }

  const action = onSubmitAsNew ? (
    <Button
      type="button"
      variant="outline"
      size="sm"
      className="hover:bg-muted"
      onClick={handleSubmitAsNew}
      data-testid="submit-as-new-entry"
    >
      {SUBMIT_AS_NEW_LABEL}
    </Button>
  ) : null;

  if (inline) {
    return (
      <span
        role="status"
        data-testid="unsettled-submit-notice"
        className="flex flex-wrap items-center gap-2 text-xs text-amber-700 dark:text-amber-300"
      >
        <span>{UNSETTLED_SUBMIT_NOTICE}</span>
        {action && <span>{SUBMIT_AS_NEW_PROMPT}</span>}
        {action}
      </span>
    );
  }
  return (
    <Alert variant="warning" role="status" data-testid="unsettled-submit-notice">
      <AlertTriangle />
      <AlertDescription>
        <p>{UNSETTLED_SUBMIT_NOTICE}</p>
        {action && (
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <span>{SUBMIT_AS_NEW_PROMPT}</span>
            {action}
          </div>
        )}
      </AlertDescription>
    </Alert>
  );
}
