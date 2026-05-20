import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { FicaStatusCard } from "@/components/compliance/FicaStatusCard";
import { RetentionCard } from "@/components/legal/retention-card";
import type { SetupStep } from "@/components/setup/types";
import { HealthBadge } from "@/components/dashboard/health-badge";
import { CompletionProgressBar } from "@/components/dashboard/completion-progress-bar";
import {
  fetchProjectHealthDetail,
  fetchProjectTaskSummary,
  fetchProjectMemberHours,
  fetchProjectUpcomingDeadlines,
} from "@/lib/actions/dashboard";
import { UpcomingDeadlinesTile } from "@/components/projects/upcoming-deadlines-tile";
import { api } from "@/lib/api";
import { resolveDateRange } from "@/lib/date-utils";
import { cn } from "@/lib/utils";
import {
  DollarSign,
  Clock,
  CheckSquare,
  Calendar,
  Shield,
  Receipt,
  ChevronDown,
} from "lucide-react";
import Link from "next/link";
import type {
  BudgetStatusResponse,
  ProjectSetupStatus,
  FicaStatus,
} from "@/lib/types";
import type { ReactNode } from "react";

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

function settled<T>(result: PromiseSettledResult<T>): T | null {
  return result.status === "fulfilled" ? result.value : null;
}

// --- MetricCard sub-component (Task 534.1) ---
interface MetricCardProps {
  id: string;
  label: string;
  value: string;
  linkTab: string;
  icon: ReactNode;
  visible: boolean;
}

function MetricCard({ id, label, value, linkTab, icon, visible }: MetricCardProps) {
  if (!visible) return null;

  const content = (
    <Card
      data-testid={`metric-card-${id}`}
      className={cn("h-full", linkTab && "transition-shadow hover:shadow-md")}
    >
      <div className="flex flex-col gap-1 px-4 py-3">
        <div className="size-5 text-slate-400">{icon}</div>
        <span className="font-mono text-2xl font-bold tabular-nums">{value}</span>
        <span className="text-muted-foreground text-xs uppercase tracking-wider">{label}</span>
      </div>
    </Card>
  );

  if (linkTab) {
    return (
      <Link href={`?tab=${linkTab}`} className="block h-full">
        {content}
      </Link>
    );
  }

  return content;
}

// --- KPIDashboard props (Task 534.2) ---
export interface KPIDashboardProps {
  projectId: string;
  projectName: string;
  projectStatus: string;
  slug: string;
  canManage: boolean;
  customerName: string | null;
  customerId: string | null;
  setupStatus: ProjectSetupStatus | null;
  setupSteps: SetupStep[];
  ficaStatus: FicaStatus | null;
  retentionClockStartedAt: string | null;
  retentionEndsOn: string | null;
  trustEnabled: boolean;
  disbursementsEnabled: boolean;
  projectDueDate?: string | null;
}

