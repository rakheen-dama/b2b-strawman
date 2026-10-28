/** One document attached to a comment (CommentController.CommentAttachmentResponse). */
export interface CommentAttachment {
  documentId: string;
  fileName: string;
  contentType: string;
  size: number;
}

export interface Comment {
  id: string;
  entityType: string;
  entityId: string;
  matterId: string;
  authorMemberId: string;
  authorName: string | null;
  authorAvatarUrl: string | null;
  body: string;
  visibility: "INTERNAL" | "SHARED";
  source: "INTERNAL" | "PORTAL";
  parentId: string | null;
  createdAt: string;
  updatedAt: string;
  /** Always present from the backend (empty list when none), ordered by attach position. */
  attachments: CommentAttachment[];
}
