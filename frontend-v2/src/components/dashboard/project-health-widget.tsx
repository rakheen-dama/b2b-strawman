"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { FolderKanban } from "lucide-react";

import { Button } from "@/components/ui/button";
import { WidgetCard } from "@/components/layout/widget-grid";
import { HealthBadge } from "@/components/dashboard/health-badge";
import { CompletionBar } from "@/components/dashboard/completion-bar";
import { cn } from "@/lib/utils";
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

const MAX_VISIBLE = 8;

export function ProjectHealthWidget({
  projects,
  orgSlug,
}: ProjectHealthWidgetProps) {
  const [activeFilter, setActiveFilter] = useState<FilterTab>("ALL");
  const router = useRouter();

  const filtered =
    !projects || activeFilter === "ALL"
      ? projects
      : projects.filter((p) => p.healthStatus === activeFilter);

  const visible = filtered?.slice(0, MAX_VISIBLE) ?? [];

  return (
    <WidgetCard
      title="Project Health"
      viewAllHref={`/org/${orgSlug}/projects`}
    >
      {/* Filter tabs */}
      <div className="mb-4 flex gap-1">
        {FILTER_TABS.map((tab) => (
          <Button
            key={tab.value}
            variant={activeFilter === tab.value ? "outline" : "ghost"}
            size="xs"
            onClick={() => setActiveFilter(tab.value)}
            className={cn(
              activeFilter === tab.value &&
                "border-slate-400 dark:border-slate-600",
            )}
          >
            {tab.label}
          </Button>
        ))}
      </div>

      {!projects || projects.length === 0 ? (
        <div className="flex flex-col items-center gap-2 py-8 text-center">
          <FolderKanban className="size-8 text-slate-300 dark:text-slate-700" />
          <p className="text-sm text-slate-500">No projects yet</p>
        </div>
      ) : visible.length === 0 ? (
        <p className="py-4 text-center text-sm text-slate-500 italic">
          No {activeFilter === "AT_RISK" ? "at-risk" : "critical"} projects
        </p>
      ) : (
        <div className="space-y-1">
          {visible.map((project) => (
            <button
              key={project.projectId}
              type="button"
              onClick={() =>
                router.push(`/org/${orgSlug}/projects/${project.projectId}`)
              }
              className="flex w-full items-start gap-3 rounded-md px-3 py-2.5 text-left transition-colors hover:bg-slate-50 dark:hover:bg-slate-900"
            >
              <div className="mt-0.5 shrink-0">
                <HealthBadge status={project.healthStatus} />
              </div>
              <div className="min-w-0 flex-1 space-y-1.5">
                <div className="flex items-baseline gap-2">
                  <span className="truncate text-sm font-medium text-slate-900 dark:text-slate-100">
                    {project.projectName}
                  </span>
                  {project.customerName && (
                    <span className="truncate text-xs text-slate-500">
                      {project.customerName}
                    </span>
                  )}
                </div>
                <CompletionBar percent={project.completionPercent} />
                {project.healthReasons.length > 0 && (
                  <p className="truncate text-xs text-slate-500">
                    {project.healthReasons.join(" \u00B7 ")}
                  </p>
                )}
              </div>
            </button>
          ))}
        </div>
      )}
    </WidgetCard>
  );
}
