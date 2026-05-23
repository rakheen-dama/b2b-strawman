"use client";

import { AlertTriangle, Calendar } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { TagInput } from "@/components/tags/TagInput";
import { formatDate, formatLocalDate, isOverdue } from "@/lib/format";
import { cn } from "@/lib/utils";
import type { ProjectStatus, TagResponse } from "@/lib/types";

interface MatterDetailsTabProps {
  projectId: string;
  description: string | null;
  priority: string | null;
  dueDate: string | null;
  createdAt: string;
  projectStatus: ProjectStatus;
  projectTags: TagResponse[];
  allTags: TagResponse[];
  canEdit: boolean;
  isAdmin: boolean;
  slug: string;
}

export function MatterDetailsTab({
  projectId,
  description,
  priority,
  dueDate,
  createdAt,
  projectStatus,
  projectTags,
  allTags,
  canEdit,
  isAdmin,
  slug,
}: MatterDetailsTabProps) {
  return (
    <div className="space-y-8" data-testid="matter-details-tab">
      {/* Description */}
      <section className="space-y-2">
        <h3 className="text-sm font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
          Description
        </h3>
        {description ? (
          <p className="max-w-prose whitespace-pre-wrap text-sm leading-relaxed text-slate-700 dark:text-slate-300">
            {description}
          </p>
        ) : (
          <p className="text-sm text-slate-400 italic dark:text-slate-500">No description</p>
        )}
      </section>

      {/* Key metadata grid */}
      <section className="space-y-4">
        <h3 className="text-sm font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
          Details
        </h3>
        <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {/* Priority */}
          {priority && (
            <div>
              <dt className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                Priority
              </dt>
              <dd className="mt-1">
                <Badge
                  variant={
                    priority === "HIGH"
                      ? "warning"
                      : priority === "MEDIUM"
                        ? "neutral"
                        : "success"
                  }
                >
                  {priority.charAt(0) + priority.slice(1).toLowerCase()}
                </Badge>
              </dd>
            </div>
          )}

          {/* Due Date */}
          {dueDate && (
            <div>
              <dt className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
                Due Date
              </dt>
              <dd
                className={cn(
                  "mt-1 inline-flex items-center gap-1 text-sm",
                  projectStatus === "ACTIVE" && isOverdue(dueDate)
                    ? "font-medium text-red-600 dark:text-red-400"
                    : "text-slate-700 dark:text-slate-300"
                )}
              >
                {projectStatus === "ACTIVE" && isOverdue(dueDate) ? (
                  <AlertTriangle className="size-3.5 text-red-600" />
                ) : (
                  <Calendar className="size-3.5" />
                )}
                {formatLocalDate(dueDate)}
              </dd>
            </div>
          )}

          {/* Created */}
          <div>
            <dt className="text-xs font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
              Created
            </dt>
            <dd className="mt-1 text-sm text-slate-700 dark:text-slate-300">
              {formatDate(createdAt)}
            </dd>
          </div>
        </dl>
      </section>

      {/* Tags */}
      <section className="space-y-2">
        <h3 className="text-sm font-medium tracking-wide text-slate-500 uppercase dark:text-slate-400">
          Tags
        </h3>
        <TagInput
          entityType="PROJECT"
          entityId={projectId}
          tags={projectTags}
          allTags={allTags}
          editable={canEdit}
          canInlineCreate={isAdmin}
          slug={slug}
        />
      </section>
    </div>
  );
}
