import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 88 U17 (ADR-354): the matter retention period rides PATCH /api/settings/data-protection
// (owner-only on the backend), sending only `legalMatterRetentionYears`, re-validated
// server-side with the same 1–30 whole-years schema the form uses.

vi.mock("server-only", () => ({}));

const { mockPatch, mockCaps, mockRevalidatePath } = vi.hoisted(() => ({
  mockPatch: vi.fn(),
  mockCaps: vi.fn(),
  mockRevalidatePath: vi.fn(),
}));

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
  fetchMyCapabilities: () => mockCaps(),
}));
vi.mock("next/cache", () => ({ revalidatePath: mockRevalidatePath }));

import { updateMatterRetentionYears } from "@/app/(app)/org/[slug]/settings/firm/_tabs/compliance-actions";
import { ApiError } from "@/lib/api";

const OWNER = { capabilities: [], role: "owner", isAdmin: false, isOwner: true };
const ADMIN = { capabilities: [], role: "admin", isAdmin: true, isOwner: false };
const OWNER_ONLY = "Only the firm owner can change the matter retention period.";

describe("updateMatterRetentionYears (Phase 88 U17)", () => {
  beforeEach(() => {
    mockPatch.mockReset();
    mockPatch.mockResolvedValue({ legalMatterRetentionYears: 7 });
    mockCaps.mockReset();
    mockCaps.mockResolvedValue(OWNER);
    mockRevalidatePath.mockReset();
  });

  it("owner: PATCHes only legalMatterRetentionYears to the data-protection endpoint", async () => {
    const result = await updateMatterRetentionYears("acme", 7);

    expect(result).toEqual({ success: true });
    expect(mockPatch).toHaveBeenCalledTimes(1);
    expect(mockPatch).toHaveBeenCalledWith("/api/settings/data-protection", {
      legalMatterRetentionYears: 7,
    });
    expect(mockRevalidatePath).toHaveBeenCalledWith("/org/acme/settings/firm");
  });

  it("admin: refused with the owner-only message, no PATCH", async () => {
    mockCaps.mockResolvedValue(ADMIN);

    const result = await updateMatterRetentionYears("acme", 7);

    expect(result).toEqual({ success: false, error: OWNER_ONLY });
    expect(mockPatch).not.toHaveBeenCalled();
  });

  it("refuses another org's slug, no PATCH", async () => {
    const result = await updateMatterRetentionYears("other-firm", 7);

    expect(result.success).toBe(false);
    expect(mockPatch).not.toHaveBeenCalled();
  });

  it.each([
    [0, "Retention period must be between 1 and 30 years."],
    [31, "Retention period must be between 1 and 30 years."],
    [2.5, "Retention period must be a whole number of years."],
    [Number.NaN, "Enter the number of years."],
  ])("rejects %s server-side without calling the API", async (years, error) => {
    const result = await updateMatterRetentionYears("acme", years);

    expect(result).toEqual({ success: false, error });
    expect(mockPatch).not.toHaveBeenCalled();
  });

  it("maps a backend 403 to the owner-only message", async () => {
    mockPatch.mockRejectedValue(
      new ApiError(403, "Only the organization owner can perform this action")
    );

    const result = await updateMatterRetentionYears("acme", 7);

    expect(result).toEqual({ success: false, error: OWNER_ONLY });
    expect(mockRevalidatePath).not.toHaveBeenCalled();
  });

  it("returns the backend message for a 400", async () => {
    mockPatch.mockRejectedValue(new ApiError(400, "1 field(s) have validation errors"));

    const result = await updateMatterRetentionYears("acme", 7);

    expect(result).toEqual({ success: false, error: "1 field(s) have validation errors" });
  });

  it("returns a generic message for a non-API failure", async () => {
    mockPatch.mockRejectedValue(new Error("socket hang up"));

    const result = await updateMatterRetentionYears("acme", 7);

    expect(result).toEqual({ success: false, error: "An unexpected error occurred." });
  });
});
