"use client";

import { useMemo, useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Inbox, Loader2 } from "lucide-react";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { DateCell, StatusBadgeCell } from "@/components/data-table/cells";
import { EmptyState } from "@/components/empty-state";
import { daysBetween, todayInSA } from "@b2mash/shared/date";
import { updateDsarStatus } from "@/app/(app)/org/[slug]/settings/firm/_tabs/data-requests-actions";
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
    return <span className="text-muted-foreground text-sm">{deadline}</span>;
  }

  // Days left from the South African "today" (G88-F48), not the browser's or the UTC date.
  const diffDays = daysBetween(todayInSA(), deadline);

  if (deadlineStatus === "OVERDUE") {
    return <span className="text-destructive text-sm font-medium">Overdue</span>;
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
          <DialogDescription>Please provide resolution notes before proceeding.</DialogDescription>
        </DialogHeader>
        <div className="space-y-3 py-2">
          <Textarea
            placeholder="Enter resolution notes..."
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={4}
            maxLength={2000}
          />
          {error && <p className="text-destructive text-sm">{error}</p>}
        </div>
        <DialogFooter>
          <Button type="button" variant="plain" onClick={handleClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button type="button" onClick={handleConfirm} disabled={isSubmitting}>
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
  const [isPending, startTransition] = useTransition();
  const [actionError, setActionError] = useState<string | null>(null);
  const [resolutionDialogOpen, setResolutionDialogOpen] = useState(false);
  const [pendingAction, setPendingAction] = useState<"COMPLETE" | "REJECT" | null>(null);

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

  function handleResolutionConfirm(notes: string) {
    if (!pendingAction) return;
    setActionError(null);
    startTransition(async () => {
      const result = await updateDsarStatus(slug, request.id, pendingAction, notes);
      if (!result.success) {
        setActionError(result.error ?? "Action failed.");
      } else {
        setResolutionDialogOpen(false);
        setPendingAction(null);
        onActionComplete();
      }
    });
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
          disabled={isPending}
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
            disabled={isPending}
            aria-label="Complete request"
          >
            Complete
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => openResolutionDialog("REJECT")}
            disabled={isPending}
            aria-label="Deny request"
          >
            Deny
          </Button>
        </>
      )}
      {actionError && (
        <span className="text-destructive text-xs" role="alert">
          {actionError}
        </span>
      )}
      <ResolutionDialog
        open={resolutionDialogOpen}
        title={pendingAction === "COMPLETE" ? "Complete Request" : "Deny Request"}
        onClose={() => {
          setResolutionDialogOpen(false);
          setPendingAction(null);
        }}
        onConfirm={handleResolutionConfirm}
        isSubmitting={isPending}
      />
    </div>
  );
}

// --- Main Table Component ---

interface DsarRequestsTableProps {
  requests: DsarRequest[];
  slug: string;
}

const getRequestId = (request: DsarRequest) => request.id;

function deadlineBadgeVariant(
  deadlineStatus: DeadlineStatus
): "destructive" | "warning" | "neutral" {
  return deadlineStatus === "OVERDUE"
    ? "destructive"
    : deadlineStatus === "DUE_SOON"
      ? "warning"
      : "neutral";
}

export function DsarRequestsTable({ requests, slug }: DsarRequestsTableProps) {
  const router = useRouter();

  // Stable columns: `RowActions` owns its transition and resolution dialog, so a column rebuild
  // would remount it and close an open dialog. `useRouter()` is stable.
  const columns = useMemo(() => {
    const onActionComplete = () => router.refresh();
    return defineColumns<DsarRequest>([
      {
        id: "subject",
        meta: { label: "Subject / Client", cellClassName: "whitespace-normal" },
        cell: ({ row }) => {
          const req = row.original;
          return (
            <div>
              <span className="text-foreground font-medium">
                {req.subjectName ?? req.clientName ?? "\u2014"}
              </span>
              {req.clientName && req.subjectName && req.clientName !== req.subjectName && (
                <p className="text-muted-foreground text-xs">{req.clientName}</p>
              )}
              {req.subjectEmail && (
                <p className="text-muted-foreground text-xs">{req.subjectEmail}</p>
              )}
            </div>
          );
        },
      },
      {
        id: "type",
        meta: { label: "Type" },
        cell: ({ row }) => (
          <Badge variant="neutral">{REQUEST_TYPE_LABEL[row.original.requestType]}</Badge>
        ),
      },
      {
        id: "status",
        meta: { label: "Status" },
        cell: ({ row }) => {
          const statusBadge = STATUS_BADGE[row.original.status];
          return <StatusBadgeCell label={statusBadge.label} variant={statusBadge.variant} />;
        },
      },
      {
        id: "received",
        meta: { label: "Received", cellClassName: "text-slate-700 dark:text-slate-300" },
        cell: ({ row }) => <DateCell value={row.original.requestedAt} />,
      },
      {
        id: "deadline",
        meta: { label: "Deadline" },
        cell: ({ row }) => {
          const req = row.original;
          return (
            <div className="flex flex-col gap-1">
              <DeadlineCell
                deadline={req.deadline}
                deadlineStatus={req.deadlineStatus}
                status={req.status}
              />
              {req.deadlineStatus !== "ON_TRACK" &&
                req.status !== "COMPLETED" &&
                req.status !== "REJECTED" && (
                  <Badge variant={deadlineBadgeVariant(req.deadlineStatus)} className="w-fit">
                    {req.deadlineStatus === "OVERDUE" ? "Overdue" : "Due Soon"}
                  </Badge>
                )}
            </div>
          );
        },
      },
      {
        id: "actions",
        enableHiding: false,
        meta: { label: "Actions" },
        cell: ({ row }) => (
          <RowActions request={row.original} slug={slug} onActionComplete={onActionComplete} />
        ),
      },
    ]);
  }, [slug, router]);

  return (
    <DataTable
      columns={columns}
      data={requests}
      getRowId={getRequestId}
      columnVisibility={false}
      pageSize={null}
      stickyHeader={false}
      frame={false}
      empty={
        <EmptyState
          variant="compact"
          icon={Inbox}
          title="No DSAR requests found."
          description={'Use the "Log new request" button to record one.'}
        />
      }
    />
  );
}
