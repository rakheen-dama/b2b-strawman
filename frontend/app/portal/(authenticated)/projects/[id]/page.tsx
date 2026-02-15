"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Loader2, AlertCircle } from "lucide-react";
import { portalApi, PortalApiError, clearPortalAuth } from "@/lib/portal-api";
import { PortalDocumentTable } from "@/components/portal/portal-document-table";
import type { PortalProject, PortalDocument } from "@/lib/types";

export default function PortalProjectDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [project, setProject] = useState<PortalProject | null>(null);
  const [documents, setDocuments] = useState<PortalDocument[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchData() {
      try {
        const [projectData, docsData] = await Promise.all([
          portalApi.get<PortalProject[]>("/portal/projects"),
          portalApi.get<PortalDocument[]>(`/portal/projects/${params.id}/documents`),
        ]);
        // Find this project in the list
        const found = projectData.find((p) => p.id === params.id);
        if (!found) {
          setError("Project not found or you don't have access");
          setIsLoading(false);
          return;
        }
        setProject(found);
        setDocuments(docsData);
      } catch (err) {
        if (err instanceof PortalApiError && err.status === 401) {
          clearPortalAuth();
          router.replace("/portal");
          return;
        }
        setError(err instanceof Error ? err.message : "Failed to load project");
      } finally {
        setIsLoading(false);
      }
    }
    fetchData();
  }, [params.id, router]);

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-8 animate-spin text-slate-400" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-4">
        <Link
          href="/portal/projects"
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Projects
        </Link>
        <div className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300" role="alert">
          <AlertCircle className="size-4 shrink-0" />
          {error}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Back link */}
      <div>
        <Link
          href="/portal/projects"
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Projects
        </Link>
      </div>

      {/* Project header */}
      <div>
        <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">
          {project?.name}
        </h1>
        {project?.description ? (
          <p className="mt-2 text-slate-600 dark:text-slate-400">
            {project.description}
          </p>
        ) : (
          <p className="mt-2 text-sm italic text-slate-400 dark:text-slate-600">
            No description
          </p>
        )}
      </div>

      {/* Documents */}
      <PortalDocumentTable documents={documents} title="Project Documents" />
    </div>
  );
}
