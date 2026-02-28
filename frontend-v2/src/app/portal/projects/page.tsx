"use client";

import { useEffect, useState } from "react";
import { FolderOpen, Loader2 } from "lucide-react";
import { PortalProjectCard } from "@/components/portal/portal-project-card";
import type { PortalProject } from "@/lib/types";

const TOKEN_KEY = "portal_token";

export default function PortalProjectsPage() {
  const [projects, setProjects] = useState<PortalProject[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchProjects() {
      const token = sessionStorage.getItem(TOKEN_KEY);
      if (!token) {
        setError("Not authenticated. Please sign in.");
        setLoading(false);
        return;
      }

      try {
        const backendUrl = process.env.NEXT_PUBLIC_BACKEND_URL ?? "";
        const res = await fetch(`${backendUrl}/portal/projects`, {
          headers: { Authorization: `Bearer ${token}` },
        });

        if (!res.ok) {
          if (res.status === 401 || res.status === 403) {
            setError("Session expired. Please sign in again.");
          } else {
            setError("Failed to load projects.");
          }
          return;
        }

        const data = await res.json();
        setProjects(data);
      } catch {
        setError("Failed to connect to server.");
      } finally {
        setLoading(false);
      }
    }

    fetchProjects();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Loader2 className="size-6 animate-spin text-slate-400" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-20">
        <p className="text-sm text-red-600 dark:text-red-400">{error}</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-display text-2xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">
          Projects
        </h1>
        <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
          Projects shared with you by your service provider.
        </p>
      </div>

      {projects.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-slate-200 py-16 dark:border-slate-800">
          <FolderOpen className="mb-3 size-10 text-slate-300 dark:text-slate-600" />
          <p className="text-sm font-medium text-slate-500 dark:text-slate-400">
            No projects yet
          </p>
          <p className="mt-1 text-xs text-slate-400 dark:text-slate-500">
            Projects linked to your account will appear here.
          </p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {projects.map((project) => (
            <PortalProjectCard key={project.id} project={project} />
          ))}
        </div>
      )}
    </div>
  );
}
