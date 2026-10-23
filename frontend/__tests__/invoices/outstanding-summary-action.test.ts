import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// Phase 88 620C (U3, ADR-347): fetchOutstandingSummaryAction maps the summary read to a
// discriminated result. 403/404 → "absent" (forbidden or absent: the page drops the tiles),
// 401 → the branded re-login, anything else → "error" (the tiles show "—").

vi.mock("server-only", () => ({}));

const { mockGet, mockRethrow, MockApiError } = vi.hoisted(() => {
  class MockApiError extends Error {
    constructor(
      public status: number,
      message: string
    ) {
      super(message);
      this.name = "ApiError";
    }
  }
  const mockRethrow = vi.fn(async (error: unknown) => {
    if (error instanceof MockApiError && error.status === 401) {
      throw new Error("NEXT_REDIRECT");
    }
  });
  return { mockGet: vi.fn(), mockRethrow, MockApiError };
});

vi.mock("@/lib/api", () => ({
  api: { get: mockGet, post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
  ApiError: MockApiError,
}));
vi.mock("@/lib/api/capabilities", () => ({ fetchMyCapabilities: vi.fn() }));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("@/lib/auth/expiry-server", () => ({ rethrowOnExpiredSession: mockRethrow }));

import { fetchOutstandingSummaryAction } from "@/app/(app)/org/[slug]/invoices/invoice-crud-actions";

const SUMMARY = {
  currency: "ZAR",
  outstandingTotal: 3450,
  outstandingCount: 1,
  overdueTotal: 0,
  overdueCount: 0,
  approvedUnsentTotal: 1500,
  approvedUnsentCount: 1,
};

describe("fetchOutstandingSummaryAction (Phase 88 620C)", () => {
  beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.clearAllMocks();
    vi.restoreAllMocks();
  });

  it("ok → returns the summary from GET /api/invoices/outstanding-summary", async () => {
    mockGet.mockResolvedValue(SUMMARY);

    await expect(fetchOutstandingSummaryAction()).resolves.toEqual({
      state: "ok",
      summary: SUMMARY,
    });
    expect(mockGet).toHaveBeenCalledWith("/api/invoices/outstanding-summary");
  });

  it("403 (no FINANCIAL_VISIBILITY) → absent", async () => {
    mockGet.mockRejectedValue(new MockApiError(403, "Insufficient permissions for this operation"));

    await expect(fetchOutstandingSummaryAction()).resolves.toEqual({ state: "absent" });
  });

  it("404 → absent", async () => {
    mockGet.mockRejectedValue(new MockApiError(404, "Not found"));

    await expect(fetchOutstandingSummaryAction()).resolves.toEqual({ state: "absent" });
  });

  it("500 → error", async () => {
    mockGet.mockRejectedValue(new MockApiError(500, "Boom"));

    await expect(fetchOutstandingSummaryAction()).resolves.toEqual({ state: "error" });
  });

  it("network failure → error", async () => {
    mockGet.mockRejectedValue(new TypeError("fetch failed"));

    await expect(fetchOutstandingSummaryAction()).resolves.toEqual({ state: "error" });
  });

  it("401 → the branded re-login (rethrown), not swallowed", async () => {
    mockGet.mockRejectedValue(new MockApiError(401, "Unauthorized"));

    await expect(fetchOutstandingSummaryAction()).rejects.toThrow("NEXT_REDIRECT");
    expect(mockRethrow).toHaveBeenCalledTimes(1);
  });
});
