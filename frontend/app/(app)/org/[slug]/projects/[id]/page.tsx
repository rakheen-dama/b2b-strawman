import { auth } from "@clerk/nextjs/server";
import { api, handleApiError } from "@/lib/api";
import type { Project } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { EditProjectDialog } from "@/components/projects/edit-project-dialog";
import { DeleteProjectDialog } from "@/components/projects/delete-project-dialog";
import { ArrowLeft, FileText, Pencil, Trash2 } from "lucide-react";
import Link from "next/link";

export default async function ProjectDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";
  const isOwner = orgRole === "org:owner";

  let project: Project;
  try {
    project = await api.get<Project>(`/api/projects/${id}`);
  } catch (error) {
    handleApiError(error);
  }

  return (
    <div className="space-y-6">
      <div>
        <Link
          href={`/org/${slug}/projects`}
          className="inline-flex items-center text-sm text-muted-foreground hover:text-foreground"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Projects
        </Link>
      </div>

      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <h1 className="text-2xl font-bold">{project.name}</h1>
          {project.description ? (
            <p className="mt-2 text-muted-foreground">
              {project.description}
            </p>
          ) : (
            <p className="mt-2 text-sm italic text-muted-foreground/60">
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
        </div>

        {isAdmin && (
          <div className="flex shrink-0 gap-2">
            {isAdmin && (
              <EditProjectDialog project={project} slug={slug}>
                <Button variant="outline" size="sm">
                  <Pencil className="mr-1.5 size-4" />
                  Edit
                </Button>
              </EditProjectDialog>
            )}
            {isOwner && (
              <DeleteProjectDialog
                slug={slug}
                projectId={project.id}
                projectName={project.name}
              >
                <Button variant="outline" size="sm">
                  <Trash2 className="mr-1.5 size-4" />
                  Delete
                </Button>
              </DeleteProjectDialog>
            )}
          </div>
        )}
      </div>

      <div className="rounded-lg border border-dashed p-8">
        <div className="flex flex-col items-center text-center">
          <FileText className="size-10 text-muted-foreground" />
          <h2 className="mt-3 text-lg font-semibold">Documents</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Document management will be available in a future update.
          </p>
        </div>
      </div>
    </div>
  );
}
