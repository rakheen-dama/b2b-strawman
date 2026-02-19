"use client";

import { useState, useTransition } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { DeletionConfirmDialog } from "@/components/compliance/DeletionConfirmDialog";
import {
  updateRequestStatus,
  generateExport,
  getExportUrl,
} from "@/app/(app)/org/[slug]/compliance/requests/actions";
import type { DataRequestResponse } from "@/lib/types";

interface DataRequestActionsProps {
  request: DataRequestResponse;
  slug: string;
}

export function DataRequestActions({ request, slug }: DataRequestActionsProps) {
  const [isPending, startTransition] = useTransition();
  const [error, setError] = useState<string | null>(null);
  const [rejectionReason, setRejectionReason] = useState("");
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [deletionDialogOpen, setDeletionDialogOpen] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  function handleAction(action: string, reason?: string) {
    setError(null);
    setSuccessMessage(null);
    startTransition(async () => {
      try {
        const result = await updateRequestStatus(slug, request.id, action, reason);
        if (!result.success) {
          setError(result.error ?? "Action failed.");
        }
      } catch {
        setError("An unexpected error occurred.");
      }
    });
  }

  function handleGenerateExport() {
    setError(null);
    setSuccessMessage(null);
    startTransition(async () => {
      try {
        const result = await generateExport(slug, request.id);
        if (result.success) {
          setSuccessMessage("Export generated successfully.");
        } else {
          setError(result.error ?? "Failed to generate export.");
        }
      } catch {
        setError("An unexpected error occurred.");
      }
    });
  }

  function handleDownloadExport() {
    setError(null);
    startTransition(async () => {
      try {
        const result = await getExportUrl(request.id);
        if (result.success && result.url) {
          window.open(result.url, "_blank");
        } else {
          setError(result.error ?? "Failed to get download URL.");
        }
      } catch {
        setError("An unexpected error occurred.");
      }
    });
  }

  function handleRejectSubmit() {
    if (!rejectionReason.trim()) return;
    handleAction("REJECT", rejectionReason.trim());
    setShowRejectForm(false);
    setRejectionReason("");
  }

  const { status, requestType } = request;

  if (status === "COMPLETED" || status === "REJECTED") {
    return (
      <div className="space-y-2">
        {request.hasExport && (
          <Button variant="outline" size="sm" onClick={handleDownloadExport} disabled={isPending}>
            Download Export
          </Button>
        )}
        {error && <p className="text-sm text-destructive">{error}</p>}
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {status === "RECEIVED" && (
          <Button
            size="sm"
            onClick={() => handleAction("START_PROCESSING")}
            disabled={isPending}
          >
            {isPending ? "Processing..." : "Start Processing"}
          </Button>
        )}

        {status === "IN_PROGRESS" && requestType === "ACCESS" && (
          <Button
            variant="outline"
            size="sm"
            onClick={handleGenerateExport}
            disabled={isPending}
          >
            {isPending ? "Generating..." : "Generate Export"}
          </Button>
        )}

        {status === "IN_PROGRESS" && requestType === "DELETION" && (
          <Button
            variant="destructive"
            size="sm"
            onClick={() => setDeletionDialogOpen(true)}
            disabled={isPending}
          >
            Execute Deletion
          </Button>
        )}

        {status === "IN_PROGRESS" && (requestType === "ACCESS" || requestType === "CORRECTION" || requestType === "OBJECTION") && (
          <Button
            size="sm"
            onClick={() => handleAction("COMPLETE")}
            disabled={isPending}
          >
            {isPending ? "Completing..." : "Complete"}
          </Button>
        )}

        {status === "IN_PROGRESS" && !showRejectForm && (
          <Button
            variant="outline"
            size="sm"
            onClick={() => setShowRejectForm(true)}
            disabled={isPending}
          >
            Reject
          </Button>
        )}

        {request.hasExport && (
          <Button variant="outline" size="sm" onClick={handleDownloadExport} disabled={isPending}>
            Download Export
          </Button>
        )}
      </div>

      {showRejectForm && (
        <div className="flex items-center gap-2">
          <Input
            value={rejectionReason}
            onChange={(e) => setRejectionReason(e.target.value)}
            placeholder="Rejection reason (required)"
            className="max-w-sm"
          />
          <Button
            variant="destructive"
            size="sm"
            onClick={handleRejectSubmit}
            disabled={isPending || !rejectionReason.trim()}
          >
            Confirm Reject
          </Button>
          <Button
            variant="plain"
            size="sm"
            onClick={() => {
              setShowRejectForm(false);
              setRejectionReason("");
            }}
            disabled={isPending}
          >
            Cancel
          </Button>
        </div>
      )}

      {successMessage && (
        <p className="text-sm text-teal-600 dark:text-teal-400">{successMessage}</p>
      )}
      {error && <p className="text-sm text-destructive">{error}</p>}

      <DeletionConfirmDialog
        open={deletionDialogOpen}
        onOpenChange={setDeletionDialogOpen}
        slug={slug}
        requestId={request.id}
        customerName={request.customerName}
      />
    </div>
  );
}
