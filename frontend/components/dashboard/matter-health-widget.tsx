"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

import type { ReactNode } from "react";
import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  CheckCircle2,
  HeartPulse,
  HelpCircle,
  XCircle,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@b2mash/ui/button";
import { EmptyState } from "@/components/empty-state";
import { CompletionProgressBar } from "@/components/dashboard/completion-progress-bar";
import { StatusBadge, type StatusTone, WidgetCard } from "@/components/dashboard/widget-parts";
import type { MatterHealth } from "@/lib/dashboard-types";

interface MatterHealthWidgetProps {
  matters: MatterHealth[] | null;
  orgSlug: string;
}

type FilterTab = "ALL" | "AT_RISK" | "OVER_BUDGET";

const FILTER_TABS: { label: string; value: FilterTab }[] = [
  { label: "All", value: "ALL" },
  { label: "At Risk", value: "AT_RISK" },
  { label: "Over estimate", value: "OVER_BUDGET" },
];

type SortField = "health" | "budget" | "name";
type SortDirection = "asc" | "desc";

const MAX_VISIBLE_ROWS = 10;

/** Labels are HealthBadge's STATUS_LABELS (existing copy); token + icon + label (ADR-370). */
const HEALTH: Record<
  MatterHealth["healthStatus"],
  { label: string; tone: StatusTone; icon: ReactNode }
> = {
  HEALTHY: { label: "Healthy", tone: "success", icon: <CheckCircle2 /> },
  AT_RISK: { label: "At Risk", tone: "warning", icon: <AlertTriangle /> },
  CRITICAL: { label: "Critical", tone: "danger", icon: <XCircle /> },
  UNKNOWN: { label: "Unknown", tone: "neutral", icon: <HelpCircle /> },
};

// Column layout by the card's own width (container queries, 640B): at a half-width cell
// (~352px) the matter name keeps the room — Hours shows from @md, the health label from @lg
// (icon + sr-only label below it), Progress from @xl. Header and rows share these classes.
const COL_STATUS = "flex w-12 justify-center @lg:w-24";
const COL_PROGRESS = "hidden w-20 @xl:block";
const COL_HOURS = "hidden w-12 text-right @md:block";
const COL_TASKS = "w-12 text-right";
const HEADER_BUTTON =
  "focus-visible:ring-ring rounded-sm tracking-wider uppercase outline-none focus-visible:ring-2";

function getHealthOrder(status: string): number {
  switch (status) {
    case "CRITICAL":
      return 0;
    case "AT_RISK":
      return 1;
    case "HEALTHY":
      return 2;
    default:
      return 3;
  }
}

function formatTaskRatio(done: number, total: number): string {
  return `${done}/${total}`;
}

function formatHours(hours: number): string {
  if (hours === 0) return "0h";
  if (hours < 1) return `${Math.round(hours * 60)}m`;
  return `${hours.toFixed(1)}h`;
}

