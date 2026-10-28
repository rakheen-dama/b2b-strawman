import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 88 G-A6: the conflict-check page resolves the `customerId` client's name + ID number
// server-side (the New Matter link carries no personal data — POPIA). A non-UUID id skips the
// lookup; an API failure returns null; Next control flow (re-login redirect) is re-thrown.

vi.mock("server-only", () => ({}));

const { mockGetClient, MockApiError } = vi.hoisted(() => {
  class MockApiError extends Error {
    constructor(
      public status: number,
      message: string
    ) {
      super(message);
      this.name = "ApiError";
    }
  }
  return { mockGetClient: vi.fn(), MockApiError };
});

vi.mock("@/lib/api/clients", () => ({ getClient: mockGetClient }));
vi.mock("next/navigation", () => ({
  unstable_rethrow: (error: unknown) => {
    if (error instanceof Error && error.message === "NEXT_REDIRECT") throw error;
  },
}));

import { resolveClientDetails } from "@/app/(app)/org/[slug]/conflict-check/resolve-client";

const CLIENT_ID = "11111111-1111-4111-8111-111111111111";

describe("resolveClientDetails", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  it.each([undefined, "", "not-a-uuid", "c-1"])(
    "returns null without a lookup for a missing / non-UUID id (%s)",
    async (customerId) => {
      await expect(resolveClientDetails(customerId)).resolves.toBeNull();
      expect(mockGetClient).not.toHaveBeenCalled();
    }
  );

  it("returns the client's name and ID number for a valid UUID", async () => {
    mockGetClient.mockResolvedValue({
      id: CLIENT_ID,
      name: "Sipho Ndlovu",
      idNumber: "8501015800087",
      email: "sipho@example.com",
    });

    await expect(resolveClientDetails(CLIENT_ID)).resolves.toEqual({
      name: "Sipho Ndlovu",
      idNumber: "8501015800087",
    });
    expect(mockGetClient).toHaveBeenCalledWith(CLIENT_ID);
  });

  it("returns null when the lookup fails with an API error", async () => {
    mockGetClient.mockRejectedValue(new MockApiError(404, "Not Found"));

    await expect(resolveClientDetails(CLIENT_ID)).resolves.toBeNull();
    expect(console.error).toHaveBeenCalled();
  });

  it("re-throws Next control flow (re-login redirect) instead of swallowing it", async () => {
    mockGetClient.mockRejectedValue(new Error("NEXT_REDIRECT"));

    await expect(resolveClientDetails(CLIENT_ID)).rejects.toThrow("NEXT_REDIRECT");
    expect(console.error).not.toHaveBeenCalled();
  });
});
