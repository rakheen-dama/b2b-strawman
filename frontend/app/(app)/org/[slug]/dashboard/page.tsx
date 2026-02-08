import { auth } from "@clerk/nextjs/server";
import { api, handleApiError } from "@/lib/api";
import type { Project } from "@/lib/types";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { formatDateShort } from "@/lib/format";
import { FolderOpen, Users, ArrowRight, Plus } from "lucide-react";
import Link from "next/link";

export default async function OrgDashboardPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const { orgSlug, orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let projects: Project[] = [];
  try {
    projects = await api.get<Project[]>("/api/projects");
  } catch (error) {
    handleApiError(error);
  }

  const recentProjects = [...projects]
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .slice(0, 5);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          Welcome to your <span className="text-foreground font-medium">{orgSlug}</span> workspace.
        </p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardDescription>{isAdmin ? "All Projects" : "Your Projects"}</CardDescription>
            <FolderOpen className="text-muted-foreground size-4" />
          </CardHeader>
          <CardContent>
            <p className="text-2xl font-bold">{projects.length}</p>
          </CardContent>
        </Card>
      </div>

      {/* Quick Actions */}
      <div className="flex flex-wrap gap-2">
        <Button asChild size="sm">
          <Link href={`/org/${slug}/projects`}>
            <Plus className="mr-1.5 size-4" />
            New Project
          </Link>
        </Button>
        <Button asChild variant="outline" size="sm">
          <Link href={`/org/${slug}/projects`}>
            <FolderOpen className="mr-1.5 size-4" />
            View Projects
          </Link>
        </Button>
        <Button asChild variant="outline" size="sm">
          <Link href={`/org/${slug}/team`}>
            <Users className="mr-1.5 size-4" />
            Manage Team
          </Link>
        </Button>
      </div>

      {/* Recent Projects */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-base">Recent Projects</CardTitle>
            {projects.length > 5 && (
              <Button asChild variant="ghost" size="sm">
                <Link href={`/org/${slug}/projects`}>
                  View all
                  <ArrowRight className="ml-1 size-4" />
                </Link>
              </Button>
            )}
          </div>
        </CardHeader>
        <CardContent>
          {recentProjects.length === 0 ? (
            <p className="text-muted-foreground text-sm">
              No projects yet.{" "}
              <Link
                href={`/org/${slug}/projects`}
                className="text-foreground underline underline-offset-4"
              >
                Create your first project
              </Link>
            </p>
          ) : (
            <ul className="divide-y">
              {recentProjects.map((project) => (
                <li key={project.id}>
                  <Link
                    href={`/org/${slug}/projects/${project.id}`}
                    className="hover:bg-muted/50 -mx-2 flex items-center justify-between rounded-md px-2 py-3 transition-colors"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{project.name}</p>
                      {project.description && (
                        <p className="text-muted-foreground truncate text-xs">
                          {project.description}
                        </p>
                      )}
                    </div>
                    <span className="text-muted-foreground ml-4 shrink-0 text-xs">
                      {formatDateShort(project.createdAt)}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
