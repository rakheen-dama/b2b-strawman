"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import useSWR from "swr";
import { ModuleGate } from "@/components/module-gate";
import { CreateDisbursementDialog } from "@/components/legal/create-disbursement-dialog";
import { EditDisbursementDialog } from "@/components/legal/edit-disbursement-dialog";
import { DisbursementListView } from "@/components/legal/disbursement-list-view";
import { fetchDisbursements } from "@/app/(app)/org/[slug]/legal/disbursements/actions";
import type { DisbursementResponse } from "@/lib/api/legal-disbursements";

interface ProjectDisbursementsTabProps {
  projectId: string;
  slug: string;
  canManage?: boolean;
}

const PAGE_SIZE = 100;

export function ProjectDisbursementsTab({
  projectId,
  slug,
  canManage: _canManage,
}: ProjectDisbursementsTabProps) {
  const router = useRouter();
  const [editTarget, setEditTarget] = useState<DisbursementResponse | null>(null);

  const { data, isLoading, mutate } = useSWR(
    `project-disbursements-${projectId}`,
    () => fetchDisbursements({ projectId, size: PAGE_SIZE }),
    { dedupingInterval: 2000 }
  );

  const disbursements = data?.content ?? [];
  const totalElements = data?.page.totalElements ?? disbursements.length;
  const hasMore = totalElements > disbursements.length;

  return (
    <ModuleGate module="disbursements">
      <div data-testid="project-disbursements-tab" className="space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-sm font-medium text-slate-700 dark:text-slate-300">
            Disbursements
          </h3>
          <CreateDisbursementDialog
            slug={slug}
            defaultProjectId={projectId}
            onSuccess={() => mutate()}
          />
        </div>

        {hasMore && (
          <div
            data-testid="project-disbursements-pagination-banner"
            className="rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200"
          >
            Showing first {PAGE_SIZE} of {totalElements} disbursements.{" "}
            <a
              href={`/org/${slug}/legal/disbursements?projectId=${projectId}`}
              className="font-medium underline"
            >
              Open the full disbursements page
            </a>{" "}
            to see all.
          </div>
        )}

        {isLoading ? (
          <p className="text-xs text-slate-500 italic">Loading disbursements&hellip;</p>
        ) : (
          <DisbursementListView
            disbursements={disbursements}
            onSelect={(d) => router.push(`/org/${slug}/legal/disbursements/${d.id}`)}
            onEdit={setEditTarget}
          />
        )}

        {editTarget && (
          <EditDisbursementDialog
            slug={slug}
            disbursement={editTarget}
            open={!!editTarget}
            onOpenChange={(open) => !open && setEditTarget(null)}
            onSuccess={() => {
              setEditTarget(null);
              mutate();
            }}
          />
        )}
      </div>
    </ModuleGate>
  );
}
