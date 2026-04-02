import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock server-only (no-op in test environment)
vi.mock("server-only", () => ({}));

// Mock next/headers
vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

// Mock global fetch
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

import {
  getAuthContext,
  getAuthToken,
  getCurrentUserEmail,
} from "@/lib/auth/providers/keycloak-bff";
import { cookies } from "next/headers";

const defaultBffResponse = {
  authenticated: true,
  userId: "kc-user-123",
  email: "alice@example.com",
  name: "Alice Smith",
  picture: "https://example.com/alice.jpg",
  orgId: "kc-org-456",
  orgSlug: "acme-corp",
};

describe("Keycloak BFF auth provider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(cookies).mockResolvedValue({
      get: vi.fn().mockReturnValue({ value: "session-abc" }),
    } as never);
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(defaultBffResponse),
    });
  });

  it("getAuthContext() returns auth context from /bff/me", async () => {
    const ctx = await getAuthContext();

    expect(ctx).toEqual({
      userId: "kc-user-123",
      orgId: "kc-org-456",
      orgSlug: "acme-corp",
      groups: [],
    });
  });

  it("getAuthContext() forwards SESSION cookie to gateway", async () => {
    await getAuthContext();

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining("/bff/me"),
      expect.objectContaining({
        headers: expect.objectContaining({
          cookie: "SESSION=session-abc",
        }),
        cache: "no-store",
      }),
    );
  });

  it("getAuthContext() throws when user is not authenticated", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve({
          authenticated: false,
          userId: null,
          email: null,
          name: null,
          picture: null,
          orgId: null,
          orgSlug: null,
        }),
    });

    await expect(getAuthContext()).rejects.toThrow(
      "No active organization — user is not authenticated via BFF",
    );
  });

  it("getAuthToken() throws with BFF mode error", async () => {
    await expect(getAuthToken()).rejects.toThrow(
      "getAuthToken() is not available in BFF mode",
    );
  });

  it("getCurrentUserEmail() returns email from /bff/me", async () => {
    const email = await getCurrentUserEmail();
    expect(email).toBe("alice@example.com");
  });

  it("getCurrentUserEmail() returns null on fetch failure", async () => {
    mockFetch.mockResolvedValue({ ok: false, status: 500 });

    const email = await getCurrentUserEmail();
    expect(email).toBeNull();
  });

});
