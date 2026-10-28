// ---- Members (from OrgMemberController.java) ----

export interface OrgMember {
  id: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  orgRole: string;
}

// ---- Matter Members (from ProjectMemberController.java) ----

export type MatterRole = "lead" | "member";

export interface MatterMember {
  id: string;
  memberId: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  matterRole: MatterRole;
  orgRole: string;
  createdAt: string;
}
