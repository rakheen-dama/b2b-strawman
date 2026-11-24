import { describe, it, expect, vi, beforeEach } from "vitest";

/**
 * FR-4: a 403 from creating/updating a role or assigning a member's role surfaces the backend's
 * ProblemDetail detail (e.g. "Only the owner can create a role holding an owner-only capability:
 * SIGN_OFF_TRUST_TAKE_ON.") instead of a generic permission message. A 403 with no detail keeps the
 * generic message.
 */

vi.mock("server-only", () => ({}));

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

vi.mock("@/lib/auth", () => ({ AUTH_MODE: "keycloak" }));

vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi.fn().mockResolvedValue({ isAdmin: true, isOwner: false }),
}));

const mockPut = vi.fn();
vi.mock("@/lib/api", () => {
  class ApiError extends Error {
    constructor(
      public status: number,
      message: string,
      public detail?: { title?: string; detail?: string }
    ) {
      super(message);
      this.name = "ApiError";
    }
  }
  return { ApiError, api: { put: (...args: unknown[]) => mockPut(...args), get: vi.fn() } };
});

const mockCreateOrgRole = vi.fn();
const mockUpdateOrgRole = vi.fn();
vi.mock("@/lib/api/org-roles", () => ({
  createOrgRole: (...args: unknown[]) => mockCreateOrgRole(...args),
  updateOrgRole: (...args: unknown[]) => mockUpdateOrgRole(...args),
  deleteOrgRole: vi.fn(),
}));

import { ApiError } from "@/lib/api";
import {
  createRoleAction,
  updateRoleAction,
} from "@/app/(app)/org/[slug]/settings/firm/_tabs/roles-actions";
import { assignMemberRole } from "@/app/(app)/org/[slug]/team/member-actions";

function ownerOnly403(detail: string) {
  return new ApiError(403, detail, {
    title: "Owner-only capability",
    detail,
    status: 403,
  } as never);
}

describe("owner-only grant refusals surface the ProblemDetail detail", () => {
  beforeEach(() => vi.clearAllMocks());

  it("createRoleAction", async () => {
    const detail =
      "Only the owner can create a role holding an owner-only capability: SIGN_OFF_TRUST_TAKE_ON.";
    mockCreateOrgRole.mockRejectedValue(ownerOnly403(detail));

    const result = await createRoleAction("acme", {
      name: "Partner",
      capabilities: ["SIGN_OFF_TRUST_TAKE_ON"],
    });

    expect(result).toEqual({ success: false, error: detail });
  });

  it("updateRoleAction", async () => {
    const detail =
      "Only the owner can add an owner-only capability to a role: SIGN_OFF_TRUST_TAKE_ON.";
    mockUpdateOrgRole.mockRejectedValue(ownerOnly403(detail));

    const result = await updateRoleAction("acme", "role-1", {
      capabilities: ["SIGN_OFF_TRUST_TAKE_ON"],
    });

    expect(result).toEqual({ success: false, error: detail });
  });

  it("assignMemberRole", async () => {
    const detail =
      "Only the owner can assign a role holding an owner-only capability: SIGN_OFF_TRUST_TAKE_ON.";
    mockPut.mockRejectedValue(ownerOnly403(detail));

    const result = await assignMemberRole("acme", "m1", "role-partner", []);

    expect(result).toEqual({ success: false, error: detail });
  });

  it("a 403 without a detail keeps the generic message", async () => {
    mockCreateOrgRole.mockRejectedValue(new ApiError(403, "Forbidden"));

    const result = await createRoleAction("acme", { name: "X", capabilities: [] });

    expect(result).toEqual({
      success: false,
      error: "You do not have permission to create roles.",
    });
  });
});
