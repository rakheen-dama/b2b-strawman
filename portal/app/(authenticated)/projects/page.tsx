"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { FolderOpen } from "lucide-react";
import { portalGet } from "@/lib/api-client";
import { useBranding } from "@/hooks/use-branding";
import { ProjectCard } from "@/components/project-card";
import { Skeleton } from "@/components/ui/skeleton";
import { PendingAcceptancesList } from "@/components/pending-acceptances-list";
import { cn } from "@/lib/utils";
import type { PortalProject } from "@/lib/types";

type ProjectFilter = "all" | "active" | "past";

const PAST_STATUSES = new Set(["CLOSED", "COMPLETED", "CANCELLED"]);

const FILTER_OPTIONS: ReadonlyArray<{ value: ProjectFilter; label: string }> = [
  { value: "all", label: "All" },
  { value: "active", label: "Active" },
  { value: "past", label: "Past" },
];

function ProjectSkeleton() {
  return (
    <div className="flex flex-col gap-4 rounded-lg border border-slate-200/80 bg-white p-6 shadow-sm">
      <Skeleton className="h-5 w-2/3" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-4 w-1/2" />
      <div className="flex gap-4">
        <Skeleton className="h-3 w-20" />
        <Skeleton className="h-3 w-24" />
      </div>
    </div>
  );
}

export default function ProjectsPage() {
  const { orgName } = useBranding();
  const [projects, setProjects] = useState<PortalProject[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState<ProjectFilter>("all");

  const visibleProjects = useMemo(() => {
    if (filter === "all") return projects;
    if (filter === "active") {
      return projects.filter((p) => p.status === "ACTIVE");
    }
    return projects.filter((p) => p.status && PAST_STATUSES.has(p.status));
  }, [projects, filter]);

  const fetchProjects = useCallback(async () => {
    setError(null);
    setIsLoading(true);
    try {
      const data = await portalGet<PortalProject[]>("/portal/projects");
      setProjects(data);
    } catch (err) {
      setError(
        err instanceof Error ? err.message : "Failed to load projects",
      );
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchProjects();
  }, [fetchProjects]);

  return (
    <div>
      <PendingAcceptancesList />

      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        Your Projects
      </h1>

      {!isLoading && !error && projects.length > 0 && (
        <div
          role="tablist"
          aria-label="Filter projects"
          className="mb-4 inline-flex rounded-md border border-slate-200 bg-slate-50 p-1"
        >
          {FILTER_OPTIONS.map((option) => {
            const isActive = filter === option.value;
            return (
              <button
                key={option.value}
                type="button"
                role="tab"
                aria-selected={isActive}
                onClick={() => setFilter(option.value)}
                className={cn(
                  "min-h-9 rounded px-3 py-1.5 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-slate-900 text-white shadow-sm"
                    : "text-slate-600 hover:text-slate-900",
                )}
              >
                {option.label}
              </button>
            );
          })}
        </div>
      )}

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <ProjectSkeleton key={i} />
          ))}
        </div>
      )}

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          <p className="mb-2">{error}</p>
          <button
            type="button"
            onClick={() => fetchProjects()}
            className="inline-flex min-h-11 items-center rounded-md bg-white px-3 py-1.5 text-sm font-medium text-red-700 ring-1 ring-red-200 hover:bg-red-100"
          >
            Try again
          </button>
        </div>
      )}

      {!isLoading && !error && projects.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <FolderOpen className="mb-4 size-12 text-slate-300" />
          <p className="text-lg font-medium text-slate-600">No projects yet</p>
          <p className="mt-1 text-sm text-slate-500">
            Your {orgName} team will share projects with you here.
          </p>
        </div>
      )}

      {!isLoading &&
        !error &&
        projects.length > 0 &&
        visibleProjects.length === 0 && (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <FolderOpen className="mb-4 size-12 text-slate-300" />
            <p className="text-lg font-medium text-slate-600">No projects</p>
            <p className="mt-1 text-sm text-slate-500">
              Try a different filter to see more projects.
            </p>
          </div>
        )}

      {!isLoading && !error && visibleProjects.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {visibleProjects.map((project) => (
            <ProjectCard key={project.id} project={project} />
          ))}
        </div>
      )}
    </div>
  );
}
