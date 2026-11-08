"use server";

import { api, ApiError } from "@/lib/api";
import {
  createMatterComment,
  deleteMatterComment,
  listMatterComments,
  listMatterMembers,
  updateMatterComment,
} from "@/lib/api/matters";
import { revalidatePath } from "next/cache";
import type { MentionCandidate } from "@/lib/comments";
import type { Comment, OrgMember } from "@/lib/types";

interface ActionResult {
  success: boolean;
  error?: string;
  /** Set when the backend rejected one specific attachment (422 "Attachment not allowed"). */
  attachmentDocumentId?: string;
}

const ATTACHMENT_NOT_ALLOWED = "Attachment not allowed";

/**
 * Maps the backend's 422 "Attachment not allowed" ProblemDetail. Its detail names the document by
 * raw UUID, so the offending id is passed through for the form to show the file name instead.
 */
function attachmentErrorResult(error: ApiError): ActionResult | null {
  if (error.status !== 422 || error.detail?.title !== ATTACHMENT_NOT_ALLOWED) {
    return null;
  }
  const documentId = error.detail.documentId;
  return {
    success: false,
    error: error.message,
    ...(typeof documentId === "string" ? { attachmentDocumentId: documentId } : {}),
  };
}

// 634E(1): the list endpoint pages (default 50, oldest first), so a single unpaged call dropped the
// newest comments once a thread passed 50. Load pages until one comes back short, with a hard cap.
const COMMENT_PAGE_SIZE = 100;
const COMMENT_MAX_PAGES = 10;

export async function fetchComments(
  projectId: string,
  entityType: string,
  entityId: string
): Promise<Comment[]> {
  const seen = new Set<string>();
  const comments: Comment[] = [];
  for (let page = 0; page < COMMENT_MAX_PAGES; page++) {
    const batch = await listMatterComments(projectId, {
      entityType,
      entityId,
      page,
      size: COMMENT_PAGE_SIZE,
    });
    // De-duplicate by id: a comment posted between two page reads shifts the page boundary.
    for (const comment of batch) {
      if (!seen.has(comment.id)) {
        seen.add(comment.id);
        comments.push(comment);
      }
    }
    if (batch.length < COMMENT_PAGE_SIZE) break;
  }
  return comments;
}

/**
 * 634C: who can be @-mentioned on this matter — its members plus the org's owners and admins (who
 * can view every matter), de-duplicated, in that order. The backend notifies only members who can
 * view the matter, so this is the same set. Org members are best-effort: if that list fails, the
 * matter's members are still offered.
 */
export async function fetchMentionCandidates(projectId: string): Promise<MentionCandidate[]> {
  const [matterMembers, orgMembers] = await Promise.all([
    listMatterMembers(projectId),
    api.get<OrgMember[]>("/api/members").catch((): OrgMember[] => []),
  ]);
  const candidates = new Map<string, MentionCandidate>();
  for (const member of matterMembers) {
    if (member.name?.trim()) {
      candidates.set(member.memberId, { id: member.memberId, name: member.name.trim() });
    }
  }
  for (const member of orgMembers) {
    const canViewEveryMatter = member.orgRole === "owner" || member.orgRole === "admin";
    if (canViewEveryMatter && member.name?.trim() && !candidates.has(member.id)) {
      candidates.set(member.id, { id: member.id, name: member.name.trim() });
    }
  }
  return [...candidates.values()];
}

export async function createComment(
  orgSlug: string,
  projectId: string,
  entityType: string,
  entityId: string,
  body: string,
  visibility?: string,
  attachmentDocumentIds?: string[]
): Promise<ActionResult> {
  if (!body.trim()) {
    return { success: false, error: "Comment cannot be empty." };
  }

  try {
    await createMatterComment(projectId, {
      entityType,
      entityId,
      body: body.trim(),
      ...(visibility ? { visibility } : {}),
      ...(attachmentDocumentIds && attachmentDocumentIds.length > 0
        ? { attachmentDocumentIds }
        : {}),
    });
  } catch (error) {
    if (error instanceof ApiError) {
      return attachmentErrorResult(error) ?? { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${orgSlug}/matters/${projectId}`);

  return { success: true };
}

export async function updateComment(
  orgSlug: string,
  projectId: string,
  commentId: string,
  body: string,
  visibility?: string,
  /** undefined = leave the attachments unchanged; a list (including []) replaces them. */
  attachmentDocumentIds?: string[]
): Promise<ActionResult> {
  if (!body.trim()) {
    return { success: false, error: "Comment cannot be empty." };
  }

  try {
    await updateMatterComment(projectId, commentId, {
      body: body.trim(),
      ...(visibility ? { visibility } : {}),
      ...(attachmentDocumentIds ? { attachmentDocumentIds } : {}),
    });
  } catch (error) {
    if (error instanceof ApiError) {
      const attachmentError = attachmentErrorResult(error);
      if (attachmentError) {
        return attachmentError;
      }
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to edit this comment.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${orgSlug}/matters/${projectId}`);

  return { success: true };
}

export async function deleteComment(
  orgSlug: string,
  projectId: string,
  commentId: string
): Promise<ActionResult> {
  try {
    await deleteMatterComment(projectId, commentId);
  } catch (error) {
    if (error instanceof ApiError) {
      if (error.status === 403) {
        return {
          success: false,
          error: "You do not have permission to delete this comment.",
        };
      }
      return { success: false, error: error.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${orgSlug}/matters/${projectId}`);

  return { success: true };
}
