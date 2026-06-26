"use client";

import { useEffect, useState, useTransition } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@b2mash/ui/card";
import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { formatDateTime } from "@/lib/format";
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
import Link from "next/link";
import { Check, X, Clock, Mail } from "lucide-react";
import type { AiGateListItem } from "@/lib/api/ai";
import type { CorrespondenceOrigin } from "@/lib/types";

interface ExecutionGateCardProps {
  gate: AiGateListItem;
  slug: string;
  correspondenceOrigin?: CorrespondenceOrigin;
  onApprove: (gateId: string, notes?: string) => Promise<{ success: boolean; error?: string }>;
  onReject: (gateId: string, notes?: string) => Promise<{ success: boolean; error?: string }>;
}

function getStatusBadgeVariant(status: string) {
  switch (status) {
    case "PENDING":
      return "warning" as const;
    case "APPROVED":
      return "success" as const;
    case "REJECTED":
      return "destructive" as const;
    case "EXPIRED":
      return "neutral" as const;
    default:
      return "default" as const;
  }
}

function formatGateType(gateType: string): string {
  return gateType
    .replace(/_/g, " ")
    .toLowerCase()
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

function getTimeRemaining(expiresAt: string): string | null {
  const now = new Date();
  const expires = new Date(expiresAt);
  const diff = expires.getTime() - now.getTime();
  if (diff <= 0) return null;
  const hours = Math.floor(diff / (1000 * 60 * 60));
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
  if (hours > 24) {
    const days = Math.floor(hours / 24);
    return `${days}d ${hours % 24}h remaining`;
  }
  if (hours > 0) return `${hours}h ${minutes}m remaining`;
  return `${minutes}m remaining`;
}

export function ExecutionGateCard({
  gate,
  slug,
  correspondenceOrigin,
  onApprove,
  onReject,
}: ExecutionGateCardProps) {
  const showCorrespondenceOrigin =
    gate.gateType === "CREATE_TASK_FROM_CORRESPONDENCE" && !!correspondenceOrigin?.projectId;
  const [approveOpen, setApproveOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  // Live countdown — computed only after mount so it never participates in
  // hydration. `getTimeRemaining` reads wall-clock `new Date()`, which differs
  // between the server render and the client hydrate (network + hydration
  // latency), so rendering it during SSR causes a hydration mismatch
  // (AIVERIFY-009). Null on the server pass and first client render (identical
  // → no mismatch); filled in post-mount and re-evaluated every minute so the
  // countdown actually advances. Mirrors the `RelativeDate` precedent in
  // components/ui/relative-date.tsx.
  const [timeRemaining, setTimeRemaining] = useState<string | null>(null);

  useEffect(() => {
    if (gate.status !== "PENDING") {
      // Clear any stale countdown if the gate flips PENDING -> non-PENDING on a
      // mounted card (e.g. right after approve/reject).
      // eslint-disable-next-line react-hooks/set-state-in-effect -- countdown is intentionally client/effect-driven (SSR hydration safety, AIVERIFY-009); reset on status change.
      setTimeRemaining(null);
      return;
    }
    // Computing the countdown client-only (initial state stays null on the
    // server pass) avoids a hydration mismatch — the wall-clock value differs
    // between server render and client mount (AIVERIFY-009).
    const update = () => setTimeRemaining(getTimeRemaining(gate.expiresAt));
    update();
    const interval = setInterval(update, 60_000);
    return () => clearInterval(interval);
  }, [gate.status, gate.expiresAt]);

  function handleApprove() {
    startTransition(async () => {
      const result = await onApprove(gate.id, notes || undefined);
      if (result.success) {
        setApproveOpen(false);
        setNotes("");
        setError(null);
      } else {
        setError(result.error || "Failed to approve gate.");
      }
    });
  }

  function handleReject() {
    startTransition(async () => {
      const result = await onReject(gate.id, notes || undefined);
      if (result.success) {
        setRejectOpen(false);
        setNotes("");
        setError(null);
      } else {
        setError(result.error || "Failed to reject gate.");
      }
    });
  }

  return (
    <Card className="border-slate-200 dark:border-slate-800">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <div className="space-y-1">
            <CardTitle className="text-base font-semibold text-slate-950 dark:text-slate-50">
              {formatGateType(gate.gateType)}
            </CardTitle>
            <p className="font-mono text-xs text-slate-500 dark:text-slate-400">
              Execution {gate.executionId.slice(0, 8)}...
            </p>
          </div>
          <Badge variant={getStatusBadgeVariant(gate.status)}>{gate.status}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {/* AI Reasoning */}
        <div>
          <p className="text-sm leading-relaxed text-slate-700 dark:text-slate-300">
            {gate.aiReasoning}
          </p>
        </div>

        {/* Originating correspondence — CREATE_TASK_FROM_CORRESPONDENCE gates only.
            Subject is not exposed by the backend; link to the matter's Correspondence tab. */}
        {showCorrespondenceOrigin && (
          <Link
            href={`/org/${slug}/projects/${correspondenceOrigin!.projectId}?tab=correspondence`}
            className="inline-flex items-center gap-1.5 text-sm text-teal-600 hover:text-teal-700"
          >
            <Mail className="size-3.5" />
            View originating correspondence
          </Link>
        )}

        {/* Expiry countdown — PENDING only, populated post-mount (see effect) */}
        {gate.status === "PENDING" && timeRemaining && (
          <div className="flex items-center gap-1.5 text-xs text-amber-600 dark:text-amber-400">
            <Clock className="size-3.5" />
            <span>{timeRemaining}</span>
          </div>
        )}

        {/* Error message */}
        {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}

        {/* Action buttons for PENDING gates */}
        {gate.status === "PENDING" && (
          <div className="flex items-center gap-2 pt-1">
            <Button
              type="button"
              variant="accent"
              size="sm"
              disabled={isPending}
              onClick={() => setApproveOpen(true)}
            >
              <Check className="size-3.5" />
              Approve
            </Button>
            <Button
              type="button"
              variant="destructive"
              size="sm"
              disabled={isPending}
              onClick={() => setRejectOpen(true)}
            >
              <X className="size-3.5" />
              Reject
            </Button>
          </div>
        )}

        {/* Timestamp — deterministic (pinned en-ZA + Africa/Johannesburg) so
            the SSR pass and client hydrate render the same string. */}
        <p className="font-mono text-xs text-slate-400 tabular-nums dark:text-slate-500">
          {formatDateTime(gate.createdAt)}
        </p>
      </CardContent>

      {/* Approve Dialog */}
      <AlertDialog
        open={approveOpen}
        onOpenChange={(open) => {
          setApproveOpen(open);
          if (!open) {
            setNotes("");
            setError(null);
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Approve Gate Action</AlertDialogTitle>
            <AlertDialogDescription>
              Approve the proposed action for &quot;{formatGateType(gate.gateType)}&quot;? The AI
              will proceed with the action once approved.
            </AlertDialogDescription>
          </AlertDialogHeader>
          {error && approveOpen && (
            <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
          )}
          <div className="py-2">
            <Textarea
              placeholder="Optional review notes..."
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={3}
            />
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction variant="accent" disabled={isPending} onClick={handleApprove}>
              Approve
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Reject Dialog */}
      <AlertDialog
        open={rejectOpen}
        onOpenChange={(open) => {
          setRejectOpen(open);
          if (!open) {
            setNotes("");
            setError(null);
          }
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Reject Gate Action</AlertDialogTitle>
            <AlertDialogDescription>
              Reject the proposed action for &quot;{formatGateType(gate.gateType)}&quot;? The AI
              will not proceed with this action.
            </AlertDialogDescription>
          </AlertDialogHeader>
          {error && rejectOpen && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
          <div className="py-2">
            <Textarea
              placeholder="Optional rejection reason..."
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={3}
            />
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction variant="destructive" disabled={isPending} onClick={handleReject}>
              Reject
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </Card>
  );
}
