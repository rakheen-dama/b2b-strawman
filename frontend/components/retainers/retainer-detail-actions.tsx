"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Pause, Play, XCircle, FileCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
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
import { ClosePeriodDialog } from "@/components/retainers/close-period-dialog";
import {
  pauseRetainerAction,
  resumeRetainerAction,
  terminateRetainerAction,
} from "@/app/(app)/org/[slug]/retainers/[id]/actions";
import type { RetainerStatus, PeriodSummary, RetainerResponse } from "@/lib/api/retainers";

interface RetainerDetailActionsProps {
  slug: string;
  retainer: RetainerResponse;
}

export function RetainerDetailActions({
  slug,
  retainer,
}: RetainerDetailActionsProps) {
  const router = useRouter();
  const [pauseDialogOpen, setPauseDialogOpen] = useState(false);
  const [terminateDialogOpen, setTerminateDialogOpen] = useState(false);
  const [closePeriodOpen, setClosePeriodOpen] = useState(false);
  const [actionInProgress, setActionInProgress] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const status: RetainerStatus = retainer.status;
  const currentPeriod: PeriodSummary | null = retainer.currentPeriod;

  async function handlePause() {
    setActionInProgress(true);
    setError(null);
    try {
      const result = await pauseRetainerAction(slug, retainer.id);
      if (result.success) {
        setPauseDialogOpen(false);
      } else {
        setError(result.error ?? "Failed to pause retainer.");
      }
    } finally {
      setActionInProgress(false);
    }
  }

  async function handleResume() {
    setActionInProgress(true);
    setError(null);
    try {
      const result = await resumeRetainerAction(slug, retainer.id);
      if (!result.success) {
        setError(result.error ?? "Failed to resume retainer.");
      }
    } finally {
      setActionInProgress(false);
    }
  }

  async function handleTerminate() {
    setActionInProgress(true);
    setError(null);
    try {
      const result = await terminateRetainerAction(slug, retainer.id);
      if (result.success) {
        setTerminateDialogOpen(false);
        router.push(`/org/${slug}/retainers`);
      } else {
        setError(result.error ?? "Failed to terminate retainer.");
        setTerminateDialogOpen(false);
      }
    } finally {
      setActionInProgress(false);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      {error && <p className="text-sm text-destructive">{error}</p>}

      <div className="flex items-center gap-2">
      {/* Close Period */}
      {currentPeriod?.readyToClose && (
        <Button
          variant="outline"
          size="sm"
          onClick={() => setClosePeriodOpen(true)}
          disabled={actionInProgress}
        >
          <FileCheck className="mr-1.5 size-4" />
          Close Period
        </Button>
      )}

      {/* Pause (ACTIVE only) */}
      {status === "ACTIVE" && (
        <>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setPauseDialogOpen(true)}
            disabled={actionInProgress}
          >
            <Pause className="mr-1.5 size-4" />
            Pause
          </Button>

          <AlertDialog
            open={pauseDialogOpen}
            onOpenChange={(o) => {
              if (!actionInProgress) setPauseDialogOpen(o);
            }}
          >
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Pause Retainer</AlertDialogTitle>
                <AlertDialogDescription>
                  Pausing this retainer will stop time tracking against it. You
                  can resume it at any time.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel disabled={actionInProgress}>
                  Cancel
                </AlertDialogCancel>
                <AlertDialogAction
                  onClick={(e) => { e.preventDefault(); handlePause(); }}
                  disabled={actionInProgress}
                >
                  {actionInProgress ? "Pausing..." : "Pause Retainer"}
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </>
      )}

      {/* Resume (PAUSED only) */}
      {status === "PAUSED" && (
        <Button
          variant="ghost"
          size="sm"
          onClick={handleResume}
          disabled={actionInProgress}
        >
          <Play className="mr-1.5 size-4" />
          Resume
        </Button>
      )}

      {/* Terminate (ACTIVE or PAUSED) */}
      {(status === "ACTIVE" || status === "PAUSED") && (
        <>
          <Button
            variant="ghost"
            size="sm"
            className="text-red-600 hover:bg-red-50 hover:text-red-700"
            onClick={() => setTerminateDialogOpen(true)}
            disabled={actionInProgress}
          >
            <XCircle className="mr-1.5 size-4" />
            Terminate
          </Button>

          <AlertDialog
            open={terminateDialogOpen}
            onOpenChange={(o) => {
              if (!actionInProgress) setTerminateDialogOpen(o);
            }}
          >
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Terminate Retainer</AlertDialogTitle>
                <AlertDialogDescription>
                  This will permanently end the retainer agreement. Any open
                  period will need to be closed separately. This action cannot be
                  undone.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel disabled={actionInProgress}>
                  Cancel
                </AlertDialogCancel>
                <AlertDialogAction
                  variant="destructive"
                  onClick={(e) => { e.preventDefault(); handleTerminate(); }}
                  disabled={actionInProgress}
                >
                  {actionInProgress ? "Terminating..." : "Terminate"}
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </>
      )}

      {/* Close Period Dialog */}
      {currentPeriod && (
        <ClosePeriodDialog
          slug={slug}
          retainerId={retainer.id}
          period={currentPeriod}
          retainer={retainer}
          open={closePeriodOpen}
          onOpenChange={setClosePeriodOpen}
        />
      )}
      </div>
    </div>
  );
}
