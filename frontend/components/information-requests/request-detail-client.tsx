"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import {
  Check,
  X,
  Clock,
  FileText,
  MessageSquare,
  MoreHorizontal,
  Ban,
  Send,
  Mail,
  Calendar,
} from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { RequestStatusBadge } from "@/components/information-requests/request-status-badge";
import { RequestProgressBar } from "@/components/information-requests/request-progress-bar";
import { ItemStatusBadge } from "@/components/information-requests/item-status-badge";
import { ResponseTypeBadge } from "@/components/information-requests/response-type-badge";
import { RejectItemDialog } from "@/components/information-requests/reject-item-dialog";
import { formatDate } from "@/lib/format";
import type {
  InformationRequestResponse,
  InformationRequestItemResponse,
} from "@/lib/api/information-requests";
import {
  acceptItemAction,
  rejectItemAction,
  cancelRequestAction,
  resendNotificationAction,
} from "@/app/(app)/org/[slug]/information-requests/[id]/actions";

interface RequestDetailClientProps {
  request: InformationRequestResponse;
  slug: string;
}

export function RequestDetailClient({ request, slug }: RequestDetailClientProps) {
  const router = useRouter();
  const [acceptingItemId, setAcceptingItemId] = useState<string | null>(null);
  const [rejectDialogItem, setRejectDialogItem] = useState<InformationRequestItemResponse | null>(
    null
  );
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false);
  const [isCancelling, setIsCancelling] = useState(false);
  const [isResending, setIsResending] = useState(false);

  const sortedItems = [...request.items].sort((a, b) => a.sortOrder - b.sortOrder);

  async function handleAccept(itemId: string) {
    setAcceptingItemId(itemId);
    try {
      const result = await acceptItemAction(slug, request.id, itemId);
      if (result.success) {
        router.refresh();
      } else {
        toast.error(result.error ?? "Failed to accept item.");
      }
    } catch {
      toast.error("An unexpected error occurred.");
    } finally {
      setAcceptingItemId(null);
    }
  }

  async function handleReject(reason: string) {
    if (!rejectDialogItem) return;
    const result = await rejectItemAction(slug, request.id, rejectDialogItem.id, reason);
    if (result.success) {
      router.refresh();
    } else {
      toast.error(result.error ?? "Failed to reject item.");
    }
  }

  async function handleCancel() {
    setIsCancelling(true);
    try {
      const result = await cancelRequestAction(slug, request.id);
      if (result.success) {
        setCancelDialogOpen(false);
        router.refresh();
        toast.success("Request cancelled successfully.");
      } else {
        toast.error(result.error ?? "Failed to cancel request.");
      }
    } catch {
      toast.error("An unexpected error occurred.");
    } finally {
      setIsCancelling(false);
    }
  }

  async function handleResend() {
    setIsResending(true);
    try {
      const result = await resendNotificationAction(request.id);
      if (result.success) {
        toast.success("Notification resent successfully.");
      } else {
        toast.error(result.error ?? "Failed to resend notification.");
      }
    } catch {
      toast.error("An unexpected error occurred.");
    } finally {
      setIsResending(false);
    }
  }

  const canCancel = request.status !== "CANCELLED" && request.status !== "COMPLETED";
  const canResend = request.status === "SENT" || request.status === "IN_PROGRESS";

  return (
    <>
      {/* Header */}
      <div className="flex items-start justify-between">
        <div className="space-y-1">
          <div className="flex items-center gap-3">
            <h1 className="font-display text-3xl text-slate-950 dark:text-slate-50">
              {request.requestNumber}
            </h1>
            <RequestStatusBadge status={request.status} />
          </div>
          <div className="flex items-center gap-4 text-sm text-slate-600 dark:text-slate-400">
            <Link
              href={`/org/${slug}/customers/${request.customerId}`}
              className="text-teal-600 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
            >
              {request.customerName}
            </Link>
            {request.projectName && (
              <>
                <span className="text-slate-300 dark:text-slate-700">|</span>
                <span>{request.projectName}</span>
              </>
            )}
          </div>
        </div>

        {(canCancel || canResend) && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="icon">
                <MoreHorizontal className="size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {canResend && (
                <DropdownMenuItem onClick={handleResend} disabled={isResending}>
                  <Send className="mr-2 size-4" />
                  {isResending ? "Resending..." : "Resend Notification"}
                </DropdownMenuItem>
              )}
              {canCancel && (
                <DropdownMenuItem
                  onClick={() => setCancelDialogOpen(true)}
                  className="text-destructive focus:text-destructive"
                >
                  <Ban className="mr-2 size-4" />
                  Cancel Request
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      </div>

      {/* Metadata */}
      <div className="grid grid-cols-2 gap-6 rounded-lg border border-slate-200 bg-slate-50 p-4 sm:grid-cols-4 dark:border-slate-800 dark:bg-slate-900">
        <div className="space-y-1">
          <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500 dark:text-slate-400">
            <Mail className="size-3.5" />
            Contact
          </div>
          <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
            {request.portalContactName}
          </p>
          <p className="text-xs text-slate-500 dark:text-slate-400">{request.portalContactEmail}</p>
        </div>
        <div className="space-y-1">
          <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500 dark:text-slate-400">
            <Clock className="size-3.5" />
            Reminder Interval
          </div>
          <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
            Every {request.reminderIntervalDays} days
          </p>
        </div>
        <div className="space-y-1">
          <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500 dark:text-slate-400">
            <Calendar className="size-3.5" />
            Sent
          </div>
          <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
            {request.sentAt ? formatDate(request.sentAt) : "Not sent"}
          </p>
        </div>
        <div className="space-y-1">
          <div className="flex items-center gap-1.5 text-xs font-medium text-slate-500 dark:text-slate-400">
            <Check className="size-3.5" />
            Progress
          </div>
          <RequestProgressBar
            totalItems={request.totalItems}
            acceptedItems={request.acceptedItems}
          />
        </div>
      </div>

      {request.completedAt && (
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Completed on {formatDate(request.completedAt)}
        </p>
      )}

      {/* Items List */}
      <div className="space-y-4">
        <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">
          Items ({sortedItems.length})
        </h2>

        <div className="divide-y divide-slate-200 rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
          {sortedItems.map((item) => (
            <RequestItemRow
              key={item.id}
              item={item}
              onAccept={() => handleAccept(item.id)}
              onReject={() => setRejectDialogItem(item)}
              isAccepting={acceptingItemId === item.id}
            />
          ))}
          {sortedItems.length === 0 && (
            <div className="px-4 py-8 text-center text-sm text-slate-500 dark:text-slate-400">
              No items in this request.
            </div>
          )}
        </div>
      </div>

      {/* Reject Item Dialog */}
      <RejectItemDialog
        open={!!rejectDialogItem}
        onOpenChange={(open) => {
          if (!open) setRejectDialogItem(null);
        }}
        itemName={rejectDialogItem?.name ?? ""}
        onConfirm={handleReject}
      />

      {/* Cancel Request Dialog */}
      <AlertDialog open={cancelDialogOpen} onOpenChange={setCancelDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Cancel Request</AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to cancel {request.requestNumber}? This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isCancelling}>Keep Request</AlertDialogCancel>
            <Button variant="destructive" onClick={handleCancel} disabled={isCancelling}>
              {isCancelling ? "Cancelling..." : "Cancel Request"}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

// ---- Item Row Component ----

interface RequestItemRowProps {
  item: InformationRequestItemResponse;
  onAccept: () => void;
  onReject: () => void;
  isAccepting: boolean;
}

function RequestItemRow({ item, onAccept, onReject, isAccepting }: RequestItemRowProps) {
  const bgClass = getItemBgClass(item.status);

  return (
    <div className={`px-4 py-4 ${bgClass}`}>
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1 space-y-1">
          <div className="flex items-center gap-2">
            {item.status === "ACCEPTED" && (
              <Check className="size-4 shrink-0 text-green-600 dark:text-green-400" />
            )}
            {item.status === "REJECTED" && (
              <X className="size-4 shrink-0 text-red-600 dark:text-red-400" />
            )}
            <span className="font-medium text-slate-900 dark:text-slate-100">{item.name}</span>
            <ResponseTypeBadge responseType={item.responseType} />
            <ItemStatusBadge status={item.status} />
          </div>

          {item.description && (
            <p className="text-sm text-slate-600 dark:text-slate-400">{item.description}</p>
          )}

          {/* Status-specific content */}
          {item.status === "PENDING" && (
            <p className="flex items-center gap-1.5 text-sm text-slate-500 dark:text-slate-400">
              <Clock className="size-3.5" />
              Waiting for client
            </p>
          )}

          {item.status === "ACCEPTED" &&
            item.responseType === "FILE_UPLOAD" &&
            item.documentFileName && (
              <p className="flex items-center gap-1.5 text-sm text-teal-600 dark:text-teal-400">
                <FileText className="size-3.5" />
                {item.documentFileName}
              </p>
            )}

          {item.status === "ACCEPTED" &&
            item.responseType === "TEXT_RESPONSE" &&
            item.textResponse && (
              <p className="flex items-center gap-1.5 text-sm text-slate-700 dark:text-slate-300">
                <MessageSquare className="size-3.5" />
                {item.textResponse}
              </p>
            )}

          {item.status === "REJECTED" && item.rejectionReason && (
            <p className="text-sm text-red-600 dark:text-red-400">Reason: {item.rejectionReason}</p>
          )}
        </div>

        {/* Action buttons for SUBMITTED items */}
        {item.status === "SUBMITTED" && (
          <div className="flex shrink-0 items-center gap-2">
            <Button
              size="sm"
              variant="outline"
              onClick={onAccept}
              disabled={isAccepting}
              className="text-green-600 hover:bg-green-50 hover:text-green-700 dark:text-green-400 dark:hover:bg-green-950 dark:hover:text-green-300"
            >
              <Check className="mr-1.5 size-3.5" />
              {isAccepting ? "Accepting..." : "Accept"}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={onReject}
              className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
            >
              <X className="mr-1.5 size-3.5" />
              Reject
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}

function getItemBgClass(status: string): string {
  switch (status) {
    case "PENDING":
      return "bg-slate-50 dark:bg-slate-900/50";
    case "SUBMITTED":
      return "bg-amber-50 dark:bg-amber-950/30";
    case "ACCEPTED":
      return "bg-green-50 dark:bg-green-950/20";
    case "REJECTED":
      return "bg-red-50 dark:bg-red-950/20";
    default:
      return "";
  }
}
