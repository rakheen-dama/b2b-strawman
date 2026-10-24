"use client";

import { useRef, useState, type RefObject } from "react";
import { Loader2, Share2 } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@b2mash/ui/label";
import { shareGeneratedDocumentAction } from "@/app/(app)/org/[slug]/settings/documents/_tabs/template-generation-actions";
import { CLIENT_EMAIL_CAVEAT } from "@/lib/client-notification-copy";
import type { GeneratedDocumentListResponse } from "@/lib/types";

interface ShareGeneratedDocumentDialogProps {
  document: GeneratedDocumentListResponse;
  onShared: (updated: GeneratedDocumentListResponse, notifyClient: boolean) => void;
  /**
   * Stable element to focus when the dialog closes and the owned trigger has left the DOM
   * (a shared row no longer offers Share, so the trigger unmounts on success).
   */
  fallbackFocusRef?: RefObject<HTMLElement | null>;
}

/**
 * Phase 88 U13 / ADR-350 — "Share with client" (INTERNAL → PORTAL, optional email). The dialog
 * owns its trigger button (frontend/CLAUDE.md "Dialog Trigger Composition"): the row already
 * hosts the Delete `AlertDialogTrigger asChild`.
 */
export function ShareGeneratedDocumentDialog({
  document: doc,
  onShared,
  fallbackFocusRef,
}: ShareGeneratedDocumentDialogProps) {
  const [open, setOpen] = useState(false);
  const [notifyClient, setNotifyClient] = useState(false);
  const [isSharing, setIsSharing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  function reset() {
    setNotifyClient(false);
    setError(null);
  }

  function handleOpenChange(next: boolean) {
    if (next) {
      setOpen(true);
    } else if (!isSharing) {
      // Ignore Escape / outside-click while in flight — a late error must land on an open dialog.
      setOpen(false);
      reset();
    }
  }

  async function handleShare() {
    setError(null);
    setIsSharing(true);
    try {
      const result = await shareGeneratedDocumentAction(doc.id, notifyClient);
      if (result.success && result.data) {
        setOpen(false);
        reset();
        onShared(result.data, notifyClient);
      } else {
        setError(result.error ?? "Failed to share document.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSharing(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <Button
        ref={triggerRef}
        type="button"
        variant="ghost"
        size="icon"
        className="size-8"
        title="Share with client"
        aria-label="Share with client"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen(true)}
        data-testid={`share-document-btn-${doc.id}`}
      >
        <Share2 className="size-4" />
      </Button>
      <DialogContent
        className="sm:max-w-md"
        data-testid="share-document-dialog"
        onCloseAutoFocus={(event) => {
          // There is no DialogTrigger, so Radix would restore focus to the opener — which has
          // left the DOM after a successful share. Fall back to a stable element instead.
          const target = triggerRef.current?.isConnected
            ? triggerRef.current
            : fallbackFocusRef?.current;
          if (target?.isConnected) {
            event.preventDefault();
            target.focus();
          }
        }}
      >
        <DialogHeader>
          <DialogTitle>Share with client</DialogTitle>
          <DialogDescription>
            The client will be able to see{" "}
            <span className="font-medium text-slate-900 dark:text-slate-100">
              {doc.templateName}
            </span>{" "}
            when they sign in to the client portal.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-row items-start gap-3">
          <Checkbox
            id={`share-document-notify-${doc.id}`}
            checked={notifyClient}
            onCheckedChange={(v) => setNotifyClient(v === true)}
            disabled={isSharing}
            data-testid="share-document-notify-checkbox"
          />
          <div className="space-y-0.5">
            <Label htmlFor={`share-document-notify-${doc.id}`} className="text-sm font-medium">
              Email the client now
            </Label>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Requests an email with a link to the client&apos;s portal contact.
            </p>
            <p className="text-xs text-slate-500 dark:text-slate-400">{CLIENT_EMAIL_CAVEAT}</p>
          </div>
        </div>

        {error && (
          <p role="alert" className="text-destructive text-sm">
            {error}
          </p>
        )}

        <DialogFooter>
          <Button
            type="button"
            variant="plain"
            onClick={() => handleOpenChange(false)}
            disabled={isSharing}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleShare}
            disabled={isSharing}
            data-testid="share-document-confirm-btn"
          >
            {isSharing ? (
              <>
                <Loader2 className="mr-1.5 size-4 animate-spin" />
                Sharing...
              </>
            ) : (
              "Share with client"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
