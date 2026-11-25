import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 88 G-D12 — Team › Pending Invitations and the seat line read listInvitations(). Without a
// status filter, GET /api/invitations returns every invitation, so members who had already
// accepted showed as pending.

vi.mock("server-only", () => ({}));

const { mockGet, mockPost, mockCaps } = vi.hoisted(() => ({
  mockGet: vi.fn(),
  mockPost: vi.fn(),
  mockCaps: vi.fn(),
}));

vi.mock("@/lib/api", () => ({
  api: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    put: vi.fn(),
    delete: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    constructor(
      public status: number,
      message: string
    ) {
      super(message);
      this.name = "ApiError";
    }
  },
}));

vi.mock("@/lib/auth", () => ({ AUTH_MODE: "keycloak" }));
vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: () => mockCaps() }));

import { inviteMember, listInvitations } from "@/app/(app)/org/[slug]/team/invitation-actions";

// Real InvitationResponse shape (backend InvitationDtos): status is the enum name, upper case.
const PENDING = {
  id: "inv-1",
  email: "thandi@mathebula.co.za",
  roleName: "Member",
  roleSlug: "member",
  invitedByName: "Alice Owner",
  status: "PENDING",
  expiresAt: "2026-11-04T08:00:00Z",
  createdAt: "2026-10-28T08:00:00Z",
  acceptedAt: null,
};

const ACCEPTED = {
  id: "inv-2",
  email: "bob@mathebula.co.za",
  roleName: "Admin",
  roleSlug: "admin",
  invitedByName: "Alice Owner",
  status: "ACCEPTED",
  expiresAt: "2026-10-30T08:00:00Z",
  createdAt: "2026-10-23T08:00:00Z",
  acceptedAt: "2026-10-23T09:00:00Z",
};

describe("listInvitations (keycloak mode)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  it("asks the backend for pending invitations only", async () => {
    mockGet.mockResolvedValueOnce({ invitations: [PENDING] });

    await listInvitations();

    expect(mockGet).toHaveBeenCalledWith("/api/invitations?status=PENDING");
  });

  it("drops invitations that are no longer pending", async () => {
    mockGet.mockResolvedValueOnce({ invitations: [PENDING, ACCEPTED] });

    await expect(listInvitations()).resolves.toEqual([
      {
        id: "inv-1",
        emailAddress: "thandi@mathebula.co.za",
        role: "org:member",
        status: "PENDING",
        createdAt: "2026-10-28T08:00:00Z",
      },
    ]);
  });

  it("returns [] when the request fails", async () => {
    mockGet.mockRejectedValueOnce(new Error("boom"));

    await expect(listInvitations()).resolves.toEqual([]);
  });
});

// Fix 6 (Keycloak-stack OBS F-2): when the person already has a login (a revoked invitee is still in
// the Keycloak org), Keycloak sends no email — the 201 says so and the action passes it on.
describe("inviteMember (keycloak mode)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCaps.mockResolvedValue({ isAdmin: false, isOwner: true });
  });

  it("passes on that no email was sent because the person already has a login", async () => {
    mockPost.mockResolvedValue({ ...PENDING, emailNotSentReason: "ALREADY_HAS_LOGIN" });

    const result = await inviteMember("thandi@mathebula.co.za", "org:member", "role-1");

    expect(mockPost).toHaveBeenCalledWith("/api/invitations", {
      email: "thandi@mathebula.co.za",
      orgRoleId: "role-1",
    });
    expect(result).toEqual({ success: true, emailNotSentReason: "ALREADY_HAS_LOGIN" });
  });

  it("an emailed invitation carries no reason", async () => {
    mockPost.mockResolvedValue({ ...PENDING, emailNotSentReason: null });

    expect(await inviteMember("thandi@mathebula.co.za", "org:member", "role-1")).toEqual({
      success: true,
    });
  });
});
