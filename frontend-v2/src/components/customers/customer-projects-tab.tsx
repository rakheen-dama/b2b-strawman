"use client";

import { useParams, useRouter } from "next/navigation";
import { FolderOpen } from "lucide-react";

import { formatDate } from "@/lib/format";
import { Card, CardContent } from "@/components/ui/card";

interface LinkedProject {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
}

interface CustomerProjectsTabProps {
  projects: LinkedProject[];
}

export function CustomerProjectsTab({ projects }: CustomerProjectsTabProps) {
  const router = useRouter();
  const params = useParams<{ slug: string }>();

  if (projects.length === 0) {
    return (
      <div className="flex min-h-[200px] flex-col items-center justify-center rounded-lg bg-slate-50/50 px-6 py-12 text-center">
        <FolderOpen className="mb-3 size-10 text-slate-400" />
        <h3 className="text-base font-semibold text-slate-700">
          No linked projects
        </h3>
        <p className="mt-1 text-sm text-slate-500">
          Link projects to this customer from the project detail page.
        </p>
      </div>
    );
  }

  return (
    <div className="grid gap-3">
      {projects.map((project) => (
        <Card
          key={project.id}
          className="cursor-pointer transition-colors hover:bg-slate-50"
          onClick={() =>
            router.push(`/org/${params.slug}/projects/${project.id}`)
          }
        >
          <CardContent className="flex items-center justify-between py-3">
            <div>
              <p className="font-medium text-slate-900">{project.name}</p>
              {project.description && (
                <p className="mt-0.5 text-sm text-slate-500 line-clamp-1">
                  {project.description}
                </p>
              )}
            </div>
            <span className="shrink-0 text-xs text-slate-400">
              {formatDate(project.createdAt)}
            </span>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
