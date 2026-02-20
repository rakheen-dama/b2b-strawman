import { auth } from "@clerk/nextjs/server";
import { api, handleApiError } from "@/lib/api";
import { getSchedule, getExecutions } from "@/lib/api/schedules";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ScheduleEditDialog } from "@/components/schedules/ScheduleEditDialog";
import { ExecutionHistory } from "@/components/schedules/ExecutionHistory";
import { ScheduleDetailActions } from "@/components/schedules/ScheduleDetailActions";
import { formatDate } from "@/lib/format";
import { FREQUENCY_LABELS } from "@/lib/schedule-constants";
import { ArrowLeft, Pencil } from "lucide-react";
import Link from "next/link";
import type { ScheduleResponse, ScheduleExecutionResponse } from "@/lib/api/schedules";
import type { OrgMember } from "@/lib/types";

function statusBadge(status: ScheduleResponse["status"]) {
  switch (status) {
    case "ACTIVE":
      return <Badge variant="success">Active</Badge>;
    case "PAUSED":
      return <Badge variant="warning">Paused</Badge>;
    case "COMPLETED":
      return <Badge variant="neutral">Completed</Badge>;
  }
}

export default async function ScheduleDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let schedule: ScheduleResponse;
  try {
    schedule = await getSchedule(id);
  } catch (error) {
    handleApiError(error);
  }

  let executions: ScheduleExecutionResponse[] = [];
  try {
    executions = await getExecutions(id);
  } catch {
    // Non-fatal: show empty execution history if fetch fails
  }

  let orgMembers: OrgMember[] = [];
  if (isAdmin) {
    try {
      orgMembers = await api.get<OrgMember[]>("/api/members");
    } catch {
      // Non-fatal: edit dialog won't show member picker
    }
  }

  return (
    <div className="space-y-8">
      {/* Back link */}
      <div>
        <Link
          href={`/org/${slug}/schedules`}
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Schedules
        </Link>
      </div>

      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">
              {schedule.nameOverride ?? schedule.templateName}
            </h1>
            {statusBadge(schedule.status)}
          </div>
          <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">
            {schedule.customerName} / {schedule.templateName}
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <Badge variant="neutral">{FREQUENCY_LABELS[schedule.frequency]}</Badge>
            <span className="text-sm text-slate-500 dark:text-slate-400">
              Starts {formatDate(schedule.startDate)}
            </span>
            {schedule.endDate && (
              <span className="text-sm text-slate-500 dark:text-slate-400">
                Ends {formatDate(schedule.endDate)}
              </span>
            )}
            <span className="text-sm text-slate-500 dark:text-slate-400">
              Lead time: {schedule.leadTimeDays} days
            </span>
            {schedule.projectLeadName && (
              <span className="text-sm text-slate-500 dark:text-slate-400">
                Lead: {schedule.projectLeadName}
              </span>
            )}
          </div>
          <p className="mt-2 text-sm text-slate-400 dark:text-slate-600">
            {schedule.executionCount} execution{schedule.executionCount !== 1 ? "s" : ""}
            {schedule.lastExecutedAt && (
              <> &middot; Last run {formatDate(schedule.lastExecutedAt)}</>
            )}
            {schedule.nextExecutionDate && schedule.status === "ACTIVE" && (
              <> &middot; Next run {formatDate(schedule.nextExecutionDate)}</>
            )}
          </p>
        </div>

        {isAdmin && schedule.status !== "COMPLETED" && (
          <div className="flex shrink-0 gap-2">
            <ScheduleEditDialog slug={slug} schedule={schedule} orgMembers={orgMembers}>
              <Button variant="outline" size="sm">
                <Pencil className="mr-1.5 size-4" />
                Edit
              </Button>
            </ScheduleEditDialog>
            <ScheduleDetailActions slug={slug} schedule={schedule} />
          </div>
        )}
      </div>

      {/* Execution History */}
      <div className="space-y-4">
        <h2 className="font-display text-lg text-slate-950 dark:text-slate-50">
          Execution History
        </h2>
        <ExecutionHistory executions={executions} slug={slug} />
      </div>
    </div>
  );
}
