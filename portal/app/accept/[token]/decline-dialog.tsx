"use client";

import { useState, type RefObject } from "react";
import { Loader2 } from "lucide-react";
import { Dialog } from "radix-ui";
import { Button } from "@b2mash/ui/button";
import { Label } from "@b2mash/ui/label";

const MAX_CHARS = 2000; // backend DeclineSubmission @Size(max = 2000)
const WARN_THRESHOLD = 1800;

interface DeclineDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  orgName: string;
  isSubmitting: boolean;
  error: string | null;
  onConfirm: (reason: string) => void;
  /** Opener to refocus on close — there is no Dialog.Trigger, so Radix cannot restore focus itself. */
  returnFocusRef?: RefObject<HTMLButtonElement | null>;
}

/**
 * Controlled "Decline this document?" dialog (Phase 88 U23). The page owns both opener
 * buttons (desktop + mobile sticky bar), so there is no Dialog.Trigger here.
 */
export function DeclineDialog({
  open,
  onOpenChange,
  orgName,
  isSubmitting,
  error,
  onConfirm,
  returnFocusRef,
}: DeclineDialogProps) {
  const [reason, setReason] = useState("");

  return (
    <Dialog.Root
      open={open}
      onOpenChange={(next) => {
        if (!isSubmitting) onOpenChange(next);
      }}
    >
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-slate-950/25 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=open]:fade-in data-[state=closed]:fade-out" />
        <Dialog.Content
          className="fixed top-1/2 left-1/2 z-50 grid w-full max-w-[calc(100%-2rem)] -translate-x-1/2 -translate-y-1/2 gap-4 rounded-xl border border-slate-200 bg-white p-6 shadow-lg sm:max-w-lg"
          onCloseAutoFocus={(e) => {
            if (returnFocusRef?.current) {
              e.preventDefault();
              returnFocusRef.current.focus();
            }
          }}
        >
          <Dialog.Title className="font-display text-lg font-semibold text-slate-900">
            Decline this document?
          </Dialog.Title>
          <Dialog.Description className="text-sm text-slate-600">
            {orgName} will be told that you declined. Adding a reason is
            optional.
          </Dialog.Description>
          <form
            id="decline-form"
            onSubmit={(e) => {
              e.preventDefault();
              onConfirm(reason);
            }}
            className="flex flex-col gap-2"
          >
            <Label htmlFor="decline-reason">Reason (optional)</Label>
            <textarea
              id="decline-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={4}
              maxLength={MAX_CHARS}
              disabled={isSubmitting}
              className="w-full min-w-0 resize-y rounded-md border border-slate-200 bg-transparent px-3 py-2 text-sm outline-none placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-[3px] focus-visible:ring-slate-500/30 disabled:cursor-not-allowed disabled:opacity-50"
            />
            {reason.length > WARN_THRESHOLD && (
              <span className="text-right text-xs text-slate-400">
                {reason.length}/{MAX_CHARS}
              </span>
            )}
            {error && (
              <p className="text-sm text-red-600" role="alert">
                {error}
              </p>
            )}
          </form>
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              disabled={isSubmitting}
              onClick={() => onOpenChange(false)}
              className="min-h-11 md:min-h-9"
            >
              Cancel
            </Button>
            <Button
              type="submit"
              form="decline-form"
              variant="destructive"
              disabled={isSubmitting}
              className="min-h-11 md:min-h-9"
            >
              {isSubmitting ? (
                <>
                  <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  Declining...
                </>
              ) : (
                "Decline document"
              )}
            </Button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
