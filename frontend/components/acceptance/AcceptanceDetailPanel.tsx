"use client";

import { useState } from "react";
import { Download, Mail, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { AcceptanceStatusBadge } from "@/components/acceptance/AcceptanceStatusBadge";
import {
  remindAcceptance,
  revokeAcceptance,
} from "@/lib/actions/acceptance-actions";
import type { AcceptanceRequestResponse } from "@/lib/actions/acceptance-actions";
import { formatDate } from "@/lib/format";
import { downloadCertificate } from "@/lib/actions/acceptance-actions";

// ---- Timeline Stage (adapted from DataRequestTimeline pattern) ----

interface TimelineStageProps {
  label: string;
  date?: string | null;
  isComplete: boolean;
  isActive?: boolean;
  isLast?: boolean;
  children?: React.ReactNode;
}

function TimelineStage({
  label,
  date,
  isComplete,
  isActive,
  isLast,
  children,
}: TimelineStageProps) {
  return (
    <div className="flex gap-3">
      <div className="flex flex-col items-center">
        <div
          className={cn(
            "size-2.5 rounded-full ring-2 ring-offset-2 ring-offset-white dark:ring-offset-slate-950 mt-0.5",
            isComplete
              ? "bg-teal-600 ring-teal-600"
              : isActive
                ? "bg-slate-400 ring-slate-400"
                : "bg-slate-200 ring-slate-200 dark:bg-slate-700 dark:ring-slate-700",
          )}
        />
        {!isLast && (
          <div className="mt-1 flex-1 w-px bg-slate-200 dark:bg-slate-800 min-h-6" />
        )}
      </div>

      <div className={cn("pb-4", isLast && "pb-0")}>
        <p
          className={cn(
            "text-sm font-medium",
            isComplete || isActive
              ? "text-slate-900 dark:text-slate-100"
              : "text-slate-400 dark:text-slate-600",
          )}
        >
          {label}
        </p>
        {date && (
          <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
            {formatDate(date)}
          </p>
        )}
        {children}
      </div>
    </div>
  );
}

// ---- Main Panel ----

interface AcceptanceDetailPanelProps {
  request: AcceptanceRequestResponse;
  isAdmin?: boolean;
  onUpdated?: () => void;
}

export function AcceptanceDetailPanel({
  request,
  isAdmin = false,
  onUpdated,
}: AcceptanceDetailPanelProps) {
  const [isReminding, setIsReminding] = useState(false);
  const [isRevoking, setIsRevoking] = useState(false);
  const [isDownloadingCert, setIsDownloadingCert] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const isSent = request.status === "SENT";
  const isViewed = request.status === "VIEWED";
  const isAccepted = request.status === "ACCEPTED";
  const isExpired = request.status === "EXPIRED";
  const isRevoked = request.status === "REVOKED";
  const isTerminal = isAccepted || isExpired || isRevoked;
  const canRemind = (isSent || isViewed) && isAdmin;
  const canRevoke = (isSent || isViewed) && isAdmin;

  async function handleRemind() {
    setIsReminding(true);
    setActionError(null);
    const result = await remindAcceptance(request.id);
    if (result.success) {
      window.dispatchEvent(new Event("acceptance-requests-refresh"));
      onUpdated?.();
    } else {
      setActionError(result.error ?? "Failed to send reminder.");
    }
    setIsReminding(false);
  }

  async function handleRevoke() {
    setIsRevoking(true);
    setActionError(null);
    const result = await revokeAcceptance(request.id);
    if (result.success) {
      window.dispatchEvent(new Event("acceptance-requests-refresh"));
      onUpdated?.();
    } else {
      setActionError(result.error ?? "Failed to revoke request.");
    }
    setIsRevoking(false);
  }

  async function handleDownloadCertificate() {
    setIsDownloadingCert(true);
    setActionError(null);
    const result = await downloadCertificate(request.id);
    if (result.success && result.pdfBase64) {
      const byteCharacters = atob(result.pdfBase64);
      const byteNumbers = new Array(byteCharacters.length);
      for (let i = 0; i < byteCharacters.length; i++) {
        byteNumbers[i] = byteCharacters.charCodeAt(i);
      }
      const byteArray = new Uint8Array(byteNumbers);
      const blob = new Blob([byteArray], { type: "application/pdf" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download =
        request.certificateFileName ?? `acceptance-certificate.pdf`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } else {
      setActionError(result.error ?? "Failed to download certificate.");
    }
    setIsDownloadingCert(false);
  }

  // Determine timeline stages
  const sentComplete = !!request.sentAt;
  const viewedComplete = !!request.viewedAt;
  const acceptedComplete = isAccepted;

  // Determine what the final stage is
  const showAcceptedStage = !isRevoked && !isExpired;
  const showRevokedStage = isRevoked;
  const showExpiredStage = isExpired;

  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50/50 p-4 dark:border-slate-800 dark:bg-slate-900/30">
      {/* Header: recipient info + status */}
      <div className="mb-4 flex items-start justify-between">
        <div>
          <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
            {request.contact.displayName}
          </p>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            {request.contact.email}
          </p>
        </div>
        <AcceptanceStatusBadge status={request.status} />
      </div>

      {/* Status Timeline */}
      <div className="mb-4">
        <TimelineStage
          label="Sent"
          date={request.sentAt}
          isComplete={sentComplete}
          isActive={isSent}
        />
        <TimelineStage
          label="Viewed"
          date={request.viewedAt}
          isComplete={viewedComplete}
          isActive={isViewed}
        />
        {showAcceptedStage && (
          <TimelineStage
            label="Accepted"
            date={request.acceptedAt}
            isComplete={acceptedComplete}
            isActive={false}
            isLast
          />
        )}
        {showRevokedStage && (
          <TimelineStage
            label="Revoked"
            date={request.revokedAt}
            isComplete
            isLast
          />
        )}
        {showExpiredStage && (
          <TimelineStage
            label="Expired"
            date={request.expiresAt}
            isComplete
            isLast
          />
        )}
      </div>

      {/* Accepted details */}
      {isAccepted && request.acceptorName && (
        <div className="mb-4 rounded-md border border-teal-200 bg-teal-50 p-3 dark:border-teal-900 dark:bg-teal-950/30">
          <p className="text-sm text-slate-700 dark:text-slate-300">
            Accepted by:{" "}
            <span className="font-medium">{request.acceptorName}</span>
          </p>
          {request.acceptedAt && (
            <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">
              on {formatDate(request.acceptedAt)}
            </p>
          )}
        </div>
      )}

      {/* Certificate download */}
      {isAccepted && request.hasCertificate && (
        <div className="mb-4">
          <Button
            variant="outline"
            size="sm"
            onClick={handleDownloadCertificate}
            disabled={isDownloadingCert}
          >
            <Download className="mr-1.5 size-3.5" />
            {isDownloadingCert
              ? "Downloading..."
              : "Download Certificate"}
          </Button>
        </div>
      )}

      {/* Reminder history */}
      {request.reminderCount > 0 && (
        <p className="mb-4 text-xs text-slate-500 dark:text-slate-400">
          Reminded {request.reminderCount}{" "}
          {request.reminderCount === 1 ? "time" : "times"}
          {request.lastRemindedAt && (
            <>, last on {formatDate(request.lastRemindedAt)}</>
          )}
        </p>
      )}

      {/* Expiry info */}
      {!isTerminal && request.expiresAt && (
        <p className="mb-4 text-xs text-slate-500 dark:text-slate-400">
          Expires: {formatDate(request.expiresAt)}
        </p>
      )}

      {/* Actions */}
      {!isTerminal && (canRemind || canRevoke) && (
        <div className="flex items-center gap-2">
          {canRemind && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleRemind}
              disabled={isReminding || isRevoking}
            >
              <Mail className="mr-1.5 size-3.5" />
              {isReminding ? "Sending..." : "Remind"}
            </Button>
          )}
          {canRevoke && (
            <Button
              variant="outline"
              size="sm"
              className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
              onClick={handleRevoke}
              disabled={isReminding || isRevoking}
            >
              <XCircle className="mr-1.5 size-3.5" />
              {isRevoking ? "Revoking..." : "Revoke"}
            </Button>
          )}
        </div>
      )}

      {/* Action error */}
      {actionError && (
        <p className="mt-2 text-sm text-destructive">{actionError}</p>
      )}
    </div>
  );
}
