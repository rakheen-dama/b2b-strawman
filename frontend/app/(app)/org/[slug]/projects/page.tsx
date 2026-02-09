import { auth } from "@clerk/nextjs/server";
import { api, handleApiError } from "@/lib/api";
import type { Project, ProjectRole } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { CreateProjectDialog } from "@/components/projects/create-project-dialog";
import { UpgradePrompt } from "@/components/billing/upgrade-prompt";
import { formatDate } from "@/lib/format";
import { FolderOpen } from "lucide-react";
import Link from "next/link";

const PROJECT_ROLE_BADGE: Record<ProjectRole, { label: string; variant: "default" | "outline" }> = {
  lead: { label: "Lead", variant: "default" },
  member: { label: "Member", variant: "outline" },
};

export default async function ProjectsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const { orgRole, has } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";
  const isPro = has?.({ plan: "pro" }) ?? false;

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
          <p className="text-muted-foreground mt-1 text-sm">
            Manage your organization&apos;s projects.
          </p>
        </div>
        <CreateProjectDialog slug={slug} />
      </div>

      {projects.length === 0 ? (
        <div className="flex flex-col items-center justify-center rounded-lg border border-dashed py-12">
          <FolderOpen className="text-muted-foreground size-12" />
          <h2 className="mt-4 text-lg font-semibold">No projects yet</h2>
          <p className="text-muted-foreground mt-1 text-sm">
            {isAdmin
              ? "Create your first project to get started."
              : "You\u2019re not on any projects yet."}
          </p>
          <div className="mt-4">
            <CreateProjectDialog slug={slug} />
          </div>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {projects.map((project) => {
            const roleBadge = project.projectRole
              ? PROJECT_ROLE_BADGE[project.projectRole]
              : null;
            return (
              <Link
                key={project.id}
                href={`/org/${slug}/projects/${project.id}`}
                className="group"
              >
                <Card className="group-hover:border-foreground/20 transition-colors">
                  <CardHeader>
                    <div className="flex items-center gap-2">
                      <CardTitle className="line-clamp-1 text-base">{project.name}</CardTitle>
                      {roleBadge && (
                        <Badge variant={roleBadge.variant} className="shrink-0">
                          {roleBadge.label}
                        </Badge>
                      )}
                    </div>
                  </CardHeader>
                  <CardContent>
                    {project.description ? (
                      <p className="text-muted-foreground line-clamp-2 text-sm">
                        {project.description}
                      </p>
                    ) : (
                      <p className="text-muted-foreground/60 text-sm italic">No description</p>
                    )}
                    <p className="text-muted-foreground mt-3 text-xs">
                      Created{" "}
                      {formatDate(project.createdAt)}
                    </p>
                  </CardContent>
                </Card>
              </Link>
            );
          })}
        </div>
      )}

      {!isPro && <UpgradePrompt slug={slug} />}
    </div>
  );
}
