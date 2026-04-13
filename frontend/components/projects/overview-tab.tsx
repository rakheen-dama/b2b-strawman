import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { TerminologyText } from "@/components/terminology-text";
import type { SetupStep } from "@/components/setup/types";
import { HealthBadge } from "@/components/dashboard/health-badge";
import { CompletionProgressBar } from "@/components/dashboard/completion-progress-bar";
import { MicroStackedBar } from "@/components/dashboard/micro-stacked-bar";
import { DonutChart } from "@/components/dashboard/donut-chart";
import {
  fetchProjectHealthDetail,
  fetchProjectTaskSummary,
  fetchProjectMemberHours,
} from "@/lib/actions/dashboard";
import { fetchProjectActivity } from "@/lib/actions/activity";
import { api } from "@/lib/api";
import { resolveDateRange } from "@/lib/date-utils";
import { formatCurrency } from "@/lib/format";
import { RelativeDate } from "@/components/ui/relative-date";
import { cn } from "@/lib/utils";
import {
  CheckSquare,
  FileText,
  MessageSquare,
  Clock,
  Users,
  Activity,
  ChevronDown,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import type {
  Task,
  BudgetStatusResponse,
  ProjectProfitabilityResponse,
  ProjectSetupStatus,
  UnbilledTimeSummary,
  TemplateReadiness,
} from "@/lib/types";
import type { ActivityItem } from "@/lib/actions/activity";

interface OverviewTabProps {
  projectId: string;
  projectName: string;
  customerName: string | null;
  customerId: string | null;
  canManage: boolean;
  isAdmin: boolean;
  tasks: Task[];
  slug: string;
  setupStatus: ProjectSetupStatus | null;
  setupSteps: SetupStep[];
  unbilledSummary: UnbilledTimeSummary | null;
  templateReadiness: TemplateReadiness[];
}

function settled<T>(result: PromiseSettledResult<T>): T | null {
  return result.status === "fulfilled" ? result.value : null;
}

// --- Activity icon map ---
const ENTITY_ICON_MAP: Record<string, LucideIcon> = {
  task: CheckSquare,
  document: FileText,
  comment: MessageSquare,
  time_entry: Clock,
  project_member: Users,
};

function getEventIcon(eventType: string): LucideIcon {
  const entityPrefix = eventType.split(".")[0];
  return ENTITY_ICON_MAP[entityPrefix] ?? Activity;
}

function getInitials(name: string): string {
  return name
    .split(" ")
    .map((part) => part[0])
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase();
}

// --- Health band color mapping ---
const HEALTH_BAND_COLORS: Record<string, string> = {
  HEALTHY: "border-green-500",
  AT_RISK: "border-amber-500",
  CRITICAL: "border-red-500",
  UNKNOWN: "border-slate-400",
};

const REASON_BADGE_COLORS: Record<string, string> = {
  budget: "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200",
  overdue: "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  default: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
};

function getReasonBadgeColor(reason: string): string {
  const lower = reason.toLowerCase();
  if (lower.includes("budget")) return REASON_BADGE_COLORS.budget;
  if (lower.includes("overdue")) return REASON_BADGE_COLORS.overdue;
  return REASON_BADGE_COLORS.default;
}

export async function OverviewTab({
  projectId,
  projectName,
  customerName,
  customerId,
  canManage,
  isAdmin,
  tasks,
  slug,
  setupStatus,
  setupSteps,
  unbilledSummary,
  templateReadiness: _templateReadiness,
}: OverviewTabProps) {
  const { from, to } = resolveDateRange({});

  const [
    healthResult,
    taskSummaryResult,
    memberHoursResult,
    budgetResult,
    profitabilityResult,
    activityResult,
  ] = await Promise.allSettled([
    fetchProjectHealthDetail(projectId),
    fetchProjectTaskSummary(projectId),
    fetchProjectMemberHours(projectId, from, to),
    api.get<BudgetStatusResponse>(`/api/projects/${projectId}/budget`).catch(() => null),
    canManage
      ? api
          .get<ProjectProfitabilityResponse>(`/api/projects/${projectId}/profitability`)
          .catch(() => null)
      : Promise.resolve(null),
    fetchProjectActivity(projectId, undefined, 0, 10).catch(() => null),
  ]);

  const health = settled(healthResult);
  const taskSummary = settled(taskSummaryResult);
  const memberHours = settled(memberHoursResult);
  const budgetStatus = settled(budgetResult);
  const profitability = settled(profitabilityResult);
  const activityResponse = settled(activityResult);

  // Compute margin from profitability data
  const marginPercent = profitability?.currencies?.[0]?.marginPercent ?? null;

  // Health status
  const healthStatus = health?.healthStatus ?? "UNKNOWN";
  const healthReasons = health?.healthReasons ?? [];

  // Metrics from health
  const metrics = health?.metrics ?? null;
  const totalHours = memberHours?.reduce((sum, m) => sum + m.totalHours, 0) ?? 0;
  const budgetPercent = budgetStatus?.hoursConsumedPct ?? null;

  // Task summary for MicroStackedBar
  const taskTodo = taskSummary?.todo ?? 0;
  const taskInProgress = taskSummary?.inProgress ?? 0;
  const taskDone = taskSummary?.done ?? 0;
  const taskTotal = taskSummary?.total ?? 0;

  // Tasks: upcoming deadlines
  const now = new Date();
  const upcomingTasks = tasks
    .filter(
      (t) =>
        t.dueDate && new Date(t.dueDate) >= now && t.status !== "DONE" && t.status !== "CANCELLED"
    )
    .sort((a, b) => new Date(a.dueDate!).getTime() - new Date(b.dueDate!).getTime())
    .slice(0, 5);

  const activityItems: ActivityItem[] = activityResponse?.content ?? [];

  // Setup progress computation
  const completedSteps = setupSteps.filter((s) => s.complete).length;
  const totalSteps = setupSteps.length;
  const setupIncomplete = setupStatus != null && completedSteps < totalSteps;

  // DonutChart data for time breakdown by member
  const timeByMemberData: Array<{
    name: string;
    value: number;
    color?: string;
  }> = (memberHours ?? [])
    .filter((m) => m.totalHours > 0)
    .map((m) => ({
      name: m.memberName,
      value: Number(m.totalHours.toFixed(1)),
    }));

  return (
    <div className="space-y-4">
      {/* 395.1 — Health header colored band */}
      <div
        data-testid="project-health-header"
        className={cn(
          "bg-card rounded-lg border border-slate-200/60 shadow-sm",
          "border-t-4",
          HEALTH_BAND_COLORS[healthStatus] ?? HEALTH_BAND_COLORS.UNKNOWN
        )}
      >
        {/* Band header with health badge + project info */}
        <div className="flex items-start gap-4 px-4 pt-4 pb-2">
          <HealthBadge status={healthStatus} size="lg" />
          <div className="min-w-0 flex-1">
            <h2 className="font-display text-lg text-slate-950 dark:text-slate-50">
              {projectName}
            </h2>
            {customerName && (
              <p className="text-sm text-slate-500 dark:text-slate-400">Customer: {customerName}</p>
            )}
          </div>
        </div>

        {/* Health reasons badges */}
        {healthReasons.length > 0 && (
          <div className="flex flex-wrap gap-1.5 px-4 pb-2">
            {healthReasons.map((reason, i) => (
              <span
                key={i}
                className={cn(
                  "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium",
                  getReasonBadgeColor(reason)
                )}
              >
                {reason}
              </span>
            ))}
          </div>
        )}

        {/* Metrics strip below the band */}
        <div className="grid grid-cols-2 gap-3 border-t border-slate-200/60 px-4 py-3 sm:grid-cols-4 dark:border-slate-700/60">
          <div>
            <p className="text-[11px] font-medium tracking-wider text-slate-500 uppercase">
              Budget
            </p>
            <span className="font-mono text-lg font-bold tabular-nums">
              {budgetPercent != null ? `${Math.round(budgetPercent)}%` : "--"}
            </span>
            {budgetPercent != null && (
              <span className="text-muted-foreground ml-1 text-xs">used</span>
            )}
          </div>
          <div>
            <p className="text-[11px] font-medium tracking-wider text-slate-500 uppercase">Hours</p>
            <span className="font-mono text-lg font-bold tabular-nums">
              {totalHours.toFixed(1)}h
            </span>
          </div>
          <div>
            <p className="text-[11px] font-medium tracking-wider text-slate-500 uppercase">Tasks</p>
            <span className="font-mono text-lg font-bold tabular-nums">
              {metrics?.tasksDone ?? 0}/{metrics?.totalTasks ?? 0}
            </span>
            <span className="text-muted-foreground ml-1 text-xs">complete</span>
          </div>
          <div>
            <p className="text-[11px] font-medium tracking-wider text-slate-500 uppercase">
              Revenue
            </p>
            <span className="font-mono text-lg font-bold tabular-nums">
              {canManage && marginPercent != null ? `${marginPercent.toFixed(1)}%` : "--"}
            </span>
            {canManage && marginPercent != null && (
              <span className="text-muted-foreground ml-1 text-xs">margin</span>
            )}
          </div>
        </div>
      </div>

      {/* 395.2 — Compact setup checklist bar (auto-hide when complete) */}
      {setupIncomplete && (
        <details
          data-testid="setup-progress-bar"
          className="group bg-card rounded-lg border border-slate-200/60 shadow-sm"
        >
          <summary className="flex cursor-pointer items-center gap-3 px-4 py-3">
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
                  {completedSteps}/{totalSteps} setup steps complete
                </span>
              </div>
              <div className="mt-1.5">
                <CompletionProgressBar percent={(completedSteps / totalSteps) * 100} />
              </div>
            </div>
            <ChevronDown className="size-4 shrink-0 text-slate-400 transition-transform group-open:rotate-180" />
          </summary>
          <div className="border-t border-slate-200/60 px-4 py-3 dark:border-slate-700/60">
            <ul className="space-y-1.5">
              {setupSteps.map((step, i) => (
                <li key={i} className="flex items-center gap-2 text-sm">
                  <span
                    className={cn(
                      "inline-block size-2 rounded-full",
                      step.complete ? "bg-green-500" : "bg-slate-300"
                    )}
                  />
                  <span
                    className={cn(
                      step.complete
                        ? "text-slate-500 line-through"
                        : "text-slate-700 dark:text-slate-300"
                    )}
                  >
                    {step.label}
                  </span>
                  {!step.complete && step.actionHref && (!step.permissionRequired || canManage) && (
                    <Link
                      href={step.actionHref}
                      className="ml-auto text-xs text-teal-600 hover:text-teal-700 dark:text-teal-400"
                    >
                      Set up
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          </div>
        </details>
      )}

      {/* 395.3 — Two-panel body layout (60/40 split) */}
      <div className="grid gap-4 lg:grid-cols-5">
        {/* Left panel: activity, task status, upcoming deadlines */}
        <div data-testid="activity-tasks-panel" className="space-y-4 lg:col-span-3">
          {/* Recent Activity */}
          <Card>
            <CardHeader className="pb-2">
              <div className="flex items-center justify-between">
                <CardTitle className="text-sm font-medium">Recent Activity</CardTitle>
                <Link
                  href={`/org/${slug}/projects/${projectId}?tab=activity`}
                  className="text-xs text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
                >
                  View all
                </Link>
              </div>
            </CardHeader>
            <CardContent>
              {activityItems.length === 0 ? (
                <p className="text-muted-foreground text-sm italic">No recent activity</p>
              ) : (
                <div className="space-y-0.5">
                  {activityItems.map((item) => {
                    const Icon = getEventIcon(item.eventType);
                    return (
                      <div key={item.id} className="flex items-start gap-3 rounded-md px-2 py-1.5">
                        <span className="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full bg-slate-200 text-xs font-medium text-slate-700 dark:bg-slate-700 dark:text-slate-200">
                          {getInitials(item.actorName)}
                        </span>
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-1.5">
                            <Icon className="size-3.5 shrink-0 text-slate-400 dark:text-slate-500" />
                            <p className="truncate text-sm text-slate-700 dark:text-slate-300">
                              {item.message}
                            </p>
                          </div>
                          <RelativeDate
                            iso={item.occurredAt}
                            className="text-xs text-slate-500 dark:text-slate-400"
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Task Status MicroStackedBar */}
          <Card>
            <CardHeader className="pb-2">
              <div className="flex items-center justify-between">
                <CardTitle className="text-sm font-medium">Task Status</CardTitle>
                <Link
                  href={`/org/${slug}/projects/${projectId}?tab=tasks`}
                  className="text-xs text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
                >
                  View all tasks
                </Link>
              </div>
            </CardHeader>
            <CardContent>
              {taskTotal === 0 ? (
                <p className="text-muted-foreground text-sm italic">No tasks yet</p>
              ) : (
                <div className="space-y-2">
                  <MicroStackedBar
                    segments={[
                      {
                        value: taskDone,
                        color: "var(--color-green-500, #22c55e)",
                        label: "Done",
                      },
                      {
                        value: taskInProgress,
                        color: "var(--color-teal-500, #14b8a6)",
                        label: "In Progress",
                      },
                      {
                        value: taskTodo,
                        color: "var(--color-slate-300, #cbd5e1)",
                        label: "Open",
                      },
                    ]}
                    height={8}
                    className="w-full"
                  />
                  <div className="flex items-center gap-4 text-xs text-slate-500">
                    <span className="flex items-center gap-1">
                      <span className="inline-block size-2 rounded-full bg-green-500" />
                      Done {taskDone}
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="inline-block size-2 rounded-full bg-teal-500" />
                      In Progress {taskInProgress}
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="inline-block size-2 rounded-full bg-slate-300" />
                      Open {taskTodo}
                    </span>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Upcoming Task Deadlines */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium">Upcoming Deadlines</CardTitle>
            </CardHeader>
            <CardContent>
              {upcomingTasks.length === 0 ? (
                <p className="text-muted-foreground text-sm italic">No upcoming deadlines</p>
              ) : (
                <div className="space-y-1">
                  {upcomingTasks.map((task) => (
                    <div key={task.id} className="flex items-center gap-2 py-1.5 pr-2 pl-2">
                      <CheckSquare className="size-3.5 shrink-0 text-slate-400 dark:text-slate-500" />
                      <span className="min-w-0 flex-1 truncate text-sm text-slate-700 dark:text-slate-300">
                        {task.title}
                      </span>
                      <span className="text-muted-foreground shrink-0 text-xs">
                        due{" "}
                        {new Date(task.dueDate!).toLocaleDateString("en-US", {
                          month: "short",
                          day: "numeric",
                        })}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* Right panel: budget, time breakdown, team roster, unbilled callout */}
        <div data-testid="financial-team-panel" className="space-y-4 lg:col-span-2">
          {/* Budget Progress */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium">Budget</CardTitle>
            </CardHeader>
            <CardContent>
              {budgetPercent != null ? (
                <div className="space-y-2">
                  <div className="flex items-center justify-between text-sm">
                    <span className="font-mono font-bold tabular-nums">
                      {Math.round(budgetPercent)}%
                    </span>
                    <span className="text-muted-foreground text-xs">used</span>
                  </div>
                  <CompletionProgressBar percent={budgetPercent} />
                  {budgetStatus?.budgetHours != null && (
                    <div className="flex justify-between text-xs text-slate-500">
                      <span>{budgetStatus.hoursConsumed.toFixed(1)}h consumed</span>
                      <span>{budgetStatus.hoursRemaining.toFixed(1)}h remaining</span>
                    </div>
                  )}
                </div>
              ) : (
                <p className="text-muted-foreground text-sm italic">No budget configured</p>
              )}
            </CardContent>
          </Card>

          {/* Time Breakdown DonutChart */}
          <Card>
            <CardHeader className="pb-2">
              <div className="flex items-center justify-between">
                <CardTitle className="text-sm font-medium">Time Breakdown</CardTitle>
                <Link
                  href={`/org/${slug}/projects/${projectId}?tab=time`}
                  className="text-xs text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
                >
                  View all time
                </Link>
              </div>
            </CardHeader>
            <CardContent>
              {totalHours === 0 ? (
                <p className="text-muted-foreground text-sm italic">No hours logged this month</p>
              ) : (
                <DonutChart
                  data={timeByMemberData}
                  centerValue={`${totalHours.toFixed(1)}h`}
                  centerLabel="total"
                  height={180}
                />
              )}
            </CardContent>
          </Card>

          {/* Compact Team Roster */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium">Team</CardTitle>
            </CardHeader>
            <CardContent>
              {!memberHours || memberHours.length === 0 ? (
                <p className="text-muted-foreground text-sm italic">No team members assigned</p>
              ) : (
                <div className="flex flex-wrap gap-1.5">
                  {memberHours.map((member) => (
                    <span
                      key={member.memberId}
                      title={`${member.memberName} - ${member.totalHours.toFixed(1)}h`}
                      className="flex size-8 items-center justify-center rounded-full bg-slate-200 text-xs font-medium text-slate-700 dark:bg-slate-700 dark:text-slate-200"
                    >
                      {getInitials(member.memberName)}
                    </span>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Unbilled Time Callout */}
          {unbilledSummary && unbilledSummary.entryCount > 0 && (
            <Card
              data-testid="unbilled-callout"
              className="border-teal-200 bg-teal-50 dark:border-teal-800 dark:bg-teal-950"
            >
              <CardContent className="pt-4">
                <div className="flex items-start gap-3">
                  <Clock className="mt-0.5 size-4 shrink-0 text-teal-600 dark:text-teal-400" />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
                      Unbilled Time
                    </p>
                    <p className="mt-0.5 text-sm text-slate-600 dark:text-slate-400">
                      {formatCurrency(unbilledSummary.totalAmount, unbilledSummary.currency)} across{" "}
                      {unbilledSummary.totalHours.toFixed(1)} hours
                    </p>
                    <div className="mt-2 flex gap-2">
                      {isAdmin && customerId && (
                        <Link
                          href={`/org/${slug}/customers/${customerId}?tab=invoices`}
                          className="text-xs font-medium text-teal-600 hover:text-teal-700 dark:text-teal-400 dark:hover:text-teal-300"
                        >
                          <TerminologyText template="Generate {Invoice}" />
                        </Link>
                      )}
                      <Link
                        href="?tab=time"
                        className="text-xs text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-300"
                      >
                        View Entries
                      </Link>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
