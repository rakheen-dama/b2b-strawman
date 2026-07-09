import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock server-only so the "use server" module loads in the test environment.
vi.mock("server-only", () => ({}));

// Mock the collections API client so we can observe which payload the action
// wires up — this is the whole point of these tests.
const mockUpdateCollectionsSettings = vi.fn();

vi.mock("@/lib/api/collections", () => ({
  updateCollectionsSettings: (...args: unknown[]) => mockUpdateCollectionsSettings(...args),
}));

// The action still imports ApiError from "@/lib/api" for the error branch.
vi.mock("@/lib/api", () => ({
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

// Auth + capabilities are checked at the top of the action; stub them so the
// action proceeds to the API call.
vi.mock("@/lib/auth", () => ({
  getAuthContext: vi.fn().mockResolvedValue({ orgSlug: "acme" }),
}));

vi.mock("@/lib/api/capabilities", () => ({
  fetchMyCapabilities: vi.fn().mockResolvedValue({ isAdmin: true, isOwner: false }),
}));

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

import { updateCollectionsSettings } from "@/app/(app)/org/[slug]/settings/collections/actions";

describe("updateCollectionsSettings — endpoint wiring", () => {
  beforeEach(() => {
    mockUpdateCollectionsSettings.mockReset();
    mockUpdateCollectionsSettings.mockResolvedValue(undefined);
  });

  it("PUTs the full 5-field DTO through the collections client", async () => {
    const dto = {
      collectionsEnabled: true,
      stage1DaysOverdue: 7,
      stage2DaysOverdue: 21,
      stage3DaysOverdue: 45,
      escalateDaysOverdue: 60,
    };

    const result = await updateCollectionsSettings("acme", dto);

    expect(result.success).toBe(true);
    expect(mockUpdateCollectionsSettings).toHaveBeenCalledWith(dto);
  });

  it("returns an org-mismatch error without calling the API when the slug differs", async () => {
    const result = await updateCollectionsSettings("not-acme", {
      collectionsEnabled: false,
      stage1DaysOverdue: 7,
      stage2DaysOverdue: 21,
      stage3DaysOverdue: 45,
      escalateDaysOverdue: 60,
    });

    expect(result.success).toBe(false);
    expect(mockUpdateCollectionsSettings).not.toHaveBeenCalled();
  });
});
