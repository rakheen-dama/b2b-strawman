"use client";

import { useState } from "react";
import { AlertTriangle } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { commitBatch } from "@/app/(app)/org/[slug]/settings/matters/take-on-actions";
import { TAKE_ON_DATASET_LABEL, takeOnLongDate, type TakeOnBatch } from "@/lib/types";
import { TakeOnError } from "./take-on-feedback";

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

/**
 * The X3 commit body: "{n} records will be created{, and {m} duplicates skipped}. This cannot be
 * undone." — n is the valid rows that are not duplicates of an existing record, m the duplicates
 * (both counted by the dry run).
 */
export function commitBody(batch: TakeOnBatch): string {
  const duplicates = batch.counts.duplicate;
  const created = Math.max(0, batch.counts.valid - duplicates);
  const skipped =
    duplicates > 0 ? `, and ${plural(duplicates, "duplicate", "duplicates")} skipped` : "";
  return `${plural(created, "record", "records")} will be created${skipped}. This cannot be undone.`;
}

/**
 * The X3 trust sentence of the commit dialog (657C): "The opening position of {account} at {cutover
 * date} will be posted and the account will not accept entries dated on or before that date."
 * Null for every other dataset.
 */
export function commitTrustSentence(batch: TakeOnBatch): string | null {
  if (batch.dataset !== "TRUST_POSITION") return null;
  const account = batch.trustAccountName ?? "the trust account";
  return `The opening position of ${account} at ${takeOnLongDate(batch.cutoverDate)} will be posted and the account will not accept entries dated on or before that date.`;
}

interface CommitTakeOnDialogProps {
  batch: TakeOnBatch;
  /** The batch as the commit answered it (COMMITTING). */
  onCommitted: (batch: TakeOnBatch) => void;
}

/**
 * The destructive commit confirm (§17.10 X3). Owns its trigger (frontend/CLAUDE.md "Dialog
 * Trigger Composition"). A refusal (409/400) shows the backend's text verbatim.
 */
export function CommitTakeOnDialog({ batch, onCommitted }: CommitTakeOnDialogProps) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const dataset = TAKE_ON_DATASET_LABEL[batch.dataset].toLowerCase();
  const trustSentence = commitTrustSentence(batch);

  function changeOpen(next: boolean) {
    if (busy) return;
    setOpen(next);
    if (next) setError(null);
  }

  async function confirm() {
    if (!batch.validationHash) return;
    setError(null);
    setBusy(true);
    try {
      const result = await commitBatch(batch.id, batch.validationHash);
      if (!result.success || !result.data) {
        setError(result.error ?? "The take-on could not be committed.");
        return;
      }
      setOpen(false);
      onCommitted(result.data);
    } finally {
      setBusy(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={changeOpen}>
      <Button
        type="button"
        variant="destructive"
        onClick={() => changeOpen(true)}
        disabled={!batch.validationHash}
        data-testid="take-on-commit"
      >
        Commit take-on
      </Button>
      <AlertDialogContent
        className="border-t-4 border-t-red-500"
        data-testid="take-on-commit-dialog"
      >
        <AlertDialogHeader>
          <div className="flex justify-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
              <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
            </div>
          </div>
          <AlertDialogTitle className="text-center">Commit {dataset} take-on?</AlertDialogTitle>
          <AlertDialogDescription className="text-center" data-testid="take-on-commit-body">
            {commitBody(batch)}
          </AlertDialogDescription>
          {trustSentence && (
            <p
              className="text-muted-foreground text-center text-sm"
              data-testid="take-on-commit-trust"
            >
              {trustSentence}
            </p>
          )}
        </AlertDialogHeader>
        {error && <TakeOnError message={error} testId="take-on-commit-error" />}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={busy}>
            Not yet
          </AlertDialogCancel>
          <Button
            variant="destructive"
            onClick={confirm}
            disabled={busy}
            data-testid="take-on-commit-confirm"
          >
            {busy ? "Committing…" : "Commit take-on"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
