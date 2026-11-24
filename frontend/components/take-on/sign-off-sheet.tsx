"use client";

import { useRef, useState, type ReactNode } from "react";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { takeOnLongDate } from "@/lib/types";
import { SignOffPanel } from "./sign-off-panel";

/** X3 "Signer entry point": the sheet's title. */
export function signOffSheetTitle(account: string | null, cutoverDate: string | null): string {
  return `Sign off the trust take-on of ${account ?? "the trust account"} at ${takeOnLongDate(cutoverDate)}`;
}

interface SignOffSheetProps {
  batchId: string;
  trustAccountName: string | null;
  cutoverDate: string | null;
  /** The trigger's content (the sheet owns its trigger — "Dialog Trigger Composition"). */
  triggerLabel: ReactNode;
  triggerClassName?: string;
  triggerTestId?: string;
  /** The sheet closed after a sign-off or a withdrawal in it: the caller re-reads its list. */
  onClosedAfterChange?: () => void;
}

/**
 * The signer's sheet (Phase 93 657C, FD-36): "Sign off the trust take-on of {account} at {cutover
 * date}", the sign-off panel inside. It stays open after signing, so the signer sees the sign-off
 * and can still "Withdraw my sign-off"; closing it after a change tells the caller to re-read the
 * queue (the signed take-on leaves it). The panel mounts only while open, so nothing is read for
 * a closed sheet.
 */
export function SignOffSheet({
  batchId,
  trustAccountName,
  cutoverDate,
  triggerLabel,
  triggerClassName,
  triggerTestId,
  onClosedAfterChange,
}: SignOffSheetProps) {
  const [open, setOpen] = useState(false);
  const changed = useRef(false);

  function changeOpen(next: boolean) {
    setOpen(next);
    if (next) {
      changed.current = false;
    } else if (changed.current) {
      changed.current = false;
      onClosedAfterChange?.();
    }
  }

  return (
    <Sheet open={open} onOpenChange={changeOpen}>
      <button
        type="button"
        onClick={() => changeOpen(true)}
        className={triggerClassName}
        data-testid={triggerTestId}
      >
        {triggerLabel}
      </button>
      <SheetContent
        side="right"
        className="w-full overflow-y-auto sm:max-w-3xl"
        data-testid="takeon-signoff-sheet"
      >
        <SheetHeader>
          <SheetTitle data-testid="takeon-signoff-sheet-title">
            {signOffSheetTitle(trustAccountName, cutoverDate)}
          </SheetTitle>
          <SheetDescription>
            Review the trust opening position, then make the declaration to sign it off.
          </SheetDescription>
        </SheetHeader>
        <div className="px-4 pb-6">
          {open && (
            <SignOffPanel
              batchId={batchId}
              onChange={() => {
                changed.current = true;
              }}
            />
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}
