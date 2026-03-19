"use client";

import { useState, useTransition } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Loader2 } from "lucide-react";
import { updateDsarStatus } from "@/app/(app)/org/[slug]/settings/data-protection/requests/actions";
import type { DsarRequest } from "@/lib/types/data-protection";

// --- Types ---

type DsarStatus = DsarRequest["status"];
type DeadlineStatus = DsarRequest["deadlineStatus"];

// --- Badge config ---

const STATUS_BADGE: Record<
  DsarStatus,
  {
    label: string;
    variant: "success" | "destructive" | "warning" | "neutral";
  }
> = {
  RECEIVED: { label: "Received", variant: "neutral" },
  IN_PROGRESS: { label: "In Progress", variant: "neutral" },
  COMPLETED: { label: "Completed", variant: "success" },
  REJECTED: { label: "Rejected", variant: "destructive" },
};

const REQUEST_TYPE_LABEL: Record<DsarRequest["requestType"], string> = {
  ACCESS: "Access",
  DELETION: "Deletion",
  CORRECTION: "Correction",
  OBJECTION: "Objection",
  PORTABILITY: "Portability",
};

// --- Deadline display helper ---

function DeadlineCell({
  deadline,
  deadlineStatus,
  status,
}: {
  deadline: string;
  deadlineStatus: DeadlineStatus;
  status: DsarStatus;
}) {
  const isTerminal = status === "COMPLETED" || status === "REJECTED";

  if (isTerminal) {
    return (
      <span className="text-sm text-slate-500 dark:text-slate-400">
        {deadline}
      </span>
    );
  }

  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const deadlineDate = new Date(deadline + "T00:00:00");
  const diffDays = Math.ceil(
    (deadlineDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24),
  );

  if (deadlineStatus === "OVERDUE") {
    return (
      <span className="text-sm font-medium text-red-600 dark:text-red-400">
        Overdue
      </span>
    );
  }

  if (deadlineStatus === "DUE_SOON") {
    return (
      <span className="text-sm text-amber-600 dark:text-amber-400">
        {diffDays === 0 ? "Due today" : `${diffDays}d left`}
      </span>
    );
  }

  return (
    <span className="text-sm text-slate-700 dark:text-slate-300">
      {diffDays > 0 ? `${diffDays}d left` : deadline}
    </span>
  );
}

// --- Resolution Notes Dialog ---

interface ResolutionDialogProps {
  open: boolean;
  title: string;
  onClose: () => void;
  onConfirm: (notes: string) => void;
  isSubmitting: boolean;
}

