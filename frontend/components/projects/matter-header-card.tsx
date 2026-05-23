"use client";

import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { Card } from "@/components/ui/card";
import { TerminologyText } from "@/components/terminology-text";
import { PROJECT_STATUS_BADGE } from "@/lib/constants/project-status";
import { ProjectLifecycleActions } from "@/components/projects/project-lifecycle-actions";
import { MatterClosureAction } from "@/components/projects/matter-closure-action";
import { MatterReopenAction } from "@/components/projects/matter-reopen-action";
import { formatDate } from "@/lib/format";
import type { ProjectStatus, Customer } from "@/lib/types";

interface MatterHeaderCardProps {
  projectId: string;
  projectName: string;
  projectStatus: ProjectStatus;
  workType: string | null;
  referenceNumber: string | null;
  closedAt: string | null;
  customers: Pick<Customer, "id" | "name">[];
  slug: string;
  isAdmin: boolean;
}

export function MatterHeaderCard({
  projectId,
  projectName,
  projectStatus,
  workType,
  referenceNumber,
  closedAt,
  customers,
  slug,
  isAdmin,
}: MatterHeaderCardProps) {
  const statusBadge = PROJECT_STATUS_BADGE[projectStatus];

  return (
    <Card className="p-5" data-testid="matter-header-card">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 space-y-2">
          <h1
            className="font-display text-xl font-semibold text-slate-950 dark:text-slate-50"
            data-testid="matter-name"
          >
            {projectName}
          </h1>

          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={statusBadge.variant} data-testid="project-status-badge">
              {statusBadge.label}
            </Badge>
            {workType && (
              <span className="text-sm text-slate-600 dark:text-slate-400">{workType}</span>
            )}
            {referenceNumber && (
              <code className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-600 dark:bg-slate-800 dark:text-slate-400">
                {referenceNumber}
              </code>
            )}
            {projectStatus === "CLOSED" && closedAt && (
              <span
                className="text-sm text-slate-500 dark:text-slate-400"
                data-testid="project-closed-at"
              >
                Closed {formatDate(closedAt)}
              </span>
            )}
          </div>

          {customers.length > 0 && (
            <div className="text-sm text-slate-600 dark:text-slate-400">
              <Link
                href={`/org/${slug}/customers/${customers[0].id}`}
                className="text-teal-600 hover:text-teal-700 hover:underline dark:text-teal-400 dark:hover:text-teal-300"
              >
                {customers[0].name}
              </Link>
            </div>
          )}
          {customers.length === 0 && (
            <div className="text-sm text-slate-500 dark:text-slate-400">
              <TerminologyText template="Internal {Project}" />
            </div>
          )}
        </div>

        {/* Lifecycle actions */}
        <div className="flex shrink-0 items-center gap-2" data-testid="header-lifecycle-actions">
          {projectStatus === "ACTIVE" && (
            <MatterClosureAction
              slug={slug}
              projectId={projectId}
              projectName={projectName}
              projectStatus={projectStatus}
            />
          )}
          {projectStatus === "CLOSED" && (
            <MatterReopenAction
              slug={slug}
              projectId={projectId}
              projectName={projectName}
              projectStatus={projectStatus}
            />
          )}
          {(projectStatus === "ACTIVE" ||
            projectStatus === "COMPLETED" ||
            projectStatus === "ARCHIVED") &&
            isAdmin && (
              <ProjectLifecycleActions
                slug={slug}
                projectId={projectId}
                projectName={projectName}
                projectStatus={projectStatus}
              />
            )}
        </div>
      </div>
    </Card>
  );
}