// --- KPIDashboard component (Tasks 534.2 + 534.3) ---
export async function KPIDashboard({
  projectId,
  projectName,
  projectStatus,
  slug,
  canManage,
  customerName,
  customerId,
  setupStatus,
  setupSteps,
  ficaStatus,
  retentionClockStartedAt,
  retentionEndsOn,
  trustEnabled,
  disbursementsEnabled,
  projectDueDate,
}: KPIDashboardProps) {
  const { from, to } = resolveDateRange({});

  const [
    healthResult,
    taskSummaryResult,
    memberHoursResult,
    budgetResult,
    upcomingDeadlinesResult,
  ] = await Promise.allSettled([
    fetchProjectHealthDetail(projectId),
    fetchProjectTaskSummary(projectId),
    fetchProjectMemberHours(projectId, from, to),
    api.get<BudgetStatusResponse>(`/api/projects/${projectId}/budget`).catch(() => null),
    fetchProjectUpcomingDeadlines(projectId),
  ]);

  const health = settled(healthResult);
  const taskSummary = settled(taskSummaryResult);
  const memberHours = settled(memberHoursResult);
  const budgetStatus = settled(budgetResult);
  const upcomingDeadlines = settled(upcomingDeadlinesResult) ?? [];

  // Health status
  const healthStatus = health?.healthStatus ?? "UNKNOWN";
  const healthReasons = health?.healthReasons ?? [];

  // Metrics from health
  const metrics = health?.metrics ?? null;
  const totalHours = memberHours?.reduce((sum, m) => sum + m.totalHours, 0) ?? 0;
  const budgetPercent = budgetStatus?.hoursConsumedPct ?? null;

  // Task completion
  const tasksDone = metrics?.tasksDone ?? 0;
  const totalTasks = metrics?.totalTasks ?? 0;
  const taskCompletionPct = totalTasks > 0 ? Math.round((tasksDone / totalTasks) * 100) : 0;

  // Days to deadline
  const daysToDeadline = projectDueDate ? computeDaysToDeadline(projectDueDate) : null;

  // Setup progress computation
  const completedSteps = setupSteps.filter((s) => s.complete).length;
  const totalSteps = setupSteps.length;
  const setupIncomplete = setupStatus != null && completedSteps < totalSteps;

  // --- Metric card configurations (Task 534.3) ---
  const metricCards: MetricCardProps[] = [
    {
      id: "budget",
      label: "Budget consumed",
      value: budgetPercent != null ? `${Math.round(budgetPercent)}%` : "--",
      linkTab: "budget",
      icon: <DollarSign className="size-5" />,
      visible: budgetPercent != null,
    },
    {
      id: "hours",
      label: "Hours logged",
      value: `${totalHours.toFixed(1)}h`,
      linkTab: "time",
      icon: <Clock className="size-5" />,
      visible: true,
    },
    {
      id: "tasks",
      label: "Task completion",
      value: `${taskCompletionPct}%`,
      linkTab: "tasks",
      icon: <CheckSquare className="size-5" />,
      visible: true,
    },
    {
      id: "deadline",
      label: "Days to deadline",
      value: daysToDeadline != null ? `${daysToDeadline}d` : "--",
      linkTab: "",
      icon: <Calendar className="size-5" />,
      visible: daysToDeadline != null,
    },
    {
      id: "trust",
      label: "Trust balance",
      value: "--",
      linkTab: "trust",
      icon: <Shield className="size-5" />,
      visible: trustEnabled,
    },
    {
      id: "invoices",
      label: "Outstanding invoices",
      value: "--",
      linkTab: "statements",
      icon: <Receipt className="size-5" />,
      visible: disbursementsEnabled,
    },
  ];

  return (
    <div className="space-y-4">
      {/* Compact health header with colored band */}
      <div
        data-testid="project-health-header"
        className={cn(
          "bg-card rounded-lg border border-slate-200/60 shadow-sm",
          "border-t-4",
          HEALTH_BAND_COLORS[healthStatus] ?? HEALTH_BAND_COLORS.UNKNOWN
        )}
      >
        <div className="flex items-start gap-4 px-4 py-3">
          <HealthBadge status={healthStatus} size="lg" />
          {customerName && (
            <p className="text-sm text-slate-500 dark:text-slate-400">
              Customer: {customerName}
            </p>
          )}
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
      </div>

      {/* Compact setup checklist bar (auto-hide when complete) */}
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

      {/* Metric cards grid */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {metricCards.map((card) => (
          <MetricCard key={card.id} {...card} />
        ))}
      </div>

      {/* FICA status card (when customer is linked) */}
      {customerId && <FicaStatusCard ficaStatus={ficaStatus} slug={slug} />}

      {/* Retention card (self-gates on projectStatus) */}
      <RetentionCard
        status={projectStatus}
        retentionClockStartedAt={retentionClockStartedAt}
        retentionEndsOn={retentionEndsOn}
        slug={slug}
      />

      {/* Upcoming deadlines tile */}
      <UpcomingDeadlinesTile deadlines={upcomingDeadlines ?? []} />
    </div>
  );
}

/** Compute the number of days from today to the given ISO date string. */
function computeDaysToDeadline(isoDate: string): number {
  const [year, month, day] = isoDate.split("-").map(Number);
  const endUtc = Date.UTC(year, month - 1, day);
  const now = new Date();
  const todayUtc = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  const diffMs = endUtc - todayUtc;
  return Math.max(0, Math.floor(diffMs / (1000 * 60 * 60 * 24)));
}
