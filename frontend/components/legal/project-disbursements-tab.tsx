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
}

export function ProjectDisbursementsTab({ projectId, slug }: ProjectDisbursementsTabProps) {
  const router = useRouter();
  const [editTarget, setEditTarget] = useState<DisbursementResponse | null>(null);

  const { data, isLoading, mutate } = useSWR(
    `project-disbursements-${projectId}`,
    () => fetchDisbursements({ projectId, size: 100 }),
    { dedupingInterval: 2000 }
  );

  const disbursements = data?.content ?? [];

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
