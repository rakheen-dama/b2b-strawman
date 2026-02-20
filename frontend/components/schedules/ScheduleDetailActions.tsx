"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Pause, Play, Trash2 } from "lucide-react";
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
import {
  pauseScheduleAction,
  resumeScheduleAction,
  deleteScheduleAction,
} from "@/app/(app)/org/[slug]/schedules/actions";
import type { ScheduleResponse } from "@/lib/api/schedules";

interface ScheduleDetailActionsProps {
  slug: string;
  schedule: ScheduleResponse;
}

export function ScheduleDetailActions({ slug, schedule }: ScheduleDetailActionsProps) {
  const router = useRouter();
  const [pauseDialogOpen, setPauseDialogOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [actionInProgress, setActionInProgress] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handlePause() {
    setActionInProgress(true);
    try {
      const result = await pauseScheduleAction(slug, schedule.id);
      if (result.success) {
        setPauseDialogOpen(false);
      } else {
        setError(result.error ?? "Failed to pause schedule.");
      }
    } finally {
      setActionInProgress(false);
    }
  }

  async function handleResume() {
    setActionInProgress(true);
    try {
      const result = await resumeScheduleAction(slug, schedule.id);
      if (!result.success) {
        setError(result.error ?? "Failed to resume schedule.");
      }
    } finally {
      setActionInProgress(false);
    }
  }

  async function handleDelete() {
    setActionInProgress(true);
    try {
      const result = await deleteScheduleAction(slug, schedule.id);
      if (result.success) {
        setDeleteDialogOpen(false);
        router.push(`/org/${slug}/schedules`);
      } else {
        setError(result.error ?? "Failed to delete schedule.");
        setDeleteDialogOpen(false);
      }
    } finally {
      setActionInProgress(false);
    }
  }

  return (
    <>
      {error && <p className="text-sm text-destructive">{error}</p>}

      {schedule.status === "ACTIVE" && (
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
                <AlertDialogTitle>Pause Schedule</AlertDialogTitle>
                <AlertDialogDescription>
                  Pausing this schedule will stop automatic project creation. You can resume it at
                  any time.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel disabled={actionInProgress}>Cancel</AlertDialogCancel>
                <AlertDialogAction onClick={handlePause} disabled={actionInProgress}>
                  {actionInProgress ? "Pausing..." : "Pause Schedule"}
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </>
      )}

      {schedule.status === "PAUSED" && (
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

      {(schedule.status === "PAUSED" || schedule.status === "COMPLETED") && (
        <>
          <Button
            variant="ghost"
            size="sm"
            className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
            onClick={() => setDeleteDialogOpen(true)}
            disabled={actionInProgress}
          >
            <Trash2 className="mr-1.5 size-4" />
            Delete
          </Button>

          <AlertDialog
            open={deleteDialogOpen}
            onOpenChange={(o) => {
              if (!actionInProgress) setDeleteDialogOpen(o);
            }}
          >
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Delete Schedule</AlertDialogTitle>
                <AlertDialogDescription>
                  This will permanently delete the schedule. Existing projects will not be affected.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel disabled={actionInProgress}>Cancel</AlertDialogCancel>
                <AlertDialogAction
                  variant="destructive"
                  onClick={handleDelete}
                  disabled={actionInProgress}
                >
                  {actionInProgress ? "Deleting..." : "Delete"}
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </>
      )}
    </>
  );
}
