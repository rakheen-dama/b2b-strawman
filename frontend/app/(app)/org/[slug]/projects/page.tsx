import { auth } from "@clerk/nextjs/server";
import { api, handleApiError } from "@/lib/api";
import type { Project } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CreateProjectDialog } from "@/components/projects/create-project-dialog";
import { FolderOpen } from "lucide-react";
import Link from "next/link";

export default async function ProjectsPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let projects: Project[] = [];
  try {
    projects = await api.get<Project[]>("/api/projects");
  } catch (error) {
    handleApiError(error);
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Projects</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Manage your organization&apos;s projects.
          </p>
        </div>
        {isAdmin && <CreateProjectDialog slug={slug} />}
      </div>

      {projects.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed py-12">
          <FolderOpen className="size-12 text-muted-foreground" />
          <h2 className="mt-4 text-lg font-semibold">No projects yet</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {isAdmin
              ? "Create your first project to get started."
              : "Ask an admin to create a project."}
          </p>
          {isAdmin && (
            <div className="mt-4">
              <CreateProjectDialog slug={slug} />
            </div>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {projects.map((project) => (
            <Link
              key={project.id}
              href={`/org/${slug}/projects/${project.id}`}
              className="group"
            >
              <Card className="transition-colors group-hover:border-foreground/20">
                <CardHeader>
                  <CardTitle className="line-clamp-1 text-base">
                    {project.name}
                  </CardTitle>
                </CardHeader>
                <CardContent>
                  {project.description ? (
                    <p className="line-clamp-2 text-sm text-muted-foreground">
                      {project.description}
                    </p>
                  ) : (
                    <p className="text-sm italic text-muted-foreground/60">
                      No description
                    </p>
                  )}
                  <p className="mt-3 text-xs text-muted-foreground">
                    Created{" "}
                    {new Date(project.createdAt).toLocaleDateString(undefined, {
                      month: "short",
                      day: "numeric",
                      year: "numeric",
                    })}
                  </p>
                </CardContent>
              </Card>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
