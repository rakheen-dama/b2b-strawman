"use client";

import { useState } from "react";
import { useRouter, usePathname } from "next/navigation";

import { HeartPulse } from "lucide-react";
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
import { HelpTip } from "@/components/help-tip";
import { useTerminology } from "@/lib/terminology";
import { HealthBadge } from "@/components/dashboard/health-badge";
import { CompletionProgressBar } from "@/components/dashboard/completion-progress-bar";
import type { ProjectHealth } from "@/lib/dashboard-types";

interface ProjectHealthWidgetProps {
  projects: ProjectHealth[] | null;
  orgSlug: string;
}

type FilterTab = "ALL" | "AT_RISK" | "CRITICAL";

const FILTER_TABS: { label: string; value: FilterTab }[] = [
  { label: "All", value: "ALL" },
  { label: "At Risk", value: "AT_RISK" },
  { label: "Critical", value: "CRITICAL" },
];

const MAX_VISIBLE_ROWS = 10;

export function ProjectHealthWidget({
  projects,
  orgSlug,
}: ProjectHealthWidgetProps) {
  const [activeFilter, setActiveFilter] = useState<FilterTab>("ALL");
  const router = useRouter();
  const pathname = usePathname();
  const { t } = useTerminology();

  if (!projects || projects.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            Project Health
            <HelpTip code="dashboard.projectHealth" />
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
      : projects.filter((p) => p.healthStatus === activeFilter);

  const visibleProjects = filteredProjects.slice(0, MAX_VISIBLE_ROWS);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Project Health</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Filter Tabs */}
        <div className="flex gap-1">
          {FILTER_TABS.map((tab) => (
            <Button
              key={tab.value}
              variant={activeFilter === tab.value ? "outline" : "ghost"}
              size="sm"
              onClick={() => setActiveFilter(tab.value)}
              className={cn(
                activeFilter === tab.value &&
                  "border-slate-400 dark:border-slate-600"
              )}
            >
              {tab.label}
            </Button>
          ))}
        </div>

        {/* Project Rows */}
        {visibleProjects.length === 0 ? (
          <p className="text-sm text-muted-foreground italic">
            No {activeFilter === "AT_RISK" ? "at-risk" : "critical"} projects
          </p>
        ) : (
          <div className="space-y-2">
            {visibleProjects.map((project) => (
              <button
                key={project.projectId}
                type="button"
                onClick={() =>
                  router.push(
                    `/org/${orgSlug}/projects/${project.projectId}`
                  )
                }
                className="flex w-full items-start gap-3 rounded-md px-3 py-2.5 text-left transition-colors hover:bg-slate-50 dark:hover:bg-slate-900"
              >
                <div className="mt-1.5 shrink-0">
                  <HealthBadge status={project.healthStatus} size="sm" />
                </div>
                <div className="min-w-0 flex-1 space-y-1">
                  <div className="flex items-baseline gap-2">
                    <span className="truncate font-medium text-sm">
                      {project.projectName}
                    </span>
                    {project.customerName && (
                      <span className="truncate text-xs text-muted-foreground">
                        {project.customerName}
                      </span>
                    )}
                  </div>
                  <CompletionProgressBar percent={project.completionPercent} />
                  {project.healthReasons.length > 0 && (
                    <p className="text-xs text-muted-foreground truncate">
                      {project.healthReasons.join(" \u00B7 ")}
                    </p>
                  )}
                </div>
              </button>
            ))}
          </div>
        )}
      </CardContent>
      <CardFooter>
        <Button
          variant="ghost"
          size="sm"
          className="text-muted-foreground"
          onClick={() => router.push(`/org/${orgSlug}/projects`)}
        >
          View all {t("projects")} &rarr;
        </Button>
      </CardFooter>
    </Card>
  );
}
