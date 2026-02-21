import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock next/headers
vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

// Mock global fetch for getCurrentUserEmail
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

import {
  getAuthContext,
  getAuthToken,
  getCurrentUserEmail,
  requireRole,
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
  o: { id: "org_e2e_test", slg: "e2e-test-org", rol: "owner" },
  iss: "http://mock-idp:8090",
  aud: "docteams-e2e",
  iat: 1708000000,
  exp: 1708086400,
  v: 2,
};

describe("Mock auth provider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    const token = buildMockJwt(defaultPayload);
    vi.mocked(cookies).mockResolvedValue({
      get: vi.fn().mockReturnValue({ value: token }),
    } as never);
  });

  it("getAuthContext() extracts claims from JWT cookie", async () => {
    const ctx = await getAuthContext();

    expect(ctx).toEqual({
      userId: "user_e2e_alice",
      orgId: "org_e2e_test",
      orgSlug: "e2e-test-org",
      orgRole: "org:owner",
    });
  });

  it("getAuthContext() throws when cookie is missing", async () => {
    vi.mocked(cookies).mockResolvedValue({
      get: vi.fn().mockReturnValue(undefined),
    } as never);

    await expect(getAuthContext()).rejects.toThrow(
      "No mock-auth-token cookie",
    );
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

  it("getCurrentUserEmail() fetches from mock IDP", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ email: "alice@e2e-test.local" }),
    });

    const email = await getCurrentUserEmail();

    expect(email).toBe("alice@e2e-test.local");
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/userinfo/user_e2e_alice"),
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    );
  });

  it("getCurrentUserEmail() returns null on fetch failure", async () => {
    mockFetch.mockResolvedValue({ ok: false });

    const email = await getCurrentUserEmail();

    expect(email).toBeNull();
  });

  it("requireRole('any') passes for any role", async () => {
    await expect(requireRole("any")).resolves.toBeUndefined();
  });

  it("requireRole('owner') passes when role is owner", async () => {
    await expect(requireRole("owner")).resolves.toBeUndefined();
  });

  it("requireRole('owner') throws when role is admin", async () => {
    const adminPayload = {
      ...defaultPayload,
      o: { ...defaultPayload.o, rol: "admin" },
    };
    vi.mocked(cookies).mockResolvedValue({
      get: vi.fn().mockReturnValue({ value: buildMockJwt(adminPayload) }),
    } as never);

    await expect(requireRole("owner")).rejects.toThrow(
      "Insufficient permissions — owner role required",
    );
  });

  it("requireRole('admin') passes when role is admin", async () => {
    const adminPayload = {
      ...defaultPayload,
      o: { ...defaultPayload.o, rol: "admin" },
    };
    vi.mocked(cookies).mockResolvedValue({
      get: vi.fn().mockReturnValue({ value: buildMockJwt(adminPayload) }),
    } as never);

    await expect(requireRole("admin")).resolves.toBeUndefined();
  });

  it("requireRole('admin') passes when role is owner", async () => {
    await expect(requireRole("admin")).resolves.toBeUndefined();
  });

  it("requireRole('admin') throws when role is member", async () => {
    const memberPayload = {
      ...defaultPayload,
      o: { ...defaultPayload.o, rol: "member" },
    };
    vi.mocked(cookies).mockResolvedValue({
      get: vi.fn().mockReturnValue({ value: buildMockJwt(memberPayload) }),
    } as never);

    await expect(requireRole("admin")).rejects.toThrow(
      "Insufficient permissions — admin role required",
    );
  });
});
