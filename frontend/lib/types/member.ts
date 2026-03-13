// ---- Members (from OrgMemberController.java) ----

export interface OrgMember {
  id: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  orgRole: string;
}

// ---- Project Members (from ProjectMemberController.java) ----

export type ProjectRole = "lead" | "member";

export interface ProjectMember {
  id: string;
  memberId: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  projectRole: ProjectRole;
  orgRole: string;
  createdAt: string;
}
