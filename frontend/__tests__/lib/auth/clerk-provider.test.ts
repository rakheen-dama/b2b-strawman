import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@clerk/nextjs/server", () => ({
  auth: vi.fn().mockResolvedValue({
    orgId: "org_test",
    orgSlug: "test-org",
    orgRole: "org:owner",
    userId: "user_1",
    getToken: vi.fn().mockResolvedValue("tok_123"),
  }),
  currentUser: vi.fn().mockResolvedValue({
    primaryEmailAddress: { emailAddress: "alice@test.com" },
  }),
}));

import {
  getAuthContext,
  getAuthToken,
  getCurrentUserEmail,
  requireRole,
} from "@/lib/auth/providers/clerk";
import { auth } from "@clerk/nextjs/server";

describe("Clerk auth provider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(auth).mockResolvedValue({
      orgId: "org_test",
      orgSlug: "test-org",
      orgRole: "org:owner",
      userId: "user_1",
      getToken: vi.fn().mockResolvedValue("tok_123"),
    } as never);
  });

  it("getAuthContext() returns correct AuthContext shape", async () => {
    const ctx = await getAuthContext();

    expect(ctx).toEqual({
      orgId: "org_test",
      orgSlug: "test-org",
      orgRole: "org:owner",
      userId: "user_1",
    });
  });

  it("getAuthToken() returns the token string", async () => {
    const token = await getAuthToken();

    expect(token).toBe("tok_123");
  });

  it("getCurrentUserEmail() returns the email string", async () => {
    const email = await getCurrentUserEmail();

    expect(email).toBe("alice@test.com");
  });

  it("requireRole('owner') passes when role matches", async () => {
    await expect(requireRole("owner")).resolves.toBeUndefined();
  });

  it("requireRole('admin') throws when role is member", async () => {
    vi.mocked(auth).mockResolvedValueOnce({
      orgId: "org_test",
      orgSlug: "test-org",
      orgRole: "org:member",
      userId: "user_1",
      getToken: vi.fn().mockResolvedValue("tok_123"),
    } as never);

    await expect(requireRole("admin")).rejects.toThrow(
      "Insufficient permissions — admin role required",
    );
  });
});
