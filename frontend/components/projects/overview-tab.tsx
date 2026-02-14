import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { OverviewHealthHeader } from "@/components/projects/overview-health-header";
import { OverviewMetricsStrip } from "@/components/projects/overview-metrics-strip";
import {
  fetchProjectHealthDetail,
  fetchProjectTaskSummary,
  fetchProjectMemberHours,
} from "@/lib/actions/dashboard";
import { fetchProjectActivity } from "@/lib/actions/activity";
import { api } from "@/lib/api";
import { resolveDateRange } from "@/lib/date-utils";
import { formatRelativeDate } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  CheckSquare,
  FileText,
  MessageSquare,
  Clock,
  Users,
  Activity,
  AlertTriangle,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import type { ProjectHealthDetail, MemberHoursEntry } from "@/lib/dashboard-types";
import type { Task, BudgetStatusResponse, ProjectProfitabilityResponse } from "@/lib/types";
import type { ActivityItem } from "@/lib/actions/activity";

interface OverviewTabProps {
  projectId: string;
  projectName: string;
  customerName: string | null;
  canManage: boolean;
  tasks: Task[];
  slug: string;
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

// --- Overdue helpers ---
function daysOverdue(dueDate: string): number {
  const now = new Date();
  const due = new Date(dueDate);
  const diffMs = now.getTime() - due.getTime();
  return Math.max(0, Math.floor(diffMs / (1000 * 60 * 60 * 24)));
}

export async function OverviewTab({
  projectId,
  projectName,
  customerName,
  canManage,
  tasks,
  slug,
}: OverviewTabProps) {
  const { from, to } = resolveDateRange({});

  const [healthResult, taskSummaryResult, memberHoursResult, budgetResult, profitabilityResult, activityResult] =
    await Promise.allSettled([
      fetchProjectHealthDetail(projectId),
      fetchProjectTaskSummary(projectId),
      fetchProjectMemberHours(projectId, from, to),
      api.get<BudgetStatusResponse>(`/api/projects/${projectId}/budget`).catch(() => null),
      canManage
        ? api.get<ProjectProfitabilityResponse>(`/api/projects/${projectId}/profitability`).catch(() => null)
        : Promise.resolve(null),
      fetchProjectActivity(projectId, undefined, 0, 5).catch(() => null),
    ]);

  const health = settled(healthResult);
  const _taskSummary = settled(taskSummaryResult);
  const memberHours = settled(memberHoursResult);
  const budgetStatus = settled(budgetResult);
  const profitability = settled(profitabilityResult);
  const activityResponse = settled(activityResult);

  // Compute margin from profitability data
  const marginPercent =
    profitability?.currencies?.[0]?.marginPercent ?? null;

  // Tasks: split overdue and upcoming
  const now = new Date();
  const overdueTasks = tasks
    .filter(
      (t) =>
        t.dueDate &&
        new Date(t.dueDate) < now &&
        t.status !== "DONE" &&
        t.status !== "CANCELLED"
    )
    .sort((a, b) => new Date(a.dueDate!).getTime() - new Date(b.dueDate!).getTime())
    .slice(0, 5);

  const upcomingTasks = tasks
    .filter(
      (t) =>
        t.dueDate &&
        new Date(t.dueDate) >= now &&
        t.status !== "DONE" &&
        t.status !== "CANCELLED"
    )
    .sort((a, b) => new Date(a.dueDate!).getTime() - new Date(b.dueDate!).getTime())
    .slice(0, 5);

  const activityItems: ActivityItem[] = activityResponse?.content ?? [];

  // Max hours for bar chart scaling
  const maxHours = memberHours
    ? Math.max(...memberHours.map((m) => m.totalHours), 1)
    : 1;

  return (
    <div className="space-y-6">
      {/* Health Header */}
      <OverviewHealthHeader
        health={health}
        projectName={projectName}
        customerName={customerName}
      />

      {/* Metrics Strip */}
      <OverviewMetricsStrip
        metrics={health?.metrics ?? null}
        memberHours={memberHours}
        budgetStatus={budgetStatus}
        marginPercent={marginPercent}
        showMargin={canManage}
      />

      {/* Two-column body */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* Left: Tasks Mini-list */}
        <Card>
          <CardHeader>
            <CardTitle>Tasks</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {overdueTasks.length === 0 && upcomingTasks.length === 0 && (
              <p className="text-sm italic text-muted-foreground">
                No tasks with due dates
              </p>
            )}

            {overdueTasks.length > 0 && (
              <div className="space-y-1.5">
                <h4 className="text-xs font-semibold uppercase tracking-wider text-red-600 dark:text-red-400">
                  Overdue
                </h4>
                {overdueTasks.map((task) => (
                  <div
                    key={task.id}
                    className="flex items-center gap-2 rounded-md border-l-2 border-red-500 py-1.5 pl-3 pr-2"
                  >
                    <AlertTriangle className="size-3.5 shrink-0 text-red-500" />
                    <span className="min-w-0 flex-1 truncate text-sm text-olive-700 dark:text-olive-300">
                      {task.title}
                    </span>
                    <span className="shrink-0 rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700 dark:bg-red-900 dark:text-red-300">
                      {daysOverdue(task.dueDate!)}d overdue
                    </span>
                  </div>
                ))}
              </div>
            )}

            {upcomingTasks.length > 0 && (
              <div className="space-y-1.5">
                <h4 className="text-xs font-semibold uppercase tracking-wider text-olive-500 dark:text-olive-400">
                  Upcoming
                </h4>
                {upcomingTasks.map((task) => (
                  <div
                    key={task.id}
                    className="flex items-center gap-2 py-1.5 pl-3 pr-2"
                  >
                    <CheckSquare className="size-3.5 shrink-0 text-olive-400 dark:text-olive-500" />
                    <span className="min-w-0 flex-1 truncate text-sm text-olive-700 dark:text-olive-300">
                      {task.title}
                    </span>
                    <span className="shrink-0 text-xs text-muted-foreground">
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
          <CardFooter>
            <Link
              href={`/org/${slug}/projects/${projectId}?tab=tasks`}
              className="text-sm text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300"
              onClick={undefined}
            >
              View all tasks &rarr;
            </Link>
          </CardFooter>
        </Card>

        {/* Right column */}
        <div className="space-y-6">
          {/* Team Hours */}
          <Card>
            <CardHeader>
              <CardTitle>Team Hours</CardTitle>
            </CardHeader>
            <CardContent>
              {!memberHours || memberHours.length === 0 ? (
                <p className="text-sm italic text-muted-foreground">
                  No hours logged this month
                </p>
              ) : (
                <div className="space-y-3">
                  {memberHours.map((member) => (
                    <div key={member.memberId} className="space-y-1">
                      <div className="flex items-center justify-between text-sm">
                        <span className="text-olive-700 dark:text-olive-300">
                          {member.memberName}
                        </span>
                        <span className="font-medium tabular-nums">
                          {member.totalHours.toFixed(1)}h
                        </span>
                      </div>
                      <div className="h-2 overflow-hidden rounded-full bg-olive-200 dark:bg-olive-700">
                        <div
                          className={cn(
                            "h-full rounded-full bg-indigo-500"
                          )}
                          style={{
                            width: `${Math.min(100, (member.totalHours / maxHours) * 100)}%`,
                          }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
            <CardFooter>
              <Link
                href={`/org/${slug}/projects/${projectId}?tab=time`}
                className="text-sm text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300"
              >
                View all time &rarr;
              </Link>
            </CardFooter>
          </Card>

          {/* Recent Activity */}
          <Card>
            <CardHeader>
              <CardTitle>Recent Activity</CardTitle>
            </CardHeader>
            <CardContent>
              {activityItems.length === 0 ? (
                <p className="text-sm italic text-muted-foreground">
                  No recent activity
                </p>
              ) : (
                <div className="space-y-1">
                  {activityItems.map((item) => {
                    const Icon = getEventIcon(item.eventType);
                    return (
                      <div
                        key={item.id}
                        className="flex items-start gap-3 rounded-md px-2 py-2"
                      >
                        <span className="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full bg-olive-200 text-xs font-medium text-olive-700 dark:bg-olive-700 dark:text-olive-200">
                          {getInitials(item.actorName)}
                        </span>
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-1.5">
                            <Icon className="size-3.5 shrink-0 text-olive-400 dark:text-olive-500" />
                            <p className="truncate text-sm text-olive-700 dark:text-olive-300">
                              {item.message}
                            </p>
                          </div>
                          <span className="text-xs text-olive-500 dark:text-olive-400">
                            {formatRelativeDate(item.occurredAt)}
                          </span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
            <CardFooter>
              <Link
                href={`/org/${slug}/projects/${projectId}?tab=activity`}
                className="text-sm text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300"
              >
                View all activity &rarr;
              </Link>
            </CardFooter>
          </Card>
        </div>
      </div>
    </div>
  );
}
