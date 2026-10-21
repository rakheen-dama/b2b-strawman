"use client";

import { useState } from "react";
import useSWR from "swr";
import { CourtDateListView } from "@/components/legal/court-date-list-view";
import { EditCourtDateDialog } from "@/components/legal/edit-court-date-dialog";
import { PostponeDialog } from "@/components/legal/postpone-dialog";
import { CancelCourtDateDialog } from "@/components/legal/cancel-court-date-dialog";
import { OutcomeDialog } from "@/components/legal/outcome-dialog";
import { CreateCourtDateDialog } from "@/components/legal/create-court-date-dialog";
import { PrescriptionTrackerList } from "@/components/legal/prescription-tracker-list";
import { fetchCourtDates, fetchPrescriptionTrackers } from "@/lib/actions/court-dates";
import type { CourtDate } from "@/lib/types";

interface ProjectCourtDatesTabProps {
  projectId: string;
  slug: string;
  /**
   * MANAGE_LEGAL (or admin / owner) — gates the prescription-tracker writes (fail closed). The
   * court-date writes on this tab keep their pre-613D (ungated) behaviour.
   */
  canManageLegal?: boolean;
}

export function ProjectCourtDatesTab({
  projectId,
  slug,
  canManageLegal = false,
}: ProjectCourtDatesTabProps) {
  const [editTarget, setEditTarget] = useState<CourtDate | null>(null);
  const [postponeTarget, setPostponeTarget] = useState<CourtDate | null>(null);
  const [cancelTarget, setCancelTarget] = useState<CourtDate | null>(null);
  const [outcomeTarget, setOutcomeTarget] = useState<CourtDate | null>(null);

  const { data, isLoading, mutate } = useSWR(
    `project-court-dates-${projectId}`,
    () => fetchCourtDates({ matterId: projectId }),
    { dedupingInterval: 2000 }
  );

  const courtDates = data?.content ?? [];

  // Phase 87 613D (Open Question #17): the matter's prescription trackers live here now that the
  // court-calendar page is gone; Diary PRESCRIPTION rows deep-link to ?tab=court-dates.
  const {
    data: trackerData,
    isLoading: trackersLoading,
    mutate: mutateTrackers,
  } = useSWR(
    `project-prescription-trackers-${projectId}`,
    () => fetchPrescriptionTrackers({ matterId: projectId }),
    { dedupingInterval: 2000 }
  );
  const trackers = trackerData?.content ?? [];

  return (
    <div data-testid="project-court-dates-tab" className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-medium text-slate-700 dark:text-slate-300">Court Dates</h3>
        <CreateCourtDateDialog slug={slug} onSuccess={() => mutate()} />
      </div>

      {isLoading ? (
        <p className="text-xs text-slate-500 italic">Loading court dates&hellip;</p>
      ) : (
        <CourtDateListView
          courtDates={courtDates}
          onEdit={setEditTarget}
          onPostpone={setPostponeTarget}
          onCancel={setCancelTarget}
          onRecordOutcome={setOutcomeTarget}
          onSelect={() => {}}
        />
      )}

      <div className="pt-4">
        {trackersLoading ? (
          <p className="text-xs text-slate-500 italic">Loading prescription trackers&hellip;</p>
        ) : (
          <PrescriptionTrackerList
            trackers={trackers}
            slug={slug}
            canManageLegal={canManageLegal}
            onRefresh={() => mutateTrackers()}
          />
        )}
      </div>

      {editTarget && (
        <EditCourtDateDialog
          slug={slug}
          courtDate={editTarget}
          open={!!editTarget}
          onOpenChange={(open) => !open && setEditTarget(null)}
          onSuccess={() => {
            setEditTarget(null);
            mutate();
          }}
        />
      )}

      {postponeTarget && (
        <PostponeDialog
          slug={slug}
          courtDateId={postponeTarget.id}
          open={!!postponeTarget}
          onOpenChange={(open) => !open && setPostponeTarget(null)}
          onSuccess={() => {
            setPostponeTarget(null);
            mutate();
          }}
        />
      )}

      {cancelTarget && (
        <CancelCourtDateDialog
          slug={slug}
          courtDateId={cancelTarget.id}
          open={!!cancelTarget}
          onOpenChange={(open) => !open && setCancelTarget(null)}
          onSuccess={() => {
            setCancelTarget(null);
            mutate();
          }}
        />
      )}

      {outcomeTarget && (
        <OutcomeDialog
          slug={slug}
          courtDateId={outcomeTarget.id}
          open={!!outcomeTarget}
          onOpenChange={(open) => !open && setOutcomeTarget(null)}
          onSuccess={() => {
            setOutcomeTarget(null);
            mutate();
          }}
        />
      )}
    </div>
  );
}
