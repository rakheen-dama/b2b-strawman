import { auth } from "@clerk/nextjs/server";
import { api, handleApiError } from "@/lib/api";
import type { Project, Document, ProjectMember, ProjectRole } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { EditProjectDialog } from "@/components/projects/edit-project-dialog";
import { DeleteProjectDialog } from "@/components/projects/delete-project-dialog";
import { DocumentsPanel } from "@/components/documents/documents-panel";
import { ProjectMembersPanel } from "@/components/projects/project-members-panel";
import { formatDate } from "@/lib/format";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import Link from "next/link";

const PROJECT_ROLE_BADGE: Record<ProjectRole, { label: string; variant: "default" | "outline" }> = {
  lead: { label: "Lead", variant: "default" },
  member: { label: "Member", variant: "outline" },
};

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

  const canEdit = isAdmin || project.projectRole === "lead";

  let documents: Document[] = [];
  try {
    documents = await api.get<Document[]>(`/api/projects/${id}/documents`);
  } catch {
    // Non-fatal: show empty documents list if fetch fails
  }

  let members: ProjectMember[] = [];
  try {
    members = await api.get<ProjectMember[]>(`/api/projects/${id}/members`);
  } catch {
    // Non-fatal: show empty members list if fetch fails
  }

  const roleBadge = project.projectRole ? PROJECT_ROLE_BADGE[project.projectRole] : null;

  return (
    <div className="space-y-6">
      <div>
        <Link
          href={`/org/${slug}/projects`}
          className="text-muted-foreground hover:text-foreground inline-flex items-center text-sm"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Projects
        </Link>
      </div>

      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold">{project.name}</h1>
            {roleBadge && <Badge variant={roleBadge.variant}>{roleBadge.label}</Badge>}
          </div>
          {project.description ? (
            <p className="text-muted-foreground mt-2">{project.description}</p>
          ) : (
            <p className="text-muted-foreground/60 mt-2 text-sm italic">No description</p>
          )}
          <p className="text-muted-foreground mt-3 text-xs">
            Created{" "}
            {formatDate(project.createdAt)}
          </p>
        </div>

        {(canEdit || isOwner) && (
          <div className="flex shrink-0 gap-2">
            {canEdit && (
              <EditProjectDialog project={project} slug={slug}>
                <Button variant="outline" size="sm">
                  <Pencil className="mr-1.5 size-4" />
                  Edit
                </Button>
              </EditProjectDialog>
            )}
            {isOwner && (
              <DeleteProjectDialog slug={slug} projectId={project.id} projectName={project.name}>
                <Button variant="outline" size="sm">
                  <Trash2 className="mr-1.5 size-4" />
                  Delete
                </Button>
              </DeleteProjectDialog>
            )}
          </div>
        )}
      </div>

      <DocumentsPanel documents={documents} projectId={id} slug={slug} />

      <ProjectMembersPanel members={members} />
    </div>
  );
}