function ResolutionDialog({
  open,
  title,
  onClose,
  onConfirm,
  isSubmitting,
}: ResolutionDialogProps) {
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);

  function handleConfirm() {
    if (!notes.trim()) {
      setError("Resolution notes are required.");
      return;
    }
    onConfirm(notes.trim());
  }

  function handleClose() {
    setNotes("");
    setError(null);
    onClose();
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => {
        if (!o) handleClose();
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>
            Please provide resolution notes before proceeding.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3 py-2">
          <Textarea
            placeholder="Enter resolution notes..."
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={4}
            maxLength={2000}
          />
          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>
        <DialogFooter>
          <Button
            type="button"
            variant="plain"
            onClick={handleClose}
            disabled={isSubmitting}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleConfirm}
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-1.5 size-4 animate-spin" />
                Saving...
              </>
            ) : (
              "Confirm"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// --- Row Action Buttons ---

interface RowActionsProps {
  request: DsarRequest;
  slug: string;
  onActionComplete: () => void;
}

function RowActions({ request, slug, onActionComplete }: RowActionsProps) {
  const [, startTransition] = useTransition();
  const [actionError, setActionError] = useState<string | null>(null);
  const [resolutionDialogOpen, setResolutionDialogOpen] = useState(false);
  const [pendingAction, setPendingAction] = useState<
    "COMPLETE" | "REJECT" | null
  >(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  function handleSimpleAction(action: "START_PROCESSING") {
    setActionError(null);
    startTransition(async () => {
      const result = await updateDsarStatus(slug, request.id, action);
      if (!result.success) {
        setActionError(result.error ?? "Action failed.");
      } else {
        onActionComplete();
      }
    });
  }

  function openResolutionDialog(action: "COMPLETE" | "REJECT") {
    setPendingAction(action);
    setResolutionDialogOpen(true);
  }

  async function handleResolutionConfirm(notes: string) {
    if (!pendingAction) return;
    setIsSubmitting(true);
    setActionError(null);
    try {
      const result = await updateDsarStatus(
        slug,
        request.id,
        pendingAction,
        notes,
      );
      if (!result.success) {
        setActionError(result.error ?? "Action failed.");
      } else {
        setResolutionDialogOpen(false);
        setPendingAction(null);
        onActionComplete();
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  const { status } = request;
  const isTerminal = status === "COMPLETED" || status === "REJECTED";

  if (isTerminal) {
    return <span className="text-xs text-slate-400">&mdash;</span>;
  }

  return (
    <div className="flex items-center gap-2">
      {status === "RECEIVED" && (
        <Button
          size="sm"
          variant="outline"
          onClick={() => handleSimpleAction("START_PROCESSING")}
          aria-label="Mark as processing"
        >
          Mark Processing
        </Button>
      )}
      {status === "IN_PROGRESS" && (
        <>
          <Button
            size="sm"
            variant="soft"
            onClick={() => openResolutionDialog("COMPLETE")}
            aria-label="Complete request"
          >
            Complete
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => openResolutionDialog("REJECT")}
            aria-label="Deny request"
          >
            Deny
          </Button>
        </>
      )}
      {actionError && (
        <span className="text-xs text-red-600 dark:text-red-400" role="alert">
          {actionError}
        </span>
      )}
      <ResolutionDialog
        open={resolutionDialogOpen}
        title={
          pendingAction === "COMPLETE" ? "Complete Request" : "Deny Request"
        }
        onClose={() => {
          setResolutionDialogOpen(false);
          setPendingAction(null);
        }}
        onConfirm={handleResolutionConfirm}
        isSubmitting={isSubmitting}
      />
    </div>
  );
}

// --- Main Table Component ---

interface DsarRequestsTableProps {
  requests: DsarRequest[];
  slug: string;
}

export function DsarRequestsTable({
  requests,
  slug,
}: DsarRequestsTableProps) {
  if (requests.length === 0) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white p-8 text-center dark:border-slate-800 dark:bg-slate-950">
        <p className="text-sm text-slate-500 dark:text-slate-400">
          No DSAR requests found. Use the &quot;Log new request&quot; button to
          record one.
        </p>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-slate-200 bg-white dark:border-slate-800 dark:bg-slate-950">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-800">
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Subject / Customer
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Type
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Status
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Received
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Deadline
              </th>
              <th className="px-4 py-3 text-left font-medium text-slate-600 dark:text-slate-400">
                Actions
              </th>
            </tr>
          </thead>
          <tbody>
            {requests.map((req) => {
              const statusBadge = STATUS_BADGE[req.status];
              const deadlineVariant:
                | "success"
                | "destructive"
                | "warning"
                | "neutral" =
                req.deadlineStatus === "OVERDUE"
                  ? "destructive"
                  : req.deadlineStatus === "DUE_SOON"
                    ? "warning"
                    : "neutral";

              return (
                <tr
                  key={req.id}
                  className="border-b border-slate-100 last:border-0 dark:border-slate-800"
                >
                  <td className="px-4 py-3">
                    <div>
                      <span className="font-medium text-slate-900 dark:text-slate-100">
                        {req.subjectName ?? req.customerName ?? "\u2014"}
                      </span>
                      {req.customerName &&
                        req.subjectName &&
                        req.customerName !== req.subjectName && (
                          <p className="text-xs text-slate-500 dark:text-slate-400">
                            {req.customerName}
                          </p>
                        )}
                      {req.subjectEmail && (
                        <p className="text-xs text-slate-500 dark:text-slate-400">
                          {req.subjectEmail}
                        </p>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant="neutral">
                      {REQUEST_TYPE_LABEL[req.requestType]}
                    </Badge>
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant={statusBadge.variant}>
                      {statusBadge.label}
                    </Badge>
                  </td>
                  <td className="px-4 py-3 text-slate-700 dark:text-slate-300">
                    {req.requestedAt
                      ? new Date(req.requestedAt).toLocaleDateString(
                          undefined,
                          {
                            year: "numeric",
                            month: "short",
                            day: "numeric",
                          },
                        )
                      : "\u2014"}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-col gap-1">
                      <DeadlineCell
                        deadline={req.deadline}
                        deadlineStatus={req.deadlineStatus}
                        status={req.status}
                      />
                      {req.deadlineStatus !== "ON_TRACK" &&
                        req.status !== "COMPLETED" &&
                        req.status !== "REJECTED" && (
                          <Badge variant={deadlineVariant} className="w-fit">
                            {req.deadlineStatus === "OVERDUE"
                              ? "Overdue"
                              : "Due Soon"}
                          </Badge>
                        )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <RowActions
                      request={req}
                      slug={slug}
                      onActionComplete={() => {
                        // revalidatePath in server action handles refresh
                      }}
                    />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
