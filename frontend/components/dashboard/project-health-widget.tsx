"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

import { HeartPulse, ArrowUp, ArrowDown } from "lucide-react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/empty-state";
import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardFooter,
} from "@/components/ui/card";
import { useTerminology } from "@/lib/terminology";
import { HealthBadge } from "@/components/dashboard/health-badge";
import { CompletionProgressBar } from "@/components/dashboard/completion-progress-bar";
import type { ProjectHealth } from "@/lib/dashboard-types";

interface ProjectHealthWidgetProps {
  projects: ProjectHealth[] | null;
  orgSlug: string;
}

type FilterTab = "ALL" | "AT_RISK" | "OVER_BUDGET";

const FILTER_TABS: { label: string; value: FilterTab }[] = [
  { label: "All", value: "ALL" },
  { label: "At Risk", value: "AT_RISK" },
  { label: "Over Budget", value: "OVER_BUDGET" },
];

type SortField = "health" | "budget" | "name";
type SortDirection = "asc" | "desc";

const MAX_VISIBLE_ROWS = 10;

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

export function ProjectHealthWidget({
  projects,
  orgSlug,
}: ProjectHealthWidgetProps) {
  const [activeFilter, setActiveFilter] = useState<FilterTab>("ALL");
  const [sortField, setSortField] = useState<SortField>("health");
  const [sortDirection, setSortDirection] = useState<SortDirection>("asc");
  const router = useRouter();
  const { t } = useTerminology();

  if (!projects || projects.length === 0) {
    return (
      <Card data-testid="project-health-panel">
        <CardHeader>
          <CardTitle className="text-sm font-medium">
            Project Health
          </CardTitle>
        </CardHeader>
        <CardContent>
          <EmptyState
            icon={HeartPulse}
            title={`No ${t("projects")} yet`}
            description={`Create a ${t("project")} to start tracking health status.`}
          />
        </CardContent>
      </Card>
    );
  }

  const filteredProjects =
    activeFilter === "ALL"
      ? projects
      : activeFilter === "AT_RISK"
        ? projects.filter(
            (p) => p.healthStatus === "AT_RISK" || p.healthStatus === "CRITICAL",
          )
        : projects.filter(
            (p) =>
              p.budgetConsumedPercent != null && p.budgetConsumedPercent > 100,
          );

  const sortedProjects = [...filteredProjects].sort((a, b) => {
    let cmp = 0;
    switch (sortField) {
      case "health":
        cmp = getHealthOrder(a.healthStatus) - getHealthOrder(b.healthStatus);
        break;
      case "budget":
        cmp =
          (b.budgetConsumedPercent ?? 0) - (a.budgetConsumedPercent ?? 0);
        break;
      case "name":
        cmp = a.projectName.localeCompare(b.projectName);
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
    <Card data-testid="project-health-panel">
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium">Project Health</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 pt-0">
        {/* Filter Tabs */}
        <div className="flex gap-1">
          {FILTER_TABS.map((tab) => (
            <Button
              key={tab.value}
              variant={activeFilter === tab.value ? "outline" : "ghost"}
              size="sm"
              onClick={() => setActiveFilter(tab.value)}
              className={cn(
                "h-7 text-xs",
                activeFilter === tab.value &&
                  "border-slate-400 dark:border-slate-600",
              )}
            >
              {tab.label}
            </Button>
          ))}
        </div>

        {/* Column Headers */}
        <div className="flex items-center gap-2 border-b border-slate-100 px-2 pb-1.5 text-[11px] font-medium uppercase tracking-wider text-slate-400 dark:border-slate-800">
          <button
            type="button"
            role="columnheader"
            onClick={() => handleSort("name")}
            className="flex min-w-0 flex-1 items-center gap-1"
            aria-label="Sort by project name"
            aria-sort={sortField === "name" ? (sortDirection === "asc" ? "ascending" : "descending") : "none"}
          >
            Project
            {sortField === "name" && <SortIcon className="size-3" />}
          </button>
          <button
            type="button"
            role="columnheader"
            onClick={() => handleSort("health")}
            className="flex w-12 items-center justify-center gap-1"
            aria-label="Sort by health status"
            aria-sort={sortField === "health" ? (sortDirection === "asc" ? "ascending" : "descending") : "none"}
          >
            Status
            {sortField === "health" && <SortIcon className="size-3" />}
          </button>
          <span className="w-20 text-center">Progress</span>
          <span className="w-12 text-right">Hours</span>
          <span className="w-12 text-right">Tasks</span>
        </div>

        {/* Project Rows */}
        {visibleProjects.length === 0 ? (
          <p className="py-2 text-center text-xs italic text-slate-500">
            No matching projects
          </p>
        ) : (
          <div className="space-y-0">
            {visibleProjects.map((project) => (
              <button
                key={project.projectId}
                type="button"
                onClick={() =>
                  router.push(
                    `/org/${orgSlug}/projects/${project.projectId}`,
                  )
                }
                className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left transition-colors hover:bg-slate-50 dark:hover:bg-slate-900"
              >
                {/* Name + Customer stacked */}
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">
                    {project.projectName}
                  </p>
                  {project.customerName && (
                    <p className="truncate text-[11px] text-slate-500">
                      {project.customerName}
                    </p>
                  )}
                </div>
                {/* Health Badge */}
                <div className="flex w-12 justify-center">
                  <HealthBadge status={project.healthStatus} size="sm" />
                </div>
                {/* Progress Bar */}
                <div className="w-20">
                  <CompletionProgressBar
                    percent={project.completionPercent}
                  />
                </div>
                {/* Hours */}
                <span className="w-12 text-right font-mono text-xs tabular-nums text-slate-600 dark:text-slate-400">
                  {formatHours(project.hoursLogged)}
                </span>
                {/* Task Ratio */}
                <span className="w-12 text-right font-mono text-xs tabular-nums text-slate-600 dark:text-slate-400">
                  {formatTaskRatio(project.tasksDone, project.tasksTotal)}
                </span>
              </button>
            ))}
          </div>
        )}
      </CardContent>
      <CardFooter className="pt-0">
        <Button
          variant="ghost"
          size="sm"
          className="h-7 text-xs text-slate-500"
          onClick={() => router.push(`/org/${orgSlug}/projects`)}
        >
          View all {t("projects")} &rarr;
        </Button>
      </CardFooter>
    </Card>
  );
}
