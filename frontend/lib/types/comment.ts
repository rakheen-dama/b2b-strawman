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
}
