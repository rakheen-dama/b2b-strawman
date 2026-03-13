"use server";

import { getAuthContext, AUTH_MODE } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import { headers } from "next/headers";
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

  if (AUTH_MODE === "keycloak") {
    return inviteMemberBff(emailAddress, role, orgRoleId, capabilityOverrides);
  }

  // Clerk path ignores orgRoleId and capabilityOverrides — Clerk manages roles separately
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
