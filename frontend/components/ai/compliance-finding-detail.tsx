"use client";

import { useState, useTransition } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  acknowledgeFindingAction,
  startProgressAction,
  resolveFindingAction,
  markFalsePositiveAction,
} from "@/app/(app)/org/[slug]/compliance/actions";
import {
  getSeverityBadgeVariant,
  getStatusBadgeVariant,
  formatCategoryName,
  formatStatusName,
} from "@/components/ai/compliance-finding-utils";
import type { ComplianceAuditFindingResponse } from "@/lib/api/compliance-audit";

interface ComplianceFindingDetailProps {
  finding: ComplianceAuditFindingResponse;
  reportId: string;
  slug: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onStatusChange: () => void;
  canReview: boolean;
}

export function ComplianceFindingDetail({
  finding,
  reportId,
  slug,
  open,
  onOpenChange,
  onStatusChange,
  canReview,
}: ComplianceFindingDetailProps) {
  const [confirmAction, setConfirmAction] = useState<"resolve" | "false_positive" | null>(null);
  const [resolutionNotes, setResolutionNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleAcknowledge() {
    setError(null);
    startTransition(async () => {
      const result = await acknowledgeFindingAction(slug, reportId, finding.id);
      if (result.success) {
        onStatusChange();
      } else {
        setError(result.error ?? "Failed to acknowledge finding.");
      }
    });
  }

  function handleStartProgress() {
    setError(null);
    startTransition(async () => {
      const result = await startProgressAction(slug, reportId, finding.id);
      if (result.success) {
        onStatusChange();
      } else {
        setError(result.error ?? "Failed to start progress.");
      }
    });
  }

  function handleResolve() {
    if (!resolutionNotes.trim()) {
      setError("Resolution notes are required.");
      return;
    }
    setError(null);
    startTransition(async () => {
      const result = await resolveFindingAction(slug, reportId, finding.id, resolutionNotes.trim());
      if (result.success) {
        setConfirmAction(null);
        setResolutionNotes("");
        onStatusChange();
      } else {
        setError(result.error ?? "Failed to resolve finding.");
      }
    });
  }

  function handleMarkFalsePositive() {
    if (!resolutionNotes.trim()) {
      setError("Resolution notes are required.");
      return;
    }
    setError(null);
    startTransition(async () => {
      const result = await markFalsePositiveAction(
        slug,
        reportId,
        finding.id,
        resolutionNotes.trim()
      );
      if (result.success) {
        setConfirmAction(null);
        setResolutionNotes("");
        onStatusChange();
      } else {
        setError(result.error ?? "Failed to mark as false positive.");
      }
    });
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="max-h-[80vh] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <div className="flex items-center gap-2">
              <Badge variant={getSeverityBadgeVariant(finding.severity)}>{finding.severity}</Badge>
              <Badge variant="neutral">{formatCategoryName(finding.category)}</Badge>
              <span className="font-mono text-xs text-slate-500 dark:text-slate-400">
                {finding.findingId}
              </span>
            </div>
            <DialogTitle className="text-lg">{finding.title}</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            {/* Description */}
            <div>
              <h4 className="mb-1 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                Description
              </h4>
              <p className="text-sm leading-relaxed text-slate-700 dark:text-slate-300">
                {finding.description}
              </p>
            </div>

            {/* Regulatory Basis */}
            {finding.regulatoryBasis && (
              <div>
                <h4 className="mb-1 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                  Regulatory Basis
                </h4>
                <p className="text-sm leading-relaxed text-slate-700 dark:text-slate-300">
                  {finding.regulatoryBasis}
                </p>
              </div>
            )}

            {/* Remediation */}
            {finding.remediation && (
              <div>
                <h4 className="mb-1 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                  Remediation
                </h4>
                <p className="text-sm leading-relaxed text-slate-700 dark:text-slate-300">
                  {finding.remediation}
                </p>
              </div>
            )}

            {/* Entity Link */}
            {finding.entityType && finding.entityId && (
              <div>
                <h4 className="mb-1 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                  Related Entity
                </h4>
                <a
                  href={
                    finding.entityType === "CUSTOMER"
                      ? `/org/${slug}/customers/${finding.entityId}`
                      : `/org/${slug}/projects/${finding.entityId}`
                  }
                  className="text-sm text-teal-600 underline hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
                >
                  View {finding.entityType.toLowerCase()}
                </a>
              </div>
            )}

            {/* Current Status */}
            <div>
              <h4 className="mb-1 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                Status
              </h4>
              <Badge variant={getStatusBadgeVariant(finding.status)}>
                {formatStatusName(finding.status)}
              </Badge>
            </div>

            {/* Error Message */}
            {error && (
              <div className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-800 dark:bg-red-950 dark:text-red-200">
                {error}
              </div>
            )}

            {/* Status Transition Buttons */}
            {canReview && finding.status === "OPEN" && (
              <Button variant="accent" size="sm" disabled={isPending} onClick={handleAcknowledge}>
                Acknowledge
              </Button>
            )}

            {canReview && finding.status === "ACKNOWLEDGED" && (
              <Button variant="accent" size="sm" disabled={isPending} onClick={handleStartProgress}>
                Start Progress
              </Button>
            )}

            {canReview && finding.status === "IN_PROGRESS" && (
              <div className="flex items-center gap-2">
                <Button
                  variant="accent"
                  size="sm"
                  disabled={isPending}
                  onClick={() => {
                    setConfirmAction("resolve");
                    setError(null);
                  }}
                >
                  Resolve
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={isPending}
                  onClick={() => {
                    setConfirmAction("false_positive");
                    setError(null);
                  }}
                >
                  Mark False Positive
                </Button>
              </div>
            )}

            {/* Resolution Info (terminal states) */}
            {(finding.status === "RESOLVED" || finding.status === "FALSE_POSITIVE") && (
              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 dark:border-slate-800 dark:bg-slate-900">
                <h4 className="mb-1 text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                  Resolution
                </h4>
                {finding.resolvedAt && (
                  <p className="text-xs text-slate-500 dark:text-slate-400">
                    Resolved on{" "}
                    {new Date(finding.resolvedAt).toLocaleDateString("en-ZA", {
                      year: "numeric",
                      month: "short",
                      day: "numeric",
                    })}
                    {finding.resolvedBy && ` by ${finding.resolvedBy}`}
                  </p>
                )}
                {finding.resolutionNotes && (
                  <p className="mt-1 text-sm text-slate-700 dark:text-slate-300">
                    {finding.resolutionNotes}
                  </p>
                )}
              </div>
            )}
          </div>
        </DialogContent>
      </Dialog>

      {/* Resolve Confirmation Dialog */}
      <AlertDialog
        open={confirmAction === "resolve"}
        onOpenChange={(open) => {
          if (!open) {
            setConfirmAction(null);
            setResolutionNotes("");
            setError(null);
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Resolve Finding</AlertDialogTitle>
            <AlertDialogDescription>
              Mark finding &quot;{finding.findingId}&quot; as resolved. Resolution notes are
              required.
            </AlertDialogDescription>
          </AlertDialogHeader>
          {error && confirmAction === "resolve" && (
            <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
          )}
          <div className="py-2">
            <Textarea
              placeholder="Describe how the finding was resolved..."
              value={resolutionNotes}
              onChange={(e) => setResolutionNotes(e.target.value)}
              rows={3}
            />
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction variant="accent" disabled={isPending} onClick={handleResolve}>
              Resolve
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* False Positive Confirmation Dialog */}
      <AlertDialog
        open={confirmAction === "false_positive"}
        onOpenChange={(open) => {
          if (!open) {
            setConfirmAction(null);
            setResolutionNotes("");
            setError(null);
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Mark as False Positive</AlertDialogTitle>
            <AlertDialogDescription>
              Mark finding &quot;{finding.findingId}&quot; as a false positive. Resolution notes are
              required.
            </AlertDialogDescription>
          </AlertDialogHeader>
          {error && confirmAction === "false_positive" && (
            <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
          )}
          <div className="py-2">
            <Textarea
              placeholder="Explain why this is a false positive..."
              value={resolutionNotes}
              onChange={(e) => setResolutionNotes(e.target.value)}
              rows={3}
            />
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction disabled={isPending} onClick={handleMarkFalsePositive}>
              Mark False Positive
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
