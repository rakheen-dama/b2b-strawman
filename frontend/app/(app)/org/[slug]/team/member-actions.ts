"use server";

import { revalidatePath } from "next/cache";
import { AUTH_MODE } from "@/lib/auth";
import { fetchMyCapabilities } from "@/lib/api/capabilities";
import { api, ApiError } from "@/lib/api";

interface ActionResult {
  success: boolean;
  error?: string;
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

/**
 * Normalize a gateway role (e.g. "owner", "admin", "member") to the
 * UI format ("org:owner", "org:admin", "org:member").
 */
function normalizeRole(role: string | undefined | null): string {
  if (!role) return "org:member";
  const lower = role.toLowerCase();
  if (lower.startsWith("org:")) return lower;
  return `org:${lower}`;
}

/** Shape returned by the backend's GET /api/members endpoint. */
interface BackendMember {
  id: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  orgRole: string;
}

async function listMembersBff(): Promise<BffMember[]> {
  try {
    const raw = await api.get<BackendMember[]>("/api/members");
    return (raw ?? []).map((m) => ({
      id: m.id,
      email: m.email,
      name: m.name,
      role: normalizeRole(m.orgRole),
    }));
  } catch (err: unknown) {
    console.error("Failed to list members:", err);
    return [];
  }
}

export async function listMembers(): Promise<BffMember[]> {
  if (AUTH_MODE === "keycloak") {
    return listMembersBff();
  }

  // Mock mode handles member listing client-side
  return [];
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
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
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
  const caps = await fetchMyCapabilities();

  if (!caps.isAdmin && !caps.isOwner) {
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
