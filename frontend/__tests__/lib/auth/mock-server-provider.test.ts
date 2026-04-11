import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock next/headers
vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

// Mock global fetch for getCurrentUserEmail
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

import {
  getSessionIdentity,
  getAuthContext,
  getAuthToken,
  getCurrentUserEmail,
} from "@/lib/auth/providers/mock/server";
import { cookies } from "next/headers";

// Helper: build a mock JWT with the given payload
function buildMockJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.fake-signature`;
}

const defaultPayload = {
  sub: "user_e2e_alice",
  organization: ["e2e-test-org"],
  groups: ["platform-admins"],
  email: "alice@e2e-test.local",
  iss: "http://mock-idp:8090",
  aud: "docteams-e2e",
  iat: 1708000000,
  exp: 1708086400,
};

describe("Mock auth provider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    const token = buildMockJwt(defaultPayload);
    vi.mocked(cookies).mockResolvedValue({
      get: vi.fn().mockReturnValue({ value: token }),
    } as never);
  });

  it("getSessionIdentity() returns userId and groups without org context", async () => {
    const identity = await getSessionIdentity();

    expect(identity).toEqual({
      userId: "user_e2e_alice",
      groups: ["platform-admins"],
    });
  });

  it("getSessionIdentity() returns empty groups for non-admin user", async () => {
    const bobPayload = { ...defaultPayload, sub: "user_e2e_bob", groups: [] };
    vi.mocked(cookies).mockResolvedValue({
      get: vi.fn().mockReturnValue({ value: buildMockJwt(bobPayload) }),
    } as never);

    const identity = await getSessionIdentity();

    expect(identity).toEqual({
      userId: "user_e2e_bob",
      groups: [],
    });
  });

  it("getAuthContext() extracts claims from JWT cookie", async () => {
    const ctx = await getAuthContext();

    expect(ctx).toEqual({
      userId: "user_e2e_alice",
      orgId: "e2e-test-org",
      orgSlug: "e2e-test-org",
      groups: ["platform-admins"],
    });
  });

  it("getAuthContext() throws when cookie is missing", async () => {
    vi.mocked(cookies).mockResolvedValue({
      get: vi.fn().mockReturnValue(undefined),
    } as never);

    await expect(getAuthContext()).rejects.toThrow("No mock-auth-token cookie");
  });

  it("getAuthToken() returns raw JWT string", async () => {
    const token = await getAuthToken();

    expect(token).toContain(".");
    expect(token.split(".")).toHaveLength(3);
  });

  it("getAuthToken() throws when cookie is missing", async () => {
    vi.mocked(cookies).mockResolvedValue({
      get: vi.fn().mockReturnValue(undefined),
    } as never);

    await expect(getAuthToken()).rejects.toThrow("No mock-auth-token cookie");
  });

  it("getCurrentUserEmail() reads email from JWT claim", async () => {
    const email = await getCurrentUserEmail();

    expect(email).toBe("alice@e2e-test.local");
    // Should NOT fetch from userinfo when email is in the token
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("getCurrentUserEmail() falls back to userinfo when email not in token", async () => {
    const noEmailPayload = { ...defaultPayload };
    delete (noEmailPayload as Record<string, unknown>).email;
    vi.mocked(cookies).mockResolvedValue({
      get: vi.fn().mockReturnValue({ value: buildMockJwt(noEmailPayload) }),
    } as never);

    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ email: "alice@e2e-test.local" }),
    });

    const email = await getCurrentUserEmail();

    expect(email).toBe("alice@e2e-test.local");
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/userinfo/user_e2e_alice"),
      expect.objectContaining({ signal: expect.any(AbortSignal) })
    );
  });

  it("getCurrentUserEmail() returns null when no email in token and fetch fails", async () => {
    const noEmailPayload = { ...defaultPayload };
    delete (noEmailPayload as Record<string, unknown>).email;
    vi.mocked(cookies).mockResolvedValue({
      get: vi.fn().mockReturnValue({ value: buildMockJwt(noEmailPayload) }),
    } as never);

    mockFetch.mockResolvedValue({ ok: false });

    const email = await getCurrentUserEmail();

    expect(email).toBeNull();
  });
});
