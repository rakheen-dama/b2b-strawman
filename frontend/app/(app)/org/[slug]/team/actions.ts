"use server";

import { revalidatePath } from "next/cache";
import { getAuthContext, AUTH_MODE } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import { classifyError } from "@/lib/error-handler";
import { createMessages } from "@/lib/messages";

interface ActionResult {
  success: boolean;
  error?: string;
}

// --- BFF invitation types ---

/** Shape returned by the gateway's /bff/admin/invitations endpoint. */
export interface BffInvitation {
  id: string;
  email: string;
  role: string;
  status: string;
  createdAt: string;
}

/** Shape returned by the gateway's /bff/admin/members endpoint. */
export interface BffMember {
  id: string;
  email: string;
  name: string;
  role: string;
  orgRoleId?: string;
  orgRoleName?: string;
  capabilityOverridesCount?: number;
}

/** UI-ready invitation shape used by components. */
export interface MappedInvitation {
  id: string;
  emailAddress: string;
  role: string;
  status: string;
  createdAt: string;
}

/**
 * Normalize a gateway role (e.g. "owner", "admin", "member") to the
 * UI format ("org:owner", "org:admin", "org:member").
 */
function normalizeRole(role: string | undefined | null): string {
  if (!role) return "org:member";
  if (role.startsWith("org:")) return role;
  return `org:${role}`;
}

/**
 * Map a BFF invitation response to the UI-ready shape.
 */
function mapBffInvitation(inv: BffInvitation): MappedInvitation {
  return {
    id: inv.id,
    emailAddress: inv.email,
    role: normalizeRole(inv.role),
    status: inv.status,
    createdAt: inv.createdAt,
  };
}

// --- BFF actions ---

async function inviteMemberBff(
  email: string,
  role: "org:member" | "org:admin",
  orgRoleId?: string,
  capabilityOverrides?: string[],
): Promise<ActionResult> {
  try {
    // Gateway expects bare role names (member, admin) — strip org: prefix
    const bareRole = role.replace("org:", "");
    const body: Record<string, unknown> = {
      email,
      role: bareRole,
    };
    if (orgRoleId) {
      body.orgRoleId = orgRoleId;
    }
    if (capabilityOverrides && capabilityOverrides.length > 0) {
      body.capabilityOverrides = capabilityOverrides;
    }
    await api.post<{ success: boolean }>("/bff/admin/invite", body);
    return { success: true };
  } catch (err: unknown) {
    const message =
      err instanceof Error
        ? err.message
        : createMessages("errors").t(classifyError(err).messageCode);
    return { success: false, error: message };
  }
}

async function listInvitationsBff(): Promise<MappedInvitation[]> {
  try {
    const raw = await api.get<BffInvitation[]>("/bff/admin/invitations");
    return (raw ?? []).map(mapBffInvitation);
  } catch (err: unknown) {
    console.error("Failed to list BFF invitations:", err);
    return [];
  }
}

async function revokeInvitationBff(id: string): Promise<ActionResult> {
  try {
    await api.delete<void>(`/bff/admin/invitations/${encodeURIComponent(id)}`);
    return { success: true };
  } catch (err: unknown) {
    const message =
      err instanceof Error
        ? err.message
        : createMessages("errors").t(classifyError(err).messageCode);
    return { success: false, error: message };
  }
}

async function listMembersBff(): Promise<BffMember[]> {
  try {
    const raw = await api.get<BffMember[]>("/bff/admin/members");
    return (raw ?? []).map((m) => ({ ...m, role: normalizeRole(m.role) }));
  } catch (err: unknown) {
    console.error("Failed to list BFF members:", err);
    return [];
  }
}

// --- Dispatch wrappers ---

export async function inviteMember(
  emailAddress: string,
  role: "org:member" | "org:admin",
  orgRoleId?: string,
  capabilityOverrides?: string[],
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "You must be an admin to invite members.",
    };
  }

  if (AUTH_MODE === "mock") {
    return { success: true };
  }

  return inviteMemberBff(emailAddress, role, orgRoleId, capabilityOverrides);
}

export async function listInvitations(): Promise<MappedInvitation[]> {
  if (AUTH_MODE === "mock") {
    return [];
  }

  return listInvitationsBff();
}

export async function revokeInvitation(id: string): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "You must be an admin to revoke invitations.",
    };
  }

  if (AUTH_MODE === "mock") {
    return { success: true };
  }

  return revokeInvitationBff(id);
}

export async function listMembers(): Promise<BffMember[]> {
  if (AUTH_MODE === "mock") {
    return [];
  }

  return listMembersBff();
}

// --- Member role assignment ---

export interface MemberCapabilities {
  memberId: string;
  roleName: string;
  roleCapabilities: string[];
  overrides: string[];
  effectiveCapabilities: string[];
}

export async function fetchMemberCapabilities(
  memberId: string,
): Promise<MemberCapabilities | null> {
  const { orgRole } = await getAuthContext();

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return null;
  }

  try {
    return await api.get<MemberCapabilities>(
      `/api/members/${encodeURIComponent(memberId)}/capabilities`,
    );
  } catch {
    return null;
  }
}

export async function assignMemberRole(
  slug: string,
  memberId: string,
  orgRoleId: string,
  capabilityOverrides: string[],
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "You must be an admin to change member roles.",
    };
  }

  try {
    await api.put<unknown>(
      `/api/members/${encodeURIComponent(memberId)}/role`,
      {
        orgRoleId,
        capabilityOverrides,
      },
    );
  } catch (err: unknown) {
    if (err instanceof ApiError) {
      if (err.status === 403) {
        return {
          success: false,
          error: "You do not have permission to change this member's role.",
        };
      }
      if (err.status === 422) {
        return { success: false, error: "Invalid role assignment." };
      }
      return { success: false, error: err.message };
    }
    return { success: false, error: "An unexpected error occurred." };
  }

  revalidatePath(`/org/${slug}/team`);
  return { success: true };
}
