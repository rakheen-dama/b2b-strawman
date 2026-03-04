"use server";

import { getAuthContext, AUTH_MODE } from "@/lib/auth";
import { api } from "@/lib/api";
import { headers } from "next/headers";

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
function normalizeRole(role: string): string {
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

// --- Clerk actions (existing) ---

async function inviteMemberClerk(
  emailAddress: string,
  role: "org:member" | "org:admin",
): Promise<ActionResult> {
  const { orgId, userId } = await getAuthContext();

  const headersList = await headers();
  const host = headersList.get("host") ?? "localhost:3000";
  const protocol = headersList.get("x-forwarded-proto") ?? "http";
  const redirectUrl = `${protocol}://${host}/sign-up`;

  try {
    const { clerkClient } = await import("@clerk/nextjs/server");
    const client = await clerkClient();
    await client.organizations.createOrganizationInvitation({
      organizationId: orgId,
      inviterUserId: userId,
      emailAddress,
      role,
      redirectUrl,
    });
  } catch (err: unknown) {
    const message =
      err instanceof Error ? err.message : "Failed to send invitation.";
    return { success: false, error: message };
  }

  return { success: true };
}

// --- BFF actions ---

async function inviteMemberBff(
  email: string,
  role: "org:member" | "org:admin",
): Promise<ActionResult> {
  try {
    // Gateway expects bare role names (member, admin) — strip org: prefix
    const bareRole = role.replace("org:", "");
    await api.post<{ success: boolean }>("/bff/admin/invite", {
      email,
      role: bareRole,
    });
    return { success: true };
  } catch (err: unknown) {
    const message =
      err instanceof Error ? err.message : "Failed to send invitation.";
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
      err instanceof Error ? err.message : "Failed to revoke invitation.";
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
): Promise<ActionResult> {
  const { orgRole } = await getAuthContext();

  if (orgRole !== "org:admin" && orgRole !== "org:owner") {
    return {
      success: false,
      error: "You must be an admin to invite members.",
    };
  }

  if (AUTH_MODE === "mock") {
    // Mock mode: stub success — E2E tests don't exercise Clerk invitations
    return { success: true };
  }

  if (AUTH_MODE === "keycloak") {
    return inviteMemberBff(emailAddress, role);
  }

  return inviteMemberClerk(emailAddress, role);
}

export async function listInvitations(): Promise<MappedInvitation[]> {
  if (AUTH_MODE === "mock") {
    return [];
  }

  if (AUTH_MODE === "keycloak") {
    return listInvitationsBff();
  }

  // Clerk mode: invitations are fetched client-side via useOrganization()
  return [];
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

  if (AUTH_MODE === "keycloak") {
    return revokeInvitationBff(id);
  }

  // Clerk mode: revoke is handled client-side via invitation.revoke()
  return { success: false, error: "Use Clerk SDK for revoking invitations." };
}

export async function listMembers(): Promise<BffMember[]> {
  if (AUTH_MODE === "keycloak") {
    return listMembersBff();
  }

  // Clerk/mock modes handle member listing client-side
  return [];
}
