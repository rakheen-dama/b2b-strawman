"use client";

import { useState, useCallback } from "react";
import { X, Check, RotateCcw } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { Badge } from "@b2mash/ui/badge";
import { Textarea } from "@/components/ui/textarea";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { AI_QUEUE_STRINGS } from "@/lib/constants/ai-queue-strings";
import {
  approveInvocation,
  rejectInvocation,
  retryInvocation,
} from "@/lib/api/assistant-specialists";
import { BillingDiff } from "@/components/assistant/specialists/billing-diff";
import { IntakeFieldDiff } from "@/components/assistant/specialists/intake-field-diff";
import { InboxSummaryPreview } from "./inbox-summary-preview";
import type { InvocationDetail } from "@/lib/api/ai-invocations";

export interface InvocationDrawerProps {
  open: boolean;
  onClose: () => void;
  invocation: InvocationDetail | null;
  onActionComplete: () => void;
}

export function InvocationDrawer({
  open,
  onClose,
  invocation,
  onActionComplete,
}: InvocationDrawerProps) {
  const [rejectReason, setRejectReason] = useState("");
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [inFlight, setInFlight] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleApprove = useCallback(async () => {
    if (!invocation || inFlight) return;
    setInFlight(true);
    setError(null);
    try {
      await approveInvocation(invocation.id);
      onActionComplete();
      onClose();
    } catch {
      setError("Failed to approve. Please try again.");
    } finally {
      setInFlight(false);
    }
  }, [invocation, inFlight, onActionComplete, onClose]);

  const handleReject = useCallback(async () => {
    if (!invocation || inFlight || !rejectReason.trim()) return;
    setInFlight(true);
    setError(null);
    try {
      await rejectInvocation(invocation.id, rejectReason.trim());
      setRejectReason("");
      setShowRejectForm(false);
      onActionComplete();
      onClose();
    } catch {
      setError("Failed to reject. Please try again.");
    } finally {
      setInFlight(false);
    }
  }, [invocation, inFlight, rejectReason, onActionComplete, onClose]);

  const handleRetry = useCallback(async () => {
    if (!invocation || inFlight) return;
    setInFlight(true);
    setError(null);
    try {
      await retryInvocation(invocation.id);
      onActionComplete();
      onClose();
    } catch {
      setError("Failed to retry. Please try again.");
    } finally {
      setInFlight(false);
    }
  }, [invocation, inFlight, onActionComplete, onClose]);

  if (!invocation) return null;

  const isPending = invocation.status === "PENDING_APPROVAL";
  const isFailed = invocation.status === "FAILED";

  return (
    <Sheet open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <SheetContent className="overflow-y-auto sm:max-w-lg" data-testid="invocation-drawer">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2">
            {AI_QUEUE_STRINGS.specialist[invocation.specialistId] ?? invocation.specialistId}
            <Badge variant="outline" className="text-xs">
              {AI_QUEUE_STRINGS.status[invocation.status] ?? invocation.status}
            </Badge>
          </SheetTitle>
        </SheetHeader>

        <div className="mt-6 space-y-6">
          {/* Metadata */}
          <div className="grid grid-cols-2 gap-2 text-sm">
            <span className="text-slate-500">Context</span>
            <span className="capitalize">
              {invocation.contextEntityType} / {invocation.contextEntityId.slice(0, 8)}...
            </span>
            <span className="text-slate-500">Source</span>
            <span className="capitalize">{invocation.invokedBy.toLowerCase()}</span>
            <span className="text-slate-500">Created</span>
            <span>{new Date(invocation.createdAt).toLocaleString()}</span>
            {invocation.promptVersion && (
              <>
                <span className="text-slate-500">Prompt version</span>
                <span>{invocation.promptVersion}</span>
              </>
            )}
            {invocation.errorMessage && (
              <>
                <span className="text-slate-500">Error</span>
                <span className="text-red-600">{invocation.errorMessage}</span>
              </>
            )}
          </div>

          {/* Proposed Output — dispatch by payload type */}
          {invocation.proposedOutput && (
            <div className="space-y-2">
              <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">
                {AI_QUEUE_STRINGS.drawer.proposedOutput}
              </h3>
              {renderDiffViewer(invocation)}
            </div>
          )}

          {/* Actions */}
          {error && <p className="text-sm text-red-600">{error}</p>}

          {isPending && (
            <div className="flex items-center gap-2 border-t pt-4">
              <Button variant="default" size="sm" disabled={inFlight} onClick={handleApprove}>
                <Check className="mr-1 size-4" />
                {AI_QUEUE_STRINGS.drawer.approve}
              </Button>
              {!showRejectForm ? (
                <Button
                  variant="outline"
                  size="sm"
                  disabled={inFlight}
                  onClick={() => setShowRejectForm(true)}
                >
                  <X className="mr-1 size-4" />
                  {AI_QUEUE_STRINGS.drawer.reject}
                </Button>
              ) : (
                <div className="flex flex-1 items-center gap-2">
                  <Textarea
                    value={rejectReason}
                    onChange={(e) => setRejectReason(e.target.value)}
                    placeholder="Reason for rejection..."
                    rows={2}
                    className="flex-1"
                  />
                  <Button
                    variant="destructive"
                    size="sm"
                    disabled={inFlight || !rejectReason.trim()}
                    onClick={handleReject}
                  >
                    Confirm
                  </Button>
                </div>
              )}
            </div>
          )}

          {isFailed && (
            <div className="border-t pt-4">
              <Button variant="outline" size="sm" disabled={inFlight} onClick={handleRetry}>
                <RotateCcw className="mr-1 size-4" />
                {AI_QUEUE_STRINGS.drawer.retry}
              </Button>
            </div>
          )}
        </div>
      </SheetContent>
    </Sheet>
  );
}

function renderDiffViewer(invocation: InvocationDetail) {
  const output = invocation.proposedOutput;
  if (!output) return null;

  const kind = output.kind as string | undefined;

  if (kind === "BillingPolishPayload" || kind === "BillingGroupingPayload") {
    return (
      <BillingDiff
        invocationId={invocation.id}
        kind={kind}
        invoiceId={(output.invoiceId as string) ?? ""}
        edits={
          (output.edits as Array<{ timeEntryId: string; beforeText: string; afterText: string }>) ??
          []
        }
        groups={
          (output.groups as Array<{
            description: string;
            hours: number;
            sourceTimeEntryIds: string[];
          }>) ?? []
        }
      />
    );
  }

  if (kind === "IntakeExtractionPayload") {
    return (
      <IntakeFieldDiff
        invocationId={invocation.id}
        contextEntityType={(output.contextEntityType as string) ?? ""}
        contextEntityId={(output.contextEntityId as string) ?? ""}
        proposedFields={(output.proposedFields as Record<string, unknown>) ?? {}}
        extractionPath={(output.extractionPath as "TEXT" | "VISION") ?? "TEXT"}
        popiaFlaggedFields={(output.popiaFlaggedFields as string[]) ?? []}
        validationFlags={(output.validationFlags as string[]) ?? []}
      />
    );
  }

  // Inbox or unknown — show preview
  return <InboxSummaryPreview invocationId={invocation.id} proposedOutput={output} />;
}
