"use client";

import { useState } from "react";
import { Button } from "@b2mash/ui/button";
import {
  fetchTakeOnBatch,
  revokeSignOff,
} from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import { formatDateTime } from "@/lib/format";
import type { TakeOnBatch, TakeOnSignOff } from "@/lib/types";
import { CommitTakeOnDialog } from "./commit-dialog";
import { SignOffPanel } from "./sign-off-panel";
import { TakeOnError } from "./take-on-feedback";

/** Product copy (657C, R-11): who signs, for a preparer who may not. */
export const SIGNED_FROM_HOME =
  "A partner or director signs off this take-on from their Home page.";

export function liveTrustSignOff(batch: TakeOnBatch): TakeOnSignOff | null {
  return batch.signOffs.find((s) => s.kind === "TRUST_SIGN_OFF") ?? null;
}

interface TrustSignOffStepProps {
  batch: TakeOnBatch;
  onBatchChange: (batch: TakeOnBatch) => void;
  /** The commit was accepted: the batch is COMMITTING. */
  onCommitted: (batch: TakeOnBatch) => void;
}

/**
 * The trust take-on's Sign-off step (Phase 93 657C, R-11). A caller who may sign (SIGN_OFF, or
 * WITHDRAW_SIGN_OFF as the live signer) gets the same panel as the Home sheet; anyone else sees
 * the sign-off status — and, as preparer or admin, "Withdraw sign-off" (REVOKE_SIGN_OFF, D-113) —
 * because the review endpoint is the signer's only (an admin would be refused). Below it, the last
 * commit failure and the commit itself once allowed. Every control follows allowedActions.
 */
export function TrustSignOffStep({ batch, onBatchChange, onCommitted }: TrustSignOffStepProps) {
  const allowed = new Set(batch.allowedActions);
  const mayUsePanel = allowed.has("SIGN_OFF") || allowed.has("WITHDRAW_SIGN_OFF");
  const live = liveTrustSignOff(batch);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** The panel signed or withdrew: re-read the batch so COMMIT and the status follow. */
  async function refetch() {
    setError(null);
    const result = await fetchTakeOnBatch(batch.id);
    if (!result.success || !result.data) {
      setError(result.error ?? "The take-on could not be re-read. Reload the page.");
      return;
    }
    onBatchChange(result.data);
  }

  async function revoke() {
    if (!live) return;
    setError(null);
    setBusy(true);
    try {
      const result = await revokeSignOff(batch.id, live.id);
      if (!result.success || !result.data) {
        setError(result.error ?? "The sign-off could not be withdrawn.");
        return;
      }
      onBatchChange(result.data);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-6" data-testid="take-on-signoff-step">
      <div className="space-y-1">
        <h3 className="text-lg font-medium text-slate-950 dark:text-slate-50">Sign-off</h3>
        <p className="text-sm text-slate-600 dark:text-slate-400">
          A partner or director reviews the trust opening position and signs it off before it is
          committed.
        </p>
      </div>

      {batch.status === "VALIDATED" && batch.lastError && (
        <TakeOnError message={batch.lastError} testId="take-on-last-error" />
      )}

      {mayUsePanel ? (
        <SignOffPanel batchId={batch.id} onChange={refetch} />
      ) : (
        <div
          className="space-y-3 rounded-lg border border-slate-200 px-4 py-3 dark:border-slate-800"
          data-testid="take-on-signoff-status"
        >
          <p className="text-sm text-slate-800 dark:text-slate-200">
            {live
              ? `Signed off by ${live.memberName} (${live.roleName}) on ${formatDateTime(live.signedAt)}.`
              : SIGNED_FROM_HOME}
          </p>
          {live && allowed.has("REVOKE_SIGN_OFF") && (
            <Button
              type="button"
              variant="outline"
              disabled={busy}
              onClick={revoke}
              data-testid="take-on-signoff-revoke"
            >
              {busy ? "Withdrawing…" : "Withdraw sign-off"}
            </Button>
          )}
        </div>
      )}

      {error && <TakeOnError message={error} testId="take-on-signoff-step-error" />}

      {allowed.has("COMMIT") && <CommitTakeOnDialog batch={batch} onCommitted={onCommitted} />}
    </div>
  );
}
