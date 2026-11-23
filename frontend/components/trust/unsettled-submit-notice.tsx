import { AlertTriangle } from "lucide-react";
import { Alert, AlertDescription } from "@/components/ui/alert";

/** Phase 92 residual §1.5 wording (ruled 2026-11-23). */
export const UNSETTLED_SUBMIT_NOTICE =
  "A submit of this entry may already have been recorded — check the ledger before submitting again.";

interface UnsettledSubmitNoticeProps {
  /** `useIdempotencyKey(...).unsettled`: an earlier submit of this form has an unknown outcome. */
  show: boolean;
  /** A one-line inline form, for a row action with no dialog (approve). */
  inline?: boolean;
}

/**
 * Phase 92 residual §1.5: an earlier submit of this trust entry ended with its outcome unknown (the
 * response was lost, or the page was reloaded mid-request). A resubmit of the same entry reuses its
 * Idempotency-Key, so it cannot post twice — but the person should check the ledger first. A
 * warning, never a block.
 */
export function UnsettledSubmitNotice({ show, inline = false }: UnsettledSubmitNoticeProps) {
  if (!show) return null;
  if (inline) {
    return (
      <span
        role="status"
        data-testid="unsettled-submit-notice"
        className="text-xs text-amber-700 dark:text-amber-300"
      >
        {UNSETTLED_SUBMIT_NOTICE}
      </span>
    );
  }
  return (
    <Alert variant="warning" role="status" data-testid="unsettled-submit-notice">
      <AlertTriangle />
      <AlertDescription>{UNSETTLED_SUBMIT_NOTICE}</AlertDescription>
    </Alert>
  );
}
