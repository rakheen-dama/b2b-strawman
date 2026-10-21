import { describe, it, expect, vi, beforeEach } from "vitest";
import { resolveDateRange } from "@/lib/date-utils";

// Phase 87 614B — Home fan-out (arch §12.5.3): each read only when its gate passes; 403/404 →
// absent; 401 → branded re-login; anything else → the card's quiet error line.

vi.mock("server-only", () => ({}));

const {
  mockGet,
  mockGetDebtors,
  mockGetDiary,
  mockEmail,
  mockRedirectToReLogin,
  mockResolveReturnTo,
  MockApiError,
} = vi.hoisted(() => {
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
    mockGet: vi.fn(),
    mockGetDebtors: vi.fn(),
    mockGetDiary: vi.fn(),
    mockEmail: vi.fn(),
    mockRedirectToReLogin: vi.fn(() => {
      throw new Error("NEXT_REDIRECT");
    }),
    mockResolveReturnTo: vi.fn(),
    MockApiError,
  };
});

vi.mock("@/lib/api/client", () => ({
  api: { get: mockGet },
  ApiError: MockApiError,
  resolveServerReturnTo: mockResolveReturnTo,
}));
vi.mock("@/lib/api/collections", () => ({ getDebtors: mockGetDebtors }));
vi.mock("@/lib/auth", () => ({ getCurrentUserEmail: mockEmail }));
vi.mock("@/app/(app)/org/[slug]/diary/diary-actions", () => ({ getDiary: mockGetDiary }));
vi.mock("@/lib/auth/expiry", () => ({ redirectToReLogin: mockRedirectToReLogin }));
vi.mock("next/navigation", () => ({
  unstable_rethrow: (error: unknown) => {
    if (error instanceof Error && error.message === "NEXT_REDIRECT") throw error;
  },
}));

import { fetchHomeWidgets } from "@/lib/actions/dashboard";
import { buildHomePlan, type HomeCaps } from "@/lib/dashboard-home";

const TODAY = "2026-10-21";
const ALL_MODULES = ["trust_accounting", "disbursements", "court_calendar"];
const MEMBER: HomeCaps = {
  capabilities: ["VIEW_LEGAL", "VIEW_TRUST"],
  isAdmin: false,
  isOwner: false,
};
const ADMIN: HomeCaps = { capabilities: [], isAdmin: true, isOwner: false };
const OWNER: HomeCaps = { capabilities: [], isAdmin: false, isOwner: true };

const MEMBERS = [
  { id: "member-carol", name: "Carol", email: "carol@example.com" },
  { id: "member-alice", name: "Alice", email: "alice@example.com" },
];

/** Every GET succeeds with a recognisable payload, keyed by URL. */
function routeGets(overrides: Record<string, () => unknown> = {}) {
  mockGet.mockImplementation(async (url: string) => {
    if (overrides[url]) return overrides[url]();
    if (url === "/api/members") return MEMBERS;
    return { url };
  });
}

function calledUrls(): string[] {
  return mockGet.mock.calls.map((c) => c[0] as string);
}

