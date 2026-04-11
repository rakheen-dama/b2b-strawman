"use server";

import { AUTH_MODE } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
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

async function resolveOrgRoleId(
  role: "org:member" | "org:admin",
  orgRoleId?: string
): Promise<string> {
  // If an explicit orgRoleId was provided (custom role), use it directly
  if (orgRoleId) return orgRoleId;

  // For system roles, look up the UUID by slug
  const slug = role.replace("org:", ""); // "member" or "admin"
  const roles = await api.get<{ id: string; slug: string; isSystem: boolean }[]>("/api/org-roles");
  const match = (roles ?? []).find((r) => r.slug === slug && r.isSystem);
  if (!match) {
    throw new Error(`System role "${slug}" not found`);
  }
  return match.id;
}

async function inviteMemberBff(
  email: string,
  role: "org:member" | "org:admin",
  orgRoleId?: string,
  _capabilityOverrides?: string[]
): Promise<ActionResult> {
  try {
    const resolvedRoleId = await resolveOrgRoleId(role, orgRoleId);
    await api.post<unknown>("/api/invitations", {
      email,
      orgRoleId: resolvedRoleId,
    });
    return { success: true };
  } catch (err: unknown) {
    const message =
      err instanceof Error
        ? err.message
        : createMessages("errors").t(classifyError(err).messageCode);
    return { success: false, error: message };
  }
}

/** Shape returned by the backend's GET /api/invitations endpoint. */
interface BackendInvitation {
  id: string;
  email: string;
  roleName: string;
  roleSlug: string;
  invitedByName: string;
  status: string;
  expiresAt: string;
  createdAt: string;
  acceptedAt: string | null;
}

async function listInvitationsBff(): Promise<MappedInvitation[]> {
  try {
    const raw = await api.get<{ invitations: BackendInvitation[] }>("/api/invitations");
    return (raw?.invitations ?? []).map((inv) => ({
      id: inv.id,
      emailAddress: inv.email,
      role: normalizeRole(inv.roleSlug),
      status: inv.status,
      createdAt: inv.createdAt,
    }));
  } catch (err: unknown) {
    console.error("Failed to list invitations:", err);
    return [];
  }
}

async function revokeInvitationBff(id: string): Promise<ActionResult> {
  try {
    await api.delete<void>(`/api/invitations/${encodeURIComponent(id)}`);
    return { success: true };
  } catch (err: unknown) {
    const message =
      err instanceof Error
        ? err.message
        : createMessages("errors").t(classifyError(err).messageCode);
    return { success: false, error: message };
  }
}

// --- Dispatch wrappers ---

export async function inviteMember(
  emailAddress: string,
  role: "org:member" | "org:admin",
  orgRoleId?: string,
  capabilityOverrides?: string[]
): Promise<ActionResult> {
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
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
  const caps = await fetchMyCapabilities();
  if (!caps.isAdmin && !caps.isOwner) {
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
