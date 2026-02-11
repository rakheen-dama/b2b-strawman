import { auth } from "@clerk/nextjs/server";
import { api, handleApiError } from "@/lib/api";
import type { Project, Document, ProjectMember, ProjectRole } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { EditProjectDialog } from "@/components/projects/edit-project-dialog";
import { DeleteProjectDialog } from "@/components/projects/delete-project-dialog";
import { DocumentsPanel } from "@/components/documents/documents-panel";
import { ProjectMembersPanel } from "@/components/projects/project-members-panel";
import { ProjectTabs } from "@/components/projects/project-tabs";
import { formatDate } from "@/lib/format";
import { ArrowLeft, Pencil, Trash2 } from "lucide-react";
import Link from "next/link";

const ROLE_BADGE: Record<ProjectRole, { label: string; variant: "lead" | "member" }> = {
  lead: { label: "Lead", variant: "lead" },
  member: { label: "Member", variant: "member" },
};

export default async function ProjectDetailPage({
  params,
}: {
  params: Promise<{ slug: string; id: string }>;
}) {
  const { slug, id } = await params;
  const { orgRole, userId } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";
  const isOwner = orgRole === "org:owner";

  let project: Project;
  try {
    project = await api.get<Project>(`/api/projects/${id}`);
  } catch (error) {
    handleApiError(error);
  }

  const canEdit = isAdmin || project.projectRole === "lead";
  const isCurrentLead = project.projectRole === "lead";
  const canManage = isAdmin || isCurrentLead;

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

  const roleBadge = project.projectRole ? ROLE_BADGE[project.projectRole] : null;

  return (
    <div className="space-y-8">
      {/* Back link */}
      <div>
        <Link
          href={`/org/${slug}/projects`}
          className="inline-flex items-center text-sm text-olive-600 transition-colors hover:text-olive-900 dark:text-olive-400 dark:hover:text-olive-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Projects
        </Link>
      </div>

      {/* Project Header (33.6) */}
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="flex items-center gap-3">
            <h1 className="font-display text-2xl text-olive-950 dark:text-olive-50">
              {project.name}
            </h1>
            {roleBadge && <Badge variant={roleBadge.variant}>{roleBadge.label}</Badge>}
          </div>
          {project.description ? (
            <p className="mt-2 text-olive-600 dark:text-olive-400">{project.description}</p>
          ) : (
            <p className="mt-2 text-sm italic text-olive-400 dark:text-olive-600">
              No description
            </p>
          )}
          <p className="mt-3 text-sm text-olive-400 dark:text-olive-600">
            Created {formatDate(project.createdAt)} &middot; {documents.length}{" "}
            {documents.length === 1 ? "document" : "documents"} &middot; {members.length}{" "}
            {members.length === 1 ? "member" : "members"}
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
                <Button variant="ghost" size="sm" className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300">
                  <Trash2 className="mr-1.5 size-4" />
                  Delete
                </Button>
              </DeleteProjectDialog>
            )}
          </div>
        )}
      </div>

      {/* Tabbed Content (33.7) */}
      <ProjectTabs
        documentsPanel={
          <DocumentsPanel documents={documents} projectId={id} slug={slug} />
        }
        membersPanel={
          <ProjectMembersPanel
            members={members}
            slug={slug}
            projectId={id}
            canManage={canManage}
            isCurrentLead={isCurrentLead}
            currentUserId={userId ?? ""}
          />
        }
      />
    </div>
  );
}