export function MatterHealthWidget({ matters: projects, orgSlug }: MatterHealthWidgetProps) {
  const [activeFilter, setActiveFilter] = useState<FilterTab>("ALL");
  const [sortField, setSortField] = useState<SortField>("health");
  const [sortDirection, setSortDirection] = useState<SortDirection>("asc");
  const router = useRouter();

  if (!projects || projects.length === 0) {
    return (
      <WidgetCard data-testid="matter-health-panel" title="Matter Health">
        <EmptyState
          variant="compact"
          icon={HeartPulse}
          title="No matters yet"
          description="Create a matter to start tracking health status."
        />
      </WidgetCard>
    );
  }

  const filteredProjects =
    activeFilter === "ALL"
      ? projects
      : activeFilter === "AT_RISK"
        ? projects.filter((p) => p.healthStatus === "AT_RISK" || p.healthStatus === "CRITICAL")
        : projects.filter((p) => p.budgetConsumedPercent != null && p.budgetConsumedPercent > 100);

  const sortedProjects = [...filteredProjects].sort((a, b) => {
    let cmp = 0;
    switch (sortField) {
      case "health":
        cmp = getHealthOrder(a.healthStatus) - getHealthOrder(b.healthStatus);
        break;
      case "budget":
        cmp = (b.budgetConsumedPercent ?? 0) - (a.budgetConsumedPercent ?? 0);
        break;
      case "name":
        cmp = a.matterName.localeCompare(b.matterName);
        break;
    }
    return sortDirection === "desc" ? -cmp : cmp;
  });

  const visibleProjects = sortedProjects.slice(0, MAX_VISIBLE_ROWS);

  function handleSort(field: SortField) {
    if (sortField === field) {
      setSortDirection((d) => (d === "asc" ? "desc" : "asc"));
    } else {
      setSortField(field);
      setSortDirection("asc");
    }
  }

  const SortIcon = sortDirection === "asc" ? ArrowUp : ArrowDown;

  return (
    <WidgetCard
      data-testid="matter-health-panel"
      title="Matter Health"
      contentProps={{ className: "@container space-y-3" }}
      footer={
        <Button
          variant="ghost"
          size="sm"
          className="text-muted-foreground h-7 text-xs"
          onClick={() => router.push(`/org/${orgSlug}/matters`)}
        >
          View all matters &rarr;
        </Button>
      }
    >
      {/* Filter Tabs */}
      <div className="flex gap-1">
        {FILTER_TABS.map((tab) => (
          <Button
            key={tab.value}
            variant={activeFilter === tab.value ? "outline" : "ghost"}
            size="sm"
            onClick={() => setActiveFilter(tab.value)}
            className={cn("h-7 text-xs", activeFilter === tab.value && "border-foreground/30")}
          >
            {tab.label}
          </Button>
        ))}
      </div>

      {/* Column Headers */}
      <div className="border-border text-muted-foreground flex items-center gap-2 border-b px-2 pb-1.5 text-[11px] font-medium tracking-wider uppercase">
        <button
          type="button"
          role="columnheader"
          onClick={() => handleSort("name")}
          className={cn(HEADER_BUTTON, "flex min-w-0 flex-1 items-center gap-1")}
          aria-label="Sort by matter name"
          aria-sort={
            sortField === "name" ? (sortDirection === "asc" ? "ascending" : "descending") : "none"
          }
        >
          Matter
          {sortField === "name" && <SortIcon className="size-3" />}
        </button>
        <button
          type="button"
          role="columnheader"
          onClick={() => handleSort("health")}
          className={cn(HEADER_BUTTON, COL_STATUS, "items-center gap-1")}
          aria-label="Sort by health status"
          aria-sort={
            sortField === "health" ? (sortDirection === "asc" ? "ascending" : "descending") : "none"
          }
        >
          Status
          {sortField === "health" && <SortIcon className="size-3" />}
        </button>
        <span className={cn(COL_PROGRESS, "text-center")}>Progress</span>
        <span className={COL_HOURS}>Hours</span>
        <span className={COL_TASKS}>Tasks</span>
      </div>

      {/* Matter Rows */}
      {visibleProjects.length === 0 ? (
        <p className="text-muted-foreground py-2 text-center text-xs italic">No matching matters</p>
      ) : (
        <div className="space-y-0">
          {visibleProjects.map((project) => {
            const health = HEALTH[project.healthStatus] ?? HEALTH.UNKNOWN;
            return (
              <button
                key={project.matterId}
                type="button"
                onClick={() => router.push(`/org/${orgSlug}/matters/${project.matterId}`)}
                className="hover:bg-muted focus-visible:ring-ring flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left transition-colors outline-none focus-visible:ring-2"
              >
                {/* Name + Client stacked */}
                <div className="min-w-0 flex-1">
                  <p className="text-foreground truncate text-sm font-medium">
                    {project.matterName}
                  </p>
                  {project.clientName && (
                    <p className="text-muted-foreground truncate text-[11px]">
                      {project.clientName}
                    </p>
                  )}
                </div>
                {/* Health: icon + label; the label is sr-only until the card is @lg wide */}
                <div className={COL_STATUS}>
                  <StatusBadge tone={health.tone} icon={health.icon} className="px-1 @lg:px-2">
                    <span className="sr-only @lg:not-sr-only">{health.label}</span>
                  </StatusBadge>
                </div>
                {/* Progress Bar */}
                <div className={COL_PROGRESS}>
                  <CompletionProgressBar percent={project.completionPercent} />
                </div>
                {/* Hours */}
                <span
                  className={cn(COL_HOURS, "text-muted-foreground font-mono text-xs tabular-nums")}
                >
                  {formatHours(project.hoursLogged)}
                </span>
                {/* Task Ratio */}
                <span
                  className={cn(COL_TASKS, "text-muted-foreground font-mono text-xs tabular-nums")}
                >
                  {formatTaskRatio(project.tasksDone, project.tasksTotal)}
                </span>
              </button>
            );
          })}
        </div>
      )}
    </WidgetCard>
  );
}
