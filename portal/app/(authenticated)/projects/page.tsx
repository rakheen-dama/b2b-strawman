"use client";

import { useEffect, useState } from "react";
import { FolderOpen } from "lucide-react";
import { portalGet } from "@/lib/api-client";
import { useBranding } from "@/hooks/use-branding";
import { ProjectCard } from "@/components/project-card";
import { Skeleton } from "@/components/ui/skeleton";
import { PendingAcceptancesList } from "@/components/PendingAcceptancesList";
import type { PortalProject } from "@/lib/types";

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

  useEffect(() => {
    let cancelled = false;

    async function fetchProjects() {
      try {
        const data = await portalGet<PortalProject[]>("/portal/projects");
        if (!cancelled) {
          setProjects(data);
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : "Failed to load projects",
          );
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    fetchProjects();

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div>
      <PendingAcceptancesList />

      <h1 className="font-display mb-6 text-2xl font-semibold text-slate-900">
        Your Projects
      </h1>

      {isLoading && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <ProjectSkeleton key={i} />
          ))}
        </div>
      )}

      {error && (
        <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          {error}
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

      {!isLoading && !error && projects.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {projects.map((project) => (
            <ProjectCard key={project.id} project={project} />
          ))}
        </div>
      )}
    </div>
  );
}
