import { describe, it, expect, vi, beforeEach } from "vitest";

// Shared server-side expired-session funnel for loaders that fail soft: framework control flow
// (a redirect thrown deeper down) is rethrown, a backend 401 goes to the branded re-login, and
// anything else is left to the caller.

vi.mock("server-only", () => ({}));

const { mockRedirectToReLogin, mockResolveReturnTo, MockApiError } = vi.hoisted(() => {
  class MockApiError extends Error {
    constructor(
      public status: number,
      message: string
    ) {
      super(message);
      this.name = "ApiError";
    }
  }
  return {
    mockRedirectToReLogin: vi.fn(() => {
      throw new Error("NEXT_REDIRECT");
    }),
    mockResolveReturnTo: vi.fn(),
    MockApiError,
  };
});

vi.mock("@/lib/api/client", () => ({
  ApiError: MockApiError,
  resolveServerReturnTo: mockResolveReturnTo,
}));
vi.mock("@/lib/auth/expiry", () => ({ redirectToReLogin: mockRedirectToReLogin }));
vi.mock("next/navigation", () => ({
  unstable_rethrow: (error: unknown) => {
    if (error instanceof Error && error.message === "NEXT_REDIRECT") throw error;
  },
}));

import { rethrowOnExpiredSession } from "@/lib/auth/expiry-server";

describe("rethrowOnExpiredSession", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockResolveReturnTo.mockResolvedValue("/org/acme/diary");
  });

  it("returns normally for ordinary failures", async () => {
    await expect(
      rethrowOnExpiredSession(new MockApiError(500, "boom"), new Error("network"))
    ).resolves.toBeUndefined();
    expect(mockRedirectToReLogin).not.toHaveBeenCalled();
  });

  it("returns normally with no errors", async () => {
    await expect(rethrowOnExpiredSession()).resolves.toBeUndefined();
  });

  it("sends a backend 401 to the branded re-login with the current path", async () => {
    await expect(
      rethrowOnExpiredSession(new MockApiError(500, "boom"), new MockApiError(401, "expired"))
    ).rejects.toThrow("NEXT_REDIRECT");
    expect(mockRedirectToReLogin).toHaveBeenCalledWith("/org/acme/diary", "expired");
  });

  it("rethrows framework control flow before considering a 401", async () => {
    await expect(
      rethrowOnExpiredSession(new MockApiError(401, "expired"), new Error("NEXT_REDIRECT"))
    ).rejects.toThrow("NEXT_REDIRECT");
    expect(mockRedirectToReLogin).not.toHaveBeenCalled();
  });
});
