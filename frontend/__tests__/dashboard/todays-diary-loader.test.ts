import { describe, it, expect, vi, beforeEach } from "vitest";

// Phase 87 613D — My work's "Today's diary": GET /api/diary?from=today&to=today scoped to the
// current user for every role; fails soft (null) except an expired session, which re-logs in.

vi.mock("server-only", () => ({}));

const { mockGetDiary, mockRedirectToReLogin, mockResolveReturnTo, MockApiError } = vi.hoisted(
  () => {
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
      mockGetDiary: vi.fn(),
      mockRedirectToReLogin: vi.fn(() => {
        throw new Error("NEXT_REDIRECT");
      }),
      mockResolveReturnTo: vi.fn(),
      MockApiError,
    };
  }
);

vi.mock("@/app/(app)/org/[slug]/diary/diary-actions", () => ({ getDiary: mockGetDiary }));
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

import { loadTodaysDiary, loadUpcomingDiary } from "@/app/(app)/org/[slug]/my-work/todays-diary";

const TODAY = "2026-10-21";
const EVENT = {
  id: "t1",
  kind: "TASK_DUE",
  date: TODAY,
  title: "File heads of argument",
  status: "OPEN",
  matterId: "m1",
  matterName: "Ndlovu v RAF",
  overdue: false,
  href: "/matters/m1?taskId=t1",
};

describe("loadTodaysDiary", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, "error").mockImplementation(() => {});
    mockResolveReturnTo.mockResolvedValue("/org/acme/my-work");
  });

  it("fetches today only, scoped to the current member (fee earner = me)", async () => {
    mockGetDiary.mockResolvedValue({ events: [EVENT], typesServed: ["TASK_DUE"] });

    await expect(loadTodaysDiary(TODAY, "member-1")).resolves.toEqual([EVENT]);
    expect(mockGetDiary).toHaveBeenCalledWith({
      from: TODAY,
      to: TODAY,
      feeEarnerId: "member-1",
    });
  });

  it('returns "unidentified" without fetching when the current member is unknown (no firm-wide fallback)', async () => {
    await expect(loadTodaysDiary(TODAY, null)).resolves.toBe("unidentified");
    expect(mockGetDiary).not.toHaveBeenCalled();
  });

  it("firmWide (Home, admin/owner): fetches everyone's day without a fee-earner filter or member", async () => {
    mockGetDiary.mockResolvedValue({ events: [EVENT], typesServed: ["TASK_DUE"] });

    await expect(loadTodaysDiary(TODAY, null, { firmWide: true })).resolves.toEqual([EVENT]);
    expect(mockGetDiary).toHaveBeenCalledWith({ from: TODAY, to: TODAY });
  });

  it("fails soft (null) on a non-401 error", async () => {
    mockGetDiary.mockRejectedValue(new MockApiError(500, "boom"));

    await expect(loadTodaysDiary(TODAY, "member-1")).resolves.toBeNull();
    expect(mockRedirectToReLogin).not.toHaveBeenCalled();
  });

  it("routes an expired-session 401 to the branded re-login", async () => {
    mockGetDiary.mockRejectedValue(new MockApiError(401, "Authentication session expired"));

    await expect(loadTodaysDiary(TODAY, "member-1")).rejects.toThrow("NEXT_REDIRECT");
    expect(mockRedirectToReLogin).toHaveBeenCalledWith("/org/acme/my-work", "expired");
  });

  it("does not swallow getDiary's own re-login redirect", async () => {
    mockGetDiary.mockRejectedValue(new Error("NEXT_REDIRECT"));

    await expect(loadTodaysDiary(TODAY, "member-1")).rejects.toThrow("NEXT_REDIRECT");
    expect(mockRedirectToReLogin).not.toHaveBeenCalled();
  });
});

// Phase 88 G-D5 — My work's "Upcoming" list and "Next deadline": tomorrow through today + 14,
// scoped to the current member, without the overdue band (Today's diary already shows it).
describe("loadUpcomingDiary", () => {
  const COURT_DATE = {
    id: "cd1",
    kind: "COURT_DATE",
    date: "2026-10-24",
    time: "10:00:00",
    title: "Case Management Conference",
    status: "SCHEDULED",
    matterId: "m1",
    matterName: "Ndlovu v RAF",
    overdue: false,
    href: "/matters/m1?tab=court-calendar",
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, "error").mockImplementation(() => {});
    mockResolveReturnTo.mockResolvedValue("/org/acme/my-work");
  });

  it("fetches tomorrow through today + 14 for the current member, without the overdue band", async () => {
    mockGetDiary.mockResolvedValue({ events: [COURT_DATE], typesServed: ["COURT_DATE"] });

    await expect(loadUpcomingDiary(TODAY, "member-1")).resolves.toEqual([COURT_DATE]);
    expect(mockGetDiary).toHaveBeenCalledWith({
      from: "2026-10-22",
      to: "2026-11-04",
      feeEarnerId: "member-1",
      includeOverdue: false,
    });
  });

  it('returns "unidentified" without fetching when the current member is unknown (no firm-wide fallback)', async () => {
    await expect(loadUpcomingDiary(TODAY, null)).resolves.toBe("unidentified");
    expect(mockGetDiary).not.toHaveBeenCalled();
  });

  it("fails soft (null) on a non-401 error", async () => {
    mockGetDiary.mockRejectedValue(new MockApiError(500, "boom"));

    await expect(loadUpcomingDiary(TODAY, "member-1")).resolves.toBeNull();
    expect(mockRedirectToReLogin).not.toHaveBeenCalled();
  });

  it("routes an expired-session 401 to the branded re-login", async () => {
    mockGetDiary.mockRejectedValue(new MockApiError(401, "Authentication session expired"));

    await expect(loadUpcomingDiary(TODAY, "member-1")).rejects.toThrow("NEXT_REDIRECT");
    expect(mockRedirectToReLogin).toHaveBeenCalledWith("/org/acme/my-work", "expired");
  });

  it("does not swallow getDiary's own re-login redirect", async () => {
    mockGetDiary.mockRejectedValue(new Error("NEXT_REDIRECT"));

    await expect(loadUpcomingDiary(TODAY, "member-1")).rejects.toThrow("NEXT_REDIRECT");
    expect(mockRedirectToReLogin).not.toHaveBeenCalled();
  });
});
