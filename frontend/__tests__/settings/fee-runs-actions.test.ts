import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 88 620C (U3, ADR-347): the firm default payment terms ride the existing
// PATCH /api/settings/batch-billing (flat defaultPaymentTermsDays), re-validated server-side
// with the same 0–365 schema the form uses.

vi.mock("server-only", () => ({}));

const { mockPatch } = vi.hoisted(() => ({ mockPatch: vi.fn() }));

vi.mock("@/lib/api", () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: mockPatch, delete: vi.fn() },
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
vi.mock("@/lib/auth", () => ({
  getAuthContext: vi.fn().mockResolvedValue({ orgSlug: "acme" }),
}));
vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi.fn().mockResolvedValue({ isAdmin: true, isOwner: false }),
}));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

import { updateBatchBillingSettings } from "@/app/(app)/org/[slug]/settings/fees/_tabs/fee-runs-actions";

const BASE = {
  billingBatchAsyncThreshold: 50,
  billingEmailRateLimit: 5,
  defaultBillingRunCurrency: null,
};

describe("updateBatchBillingSettings — default payment terms (Phase 88 620C)", () => {
  beforeEach(() => {
    mockPatch.mockReset();
    mockPatch.mockResolvedValue({});
  });

  it("PATCHes /api/settings/batch-billing with defaultPaymentTermsDays", async () => {
    const result = await updateBatchBillingSettings("acme", {
      ...BASE,
      defaultPaymentTermsDays: 14,
    });

    expect(result).toEqual({ success: true });
    expect(mockPatch).toHaveBeenCalledWith("/api/settings/batch-billing", {
      ...BASE,
      defaultPaymentTermsDays: 14,
    });
  });

  it.each([
    [366, "Payment terms must be between 0 and 365 days."],
    [-1, "Payment terms must be between 0 and 365 days."],
    [14.5, "Payment terms must be a whole number of days."],
  ])("rejects %s server-side without calling the API", async (days, error) => {
    const result = await updateBatchBillingSettings("acme", {
      ...BASE,
      defaultPaymentTermsDays: days,
    });

    expect(result).toEqual({ success: false, error });
    expect(mockPatch).not.toHaveBeenCalled();
  });
});
