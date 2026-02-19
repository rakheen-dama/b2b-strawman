"use client";

import { useState, useEffect, useCallback } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { Check, Circle, Ban, Lock, XCircle, FileText } from "lucide-react";
import { formatDate } from "@/lib/format";
import type { ChecklistInstanceItemResponse, ChecklistItemStatus } from "@/lib/types";

type BadgeVariant = "success" | "warning" | "destructive" | "neutral";

const STATUS_CONFIG: Record<ChecklistItemStatus, { label: string; variant: BadgeVariant }> = {
  PENDING: { label: "Pending", variant: "neutral" },
  COMPLETED: { label: "Completed", variant: "success" },
  SKIPPED: { label: "Skipped", variant: "warning" },
  BLOCKED: { label: "Blocked", variant: "neutral" },
  CANCELLED: { label: "Cancelled", variant: "destructive" },
};

const STATUS_ICON: Record<ChecklistItemStatus, React.ReactNode> = {
  PENDING: <Circle className="size-4 text-slate-400" />,
  COMPLETED: <Check className="size-4 text-emerald-500" />,
  SKIPPED: <Ban className="size-4 text-amber-500" />,
  BLOCKED: <Lock className="size-4 text-slate-400" />,
  CANCELLED: <XCircle className="size-4 text-red-400" />,
};

interface ChecklistInstanceItemRowProps {
  item: ChecklistInstanceItemResponse;
  instanceItems: ChecklistInstanceItemResponse[];
  onComplete: (itemId: string, notes: string, documentId?: string) => Promise<void>;
  onSkip: (itemId: string, reason: string) => Promise<void>;
  onReopen: (itemId: string) => Promise<void>;
  isAdmin: boolean;
}

export function ChecklistInstanceItemRow({
  item,
  instanceItems,
  onComplete,
  onSkip,
  onReopen,
  isAdmin,
}: ChecklistInstanceItemRowProps) {
  const [isPending, setIsPending] = useState(false);
  const [showCompleteForm, setShowCompleteForm] = useState(false);
  const [showSkipForm, setShowSkipForm] = useState(false);
  const [notes, setNotes] = useState("");
  const [documentId, setDocumentId] = useState("");
  const [skipReason, setSkipReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);

  useEffect(() => {
    if (!error) return;
    const timer = setTimeout(clearError, 3000);
    return () => clearTimeout(timer);
  }, [error, clearError]);

  const config = STATUS_CONFIG[item.status] ?? { label: item.status, variant: "neutral" as const };
  const icon = STATUS_ICON[item.status] ?? <Circle className="size-4 text-slate-400" />;

  const isCancelled = item.status === "CANCELLED";
  const isBlocked = item.status === "BLOCKED";
  const isPendingStatus = item.status === "PENDING";
  const isCompleted = item.status === "COMPLETED";
  const isSkipped = item.status === "SKIPPED";

  const dependencyName = item.dependsOnItemId
    ? instanceItems.find((i) => i.id === item.dependsOnItemId)?.name ?? "Unknown item"
    : null;

  async function handleComplete() {
    setIsPending(true);
    setError(null);
    try {
      await onComplete(item.id, notes.trim(), documentId.trim() || undefined);
      setShowCompleteForm(false);
      setNotes("");
      setDocumentId("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to complete item");
    } finally {
      setIsPending(false);
    }
  }

  async function handleSkip() {
    setIsPending(true);
    setError(null);
    try {
      await onSkip(item.id, skipReason.trim());
      setShowSkipForm(false);
      setSkipReason("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to skip item");
    } finally {
      setIsPending(false);
    }
  }

  async function handleReopen() {
    setIsPending(true);
    setError(null);
    try {
      await onReopen(item.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to reopen item");
    } finally {
      setIsPending(false);
    }
  }

  return (
    <div
      className={cn(
        "rounded-lg border border-slate-200 p-3 dark:border-slate-800",
        isCancelled && "opacity-50",
      )}
    >
      <div className="flex items-start gap-3">
        {/* Status icon */}
        <div className="mt-0.5 shrink-0">{icon}</div>

        {/* Content */}
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="font-medium text-slate-900 dark:text-slate-100">{item.name}</span>
            <Badge variant={config.variant}>{config.label}</Badge>
            {item.required && (
              <span className="text-xs text-slate-500 dark:text-slate-400">Required</span>
            )}
          </div>

          {item.description && (
            <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
              {item.description}
            </p>
          )}

          {item.requiresDocument && (
            <p className="mt-1 flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400">
              <FileText className="size-3" />
              Requires document: {item.requiredDocumentLabel ?? "Document"}
            </p>
          )}

          {dependencyName && isBlocked && (
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Blocked by: {dependencyName}
            </p>
          )}

          {/* Completion metadata */}
          {(isCompleted || isSkipped) && (
            <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              {item.completedAt && <span>{formatDate(item.completedAt)}</span>}
              {item.notes && <span className="ml-2">&mdash; {item.notes}</span>}
            </div>
          )}

          {/* Error message */}
          {error && (
            <p className="mt-1 text-xs text-red-600 dark:text-red-400">{error}</p>
          )}

          {/* Complete form */}
          {showCompleteForm && (
            <div className="mt-3 space-y-2 rounded-md border border-slate-200 p-3 dark:border-slate-700">
              <Textarea
                placeholder="Notes (optional)"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                className="resize-none"
                rows={2}
              />
              {item.requiresDocument && (
                <Input
                  placeholder="Document ID (UUID)"
                  value={documentId}
                  onChange={(e) => setDocumentId(e.target.value)}
                />
              )}
              <div className="flex gap-2">
                <Button size="sm" onClick={handleComplete} disabled={isPending}>
                  {isPending ? "Saving..." : "Confirm"}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setShowCompleteForm(false);
                    setNotes("");
                    setDocumentId("");
                  }}
                  disabled={isPending}
                >
                  Cancel
                </Button>
              </div>
            </div>
          )}

          {/* Skip form */}
          {showSkipForm && (
            <div className="mt-3 space-y-2 rounded-md border border-slate-200 p-3 dark:border-slate-700">
              <Textarea
                placeholder="Reason for skipping"
                value={skipReason}
                onChange={(e) => setSkipReason(e.target.value)}
                className="resize-none"
                rows={2}
              />
              <div className="flex gap-2">
                <Button
                  size="sm"
                  onClick={handleSkip}
                  disabled={isPending || !skipReason.trim()}
                >
                  {isPending ? "Saving..." : "Confirm Skip"}
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => {
                    setShowSkipForm(false);
                    setSkipReason("");
                  }}
                  disabled={isPending}
                >
                  Cancel
                </Button>
              </div>
            </div>
          )}
        </div>

        {/* Actions */}
        {!isCancelled && !isBlocked && (
          <div className="flex shrink-0 gap-1">
            {isPendingStatus && isAdmin && !showCompleteForm && !showSkipForm && (
              <>
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setShowCompleteForm(true)}
                  disabled={isPending}
                >
                  Mark Complete
                </Button>
                {!item.required && (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setShowSkipForm(true)}
                    disabled={isPending}
                  >
                    Skip
                  </Button>
                )}
              </>
            )}
            {(isCompleted || isSkipped) && isAdmin && (
              <Button
                size="sm"
                variant="outline"
                onClick={handleReopen}
                disabled={isPending}
              >
                {isPending ? "Reopening..." : "Reopen"}
              </Button>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
