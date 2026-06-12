import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock server-only so the "use server" module loads in the test environment.
vi.mock("server-only", () => ({}));

// Mock the api module so we can observe which endpoint / payload the action
// actually wires up — this is the whole point of these tests.
const mockPut = vi.fn();
const mockPatch = vi.fn();

vi.mock("@/lib/api", () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: (...args: unknown[]) => mockPut(...args),
    patch: (...args: unknown[]) => mockPatch(...args),
    delete: vi.fn(),
  },
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

import { updateTimeTrackingSettings } from "@/app/(app)/org/[slug]/settings/time-tracking/actions";

describe("updateTimeTrackingSettings — endpoint wiring", () => {
  beforeEach(() => {
    mockPut.mockReset();
    mockPatch.mockReset();
    mockPut.mockResolvedValue(undefined);
    mockPatch.mockResolvedValue(undefined);
  });

  it("PATCHes /api/settings/time-reminders with hours→minutes conversion", async () => {
    const result = await updateTimeTrackingSettings("acme", {
      timeReminderEnabled: true,
      timeReminderDays: "MON,TUE,WED,THU,FRI",
      timeReminderTime: "17:00",
      timeReminderMinHours: 4.0,
      defaultExpenseMarkupPercent: null,
    });

    expect(result.success).toBe(true);

    // Must hit the real reminders endpoint with minutes, not PUT /api/settings.
    expect(mockPatch).toHaveBeenCalledWith("/api/settings/time-reminders", {
      timeReminderEnabled: true,
      timeReminderDays: "MON,TUE,WED,THU,FRI",
      timeReminderTime: "17:00",
      timeReminderMinMinutes: 240,
    });

    // The dead PUT /api/settings (UpdateSettingsRequest) must NOT be called.
    expect(mockPut).not.toHaveBeenCalled();
  });

  it("rounds fractional hours to whole minutes", async () => {
    await updateTimeTrackingSettings("acme", {
      timeReminderEnabled: false,
      timeReminderDays: "MON,WED,FRI",
      timeReminderTime: "09:30",
      timeReminderMinHours: 2.5,
      defaultExpenseMarkupPercent: null,
    });

    expect(mockPatch).toHaveBeenCalledWith("/api/settings/time-reminders", {
      timeReminderEnabled: false,
      timeReminderDays: "MON,WED,FRI",
      timeReminderTime: "09:30",
      timeReminderMinMinutes: 150,
    });
  });
});