describe("fetchHomeWidgets — gate → call matrix", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, "error").mockImplementation(() => {});
    mockResolveReturnTo.mockResolvedValue("/org/acme/dashboard");
    mockEmail.mockResolvedValue("carol@example.com");
    mockGetDiary.mockResolvedValue({ events: [], typesServed: [] });
    mockGetDebtors.mockResolvedValue({ content: [], page: { totalElements: 0 } });
    routeGets();
  });

  it("fee earner: own diary, tasks, redacted WIP, matter health, activity — no firm reads", async () => {
    const data = await fetchHomeWidgets(buildHomePlan(MEMBER, ALL_MODULES), TODAY);

    expect(mockGetDiary).toHaveBeenCalledWith({
      from: TODAY,
      to: TODAY,
      feeEarnerId: "member-carol",
    });
    expect(calledUrls()).toEqual(
      expect.arrayContaining([
        "/api/my-work/tasks",
        "/api/dashboard/unbilled-wip",
        "/api/dashboard/matter-health",
        "/api/dashboard/activity?limit=15",
      ])
    );
    expect(calledUrls()).not.toContain("/api/dashboard/trust-position");
    expect(calledUrls()).not.toContain("/api/dashboard/fica-outstanding?limit=5");
    expect(mockGetDebtors).not.toHaveBeenCalled();
    expect(data.tasks.state).toBe("ok");
    expect(data.trustPosition).toEqual({ state: "absent" });
    expect(data.debtors).toEqual({ state: "absent" });
    expect(data.ficaOutstanding).toEqual({ state: "absent" });
  });

  it("admin (bookkeeper row): firm-wide diary, WIP, debtors, trust, FICA, activity — no tasks or health", async () => {
    const data = await fetchHomeWidgets(buildHomePlan(ADMIN, ALL_MODULES), TODAY);

    expect(mockGetDiary).toHaveBeenCalledWith({ from: TODAY, to: TODAY });
    expect(calledUrls()).not.toContain("/api/members");
    expect(calledUrls()).toEqual(
      expect.arrayContaining([
        "/api/dashboard/unbilled-wip",
        "/api/dashboard/trust-position",
        "/api/dashboard/fica-outstanding?limit=5",
        "/api/dashboard/activity?limit=15",
      ])
    );
    expect(mockGetDebtors).toHaveBeenCalledWith({ size: 5 });
    expect(calledUrls()).not.toContain("/api/my-work/tasks");
    expect(calledUrls()).not.toContain("/api/dashboard/matter-health");
    expect(data.tasks).toEqual({ state: "absent" });
    expect(data.matterHealth).toBeNull();
    expect(data.trustPosition).toEqual({
      state: "ok",
      data: { url: "/api/dashboard/trust-position" },
    });
  });

  it("owner: every firm read plus matter health; no tasks", async () => {
    await fetchHomeWidgets(buildHomePlan(OWNER, ALL_MODULES), TODAY);

    expect(calledUrls()).toEqual(
      expect.arrayContaining([
        "/api/dashboard/unbilled-wip",
        "/api/dashboard/trust-position",
        "/api/dashboard/fica-outstanding?limit=5",
        "/api/dashboard/matter-health",
        "/api/dashboard/activity?limit=15",
      ])
    );
    expect(mockGetDebtors).toHaveBeenCalledWith({ size: 5 });
    expect(calledUrls()).not.toContain("/api/my-work/tasks");
  });

  it("module trust_accounting off → no trust-position call", async () => {
    const data = await fetchHomeWidgets(buildHomePlan(OWNER, ["disbursements"]), TODAY);

    expect(calledUrls()).not.toContain("/api/dashboard/trust-position");
    expect(data.trustPosition).toEqual({ state: "absent" });
  });

  it("trust 404 (no trust account) and 403 (module off) → absent, not an error", async () => {
    routeGets({
      "/api/dashboard/trust-position": () => {
        throw new MockApiError(404, "No trust account");
      },
    });
    const notFound = await fetchHomeWidgets(buildHomePlan(OWNER, ALL_MODULES), TODAY);
    expect(notFound.trustPosition).toEqual({ state: "absent" });

    routeGets({
      "/api/dashboard/trust-position": () => {
        throw new MockApiError(403, "Module not enabled");
      },
    });
    const forbidden = await fetchHomeWidgets(buildHomePlan(OWNER, ALL_MODULES), TODAY);
    expect(forbidden.trustPosition).toEqual({ state: "absent" });
    expect(mockRedirectToReLogin).not.toHaveBeenCalled();
  });

  it("FICA and debtors 403 → absent", async () => {
    routeGets({
      "/api/dashboard/fica-outstanding?limit=5": () => {
        throw new MockApiError(403, "Forbidden");
      },
    });
    mockGetDebtors.mockRejectedValue(new MockApiError(403, "Forbidden"));

    const data = await fetchHomeWidgets(buildHomePlan(ADMIN, ALL_MODULES), TODAY);

    expect(data.ficaOutstanding).toEqual({ state: "absent" });
    expect(data.debtors).toEqual({ state: "absent" });
  });

  it("any other failure → error state (the card shows its error line); the rest still load", async () => {
    routeGets({
      "/api/dashboard/unbilled-wip": () => {
        throw new MockApiError(500, "boom");
      },
    });

    const data = await fetchHomeWidgets(buildHomePlan(ADMIN, ALL_MODULES), TODAY);

    expect(data.unbilledWip).toEqual({ state: "error" });
    expect(data.trustPosition.state).toBe("ok");
    expect(mockRedirectToReLogin).not.toHaveBeenCalled();
  });

  it("a 401 from a gated read goes to the branded re-login", async () => {
    routeGets({
      "/api/dashboard/fica-outstanding?limit=5": () => {
        throw new MockApiError(401, "Authentication session expired");
      },
    });

    await expect(fetchHomeWidgets(buildHomePlan(ADMIN, ALL_MODULES), TODAY)).rejects.toThrow(
      "NEXT_REDIRECT"
    );
    expect(mockRedirectToReLogin).toHaveBeenCalledWith("/org/acme/dashboard", "expired");
  });

  it("member whose profile can't be matched → 'unidentified' diary, never firm-wide", async () => {
    mockEmail.mockResolvedValue("stranger@example.com");

    const data = await fetchHomeWidgets(buildHomePlan(MEMBER, ALL_MODULES), TODAY);

    expect(data.diary).toBe("unidentified");
    expect(mockGetDiary).not.toHaveBeenCalled();
  });
});

describe("resolveDateRange", () => {
  it("returns provided from and to when both are present", () => {
    const result = resolveDateRange({ from: "2025-01-01", to: "2025-01-31" });
    expect(result).toEqual({ from: "2025-01-01", to: "2025-01-31" });
  });

  it("defaults to current month when neither from nor to is provided", () => {
    const result = resolveDateRange({});
    const now = new Date();
    const expectedYear = now.getFullYear();
    const expectedMonth = String(now.getMonth() + 1).padStart(2, "0");

    expect(result.from).toMatch(new RegExp(`^${expectedYear}-${expectedMonth}-01$`));
    // Last day of month varies, just check it starts with the right year-month
    expect(result.to).toMatch(new RegExp(`^${expectedYear}-${expectedMonth}-\\d{2}$`));
  });

  it("uses provided from with default to when only from is given", () => {
    const result = resolveDateRange({ from: "2025-03-15" });
    expect(result.from).toBe("2025-03-15");
    // to defaults to end of current month
    expect(result.to).toBeDefined();
  });

  it("uses provided to with default from when only to is given", () => {
    const result = resolveDateRange({ to: "2025-06-30" });
    expect(result.to).toBe("2025-06-30");
    // from defaults to start of current month
    expect(result.from).toBeDefined();
  });
});
