import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { isValidElement, type ReactElement } from "react";
import { formatCurrency, formatLocalDate } from "@/lib/format";
import { formatChartValue } from "@/lib/chart-format";
import type { HeroTile } from "@/components/shared/detail-hero";

// Phase 90 641B — matter hero tiles (arch §15.3.5, ADR-373): each read only under its own gate;
// gate fails / 403 / 404 → tile omitted; any other failure → state "error"; a real zero → the
// formatted zero; tiles carry only data across the RSC boundary (tasks OQ #26).

vi.mock("server-only", () => ({}));

const { mockUnbilled, mockDeadlines, mockOutstanding, mockTrust, MockApiError } = vi.hoisted(() => {
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
    mockUnbilled: vi.fn(),
    mockDeadlines: vi.fn(),
    mockOutstanding: vi.fn(),
    mockTrust: vi.fn(),
    MockApiError,
  };
});

vi.mock("@/lib/api/matters", () => ({
  getMatterUnbilledSummary: mockUnbilled,
  getMatterUpcomingDeadlines: mockDeadlines,
}));
vi.mock("@/app/(app)/org/[slug]/invoices/invoice-crud-actions", () => ({
  fetchOutstandingSummaryAction: mockOutstanding,
}));
vi.mock("@/app/(app)/org/[slug]/trust-accounting/actions", () => ({
  fetchMatterTrustBalance: mockTrust,
}));
// The real settle(): its deps are the API client's ApiError and the expired-session funnel.
vi.mock("@/lib/api/client", () => ({ ApiError: MockApiError }));
vi.mock("@/lib/auth/expiry-server", () => ({ rethrowOnExpiredSession: vi.fn() }));

import {
  buildMatterHeroTiles,
  loadMatterHeroData,
  upcomingDeadlineRows,
} from "@/app/(app)/org/[slug]/matters/[id]/hero-tiles";
import type { HomeCaps } from "@/lib/dashboard-home";

const MATTER = "m-1";
const ALL_MODULES = { trustEnabled: true, courtCalendarEnabled: true };

const ADMIN: HomeCaps = { capabilities: [], isAdmin: true, isOwner: false };
const OWNER: HomeCaps = { capabilities: [], isAdmin: false, isOwner: true };
/** The built-in member role (tenant V1 baseline): no PROJECT_MANAGEMENT, no FINANCIAL_VISIBILITY. */
const MEMBER: HomeCaps = {
  capabilities: ["VIEW_LEGAL", "VIEW_TRUST", "MANAGE_DISBURSEMENTS"],
  isAdmin: false,
  isOwner: false,
};
const PM_ONLY: HomeCaps = {
  capabilities: ["PROJECT_MANAGEMENT", "VIEW_TRUST"],
  isAdmin: false,
  isOwner: false,
};

const UNBILLED = {
  totalHours: 8.5,
  totalAmount: 12500,
  currency: "ZAR",
  entryCount: 6,
  byMatter: null,
};
const TRUST = { matterId: MATTER, balance: 50000, currency: "ZAR" };
const OUTSTANDING = {
  currency: "ZAR",
  outstandingTotal: 3450,
  outstandingCount: 2,
  overdueTotal: 1200,
  overdueCount: 2,
  approvedUnsentTotal: 0,
  approvedUnsentCount: 0,
};
const DEADLINES = [
  {
    type: "COURT" as const,
    date: "2026-12-01",
    description: "Pre-trial conference",
    status: "SCHEDULED",
  },
  { type: "COURT" as const, date: "2027-01-15", description: "Trial", status: "SCHEDULED" },
];

async function tilesFor(caps: HomeCaps): Promise<HeroTile[]> {
  return buildMatterHeroTiles(await loadMatterHeroData(MATTER, caps, ALL_MODULES));
}

function tileIds(tiles: HeroTile[]): string[] {
  return tiles.map((t) => t.id);
}

/** Paths the RSC boundary cannot carry: functions anywhere, elements outside value / hint. */
function unserializablePaths(value: unknown, path: string, inSlot: boolean): string[] {
  if (typeof value === "function") return [path];
  if (isValidElement(value)) return inSlot ? [] : [path];
  if (Array.isArray(value)) {
    return value.flatMap((item, i) => unserializablePaths(item, `${path}[${i}]`, inSlot));
  }
  if (typeof value === "object" && value !== null) {
    return Object.entries(value).flatMap(([key, item]) =>
      unserializablePaths(item, `${path}.${key}`, inSlot || key === "value" || key === "hint")
    );
  }
  return [];
}

describe("matter hero tiles (641B)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(console, "error").mockImplementation(() => {});
    mockUnbilled.mockResolvedValue(UNBILLED);
    mockTrust.mockResolvedValue({ state: "ok", data: TRUST });
    mockOutstanding.mockResolvedValue({ state: "ok", data: OUTSTANDING });
    mockDeadlines.mockResolvedValue(DEADLINES);
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it.each([
    ["admin", ADMIN],
    ["owner", OWNER],
  ])("%s: four tiles in order with formatted values", async (_, caps) => {
    const tiles = await tilesFor(caps);

    expect(tiles.map((t) => t.label)).toEqual([
      "Unbilled WIP",
      "Trust balance",
      "Outstanding",
      "Next court date",
    ]);
    expect(tiles.every((t) => t.state === "ready")).toBe(true);
    expect(tiles[0].value).toBe(formatCurrency(12500, "ZAR"));
    expect(tiles[0].hint).toBe(`${formatChartValue(8.5, "hours")} · 6 entries`);
    expect(tiles[1].value).toBe(formatCurrency(50000, "ZAR"));
    expect(tiles[2].value).toBe(formatCurrency(3450, "ZAR"));
    expect(tiles[3].value).toBe(formatLocalDate("2026-12-01"));
    expect(tiles[3].hint).toBe("Pre-trial conference");
    // Not links (641B decision): a ?tab= Link would round-trip the whole matter page.
    expect(tiles.some((t) => t.href)).toBe(false);
    expect(mockUnbilled).toHaveBeenCalledWith(MATTER);
    expect(mockOutstanding).toHaveBeenCalledWith(MATTER);
    expect(mockTrust).toHaveBeenCalledWith(MATTER);
  });

  it("member: WIP and Outstanding omitted without a request; trust and court present", async () => {
    const tiles = await tilesFor(MEMBER);

    expect(tileIds(tiles)).toEqual(["trust", "court"]);
    expect(mockUnbilled).not.toHaveBeenCalled();
    expect(mockOutstanding).not.toHaveBeenCalled();
    expect(mockTrust).toHaveBeenCalledTimes(1);
  });

  it("PROJECT_MANAGEMENT without FINANCIAL_VISIBILITY: WIP omitted and its endpoint never called", async () => {
    // The unbilled-summary endpoint does no financial redaction — the frontend gate is the gate.
    const tiles = await tilesFor(PM_ONLY);

    expect(tileIds(tiles)).not.toContain("wip");
    expect(mockUnbilled).not.toHaveBeenCalled();
  });

  it("FINANCIAL_VISIBILITY without PROJECT_MANAGEMENT: WIP omitted, Outstanding present", async () => {
    const tiles = await tilesFor({
      capabilities: ["FINANCIAL_VISIBILITY"],
      isAdmin: false,
      isOwner: false,
    });

    expect(tileIds(tiles)).toEqual(["outstanding", "court"]);
    expect(mockUnbilled).not.toHaveBeenCalled();
  });

  it("trust_accounting off: trust tile omitted and the balance never read", async () => {
    const data = await loadMatterHeroData(MATTER, ADMIN, { ...ALL_MODULES, trustEnabled: false });

    expect(data.trustBalance).toEqual({ state: "absent" });
    expect(tileIds(buildMatterHeroTiles(data))).toEqual(["wip", "outstanding", "court"]);
    expect(mockTrust).not.toHaveBeenCalled();
  });

  it("court_calendar off: court tile omitted, deadlines never read, KPIDashboard gets []", async () => {
    const data = await loadMatterHeroData(MATTER, ADMIN, {
      ...ALL_MODULES,
      courtCalendarEnabled: false,
    });

    expect(data.upcomingDeadlines).toEqual({ state: "absent" });
    expect(tileIds(buildMatterHeroTiles(data))).toEqual(["wip", "trust", "outstanding"]);
    expect(mockDeadlines).not.toHaveBeenCalled();
    expect(upcomingDeadlineRows(data)).toEqual([]);
  });

  it("no upcoming court dates: '—' (null value) with 'None scheduled'", async () => {
    mockDeadlines.mockResolvedValue([]);
    const court = (await tilesFor(ADMIN)).find((t) => t.id === "court");

    expect(court).toEqual({
      id: "court",
      label: "Next court date",
      value: null,
      hint: "None scheduled",
      state: "ready",
    });
  });

  it("a postponed hearing: the earlier POSTPONED row is skipped for the rescheduled SCHEDULED one", async () => {
    // CourtCalendarService.postponeCourtDate keeps the original row (POSTPONED) beside a new
    // SCHEDULED one; the backend filters by date only, so both come back.
    mockDeadlines.mockResolvedValue([
      { type: "COURT", date: "2026-12-01", description: "Pre-trial", status: "POSTPONED" },
      { type: "COURT", date: "2026-12-05", description: "Motion", status: "CANCELLED" },
      { type: "COURT", date: "2026-12-15", description: "Pre-trial", status: "SCHEDULED" },
    ]);
    const court = (await tilesFor(ADMIN)).find((t) => t.id === "court");

    expect(court?.value).toBe(formatLocalDate("2026-12-15"));
    expect(court?.hint).toBe("Pre-trial");
  });

  it("only cancelled / postponed / heard rows: '—' (null value) with 'None scheduled'", async () => {
    mockDeadlines.mockResolvedValue([
      { type: "COURT", date: "2026-12-01", description: "Trial", status: "CANCELLED" },
      { type: "COURT", date: "2026-12-02", description: "Hearing", status: "POSTPONED" },
      { type: "COURT", date: "2026-12-03", description: "Motion", status: "HEARD" },
    ]);
    const court = (await tilesFor(ADMIN)).find((t) => t.id === "court");

    expect(court).toEqual({
      id: "court",
      label: "Next court date",
      value: null,
      hint: "None scheduled",
      state: "ready",
    });
  });

  it("deadlines are read once and shared with KPIDashboard", async () => {
    const data = await loadMatterHeroData(MATTER, ADMIN, ALL_MODULES);

    expect(mockDeadlines).toHaveBeenCalledTimes(1);
    expect(mockDeadlines).toHaveBeenCalledWith(MATTER);
    expect(upcomingDeadlineRows(data)).toEqual(DEADLINES);
  });

  it("a failed deadlines read: court tile errors, KPIDashboard gets [] (as before)", async () => {
    mockDeadlines.mockRejectedValue(new Error("boom"));
    const data = await loadMatterHeroData(MATTER, ADMIN, ALL_MODULES);

    expect(buildMatterHeroTiles(data).find((t) => t.id === "court")).toEqual({
      id: "court",
      label: "Next court date",
      value: null,
      state: "error",
    });
    expect(upcomingDeadlineRows(data)).toEqual([]);
  });

  it("a failed outstanding read renders an error tile, never omitted", async () => {
    mockOutstanding.mockResolvedValue({ state: "error" });
    const outstanding = (await tilesFor(ADMIN)).find((t) => t.id === "outstanding");

    expect(outstanding).toEqual({
      id: "outstanding",
      label: "Outstanding",
      value: null,
      state: "error",
    });
  });

  it("unbilled read: a plain failure → error tile; a 403 → tile omitted", async () => {
    mockUnbilled.mockRejectedValueOnce(new Error("backend down"));
    expect((await tilesFor(ADMIN)).find((t) => t.id === "wip")?.state).toBe("error");

    mockUnbilled.mockRejectedValueOnce(new MockApiError(403, "Forbidden"));
    expect(tileIds(await tilesFor(ADMIN))).not.toContain("wip");
  });

  it("overdue > 0: the hint carries a --danger icon beside the count", async () => {
    const outstanding = (await tilesFor(ADMIN)).find((t) => t.id === "outstanding");

    expect(isValidElement(outstanding?.hint)).toBe(true);
    const { container } = render(outstanding?.hint as ReactElement);
    expect(screen.getByText("2 overdue")).toBeInTheDocument();
    const icon = container.querySelector("svg");
    expect(icon).toHaveAttribute("aria-hidden", "true");
    expect(icon).toHaveClass("text-danger");
  });

  it("overdue = 0: the hint is plain text, no icon", async () => {
    mockOutstanding.mockResolvedValue({
      state: "ok",
      data: { ...OUTSTANDING, overdueCount: 0, overdueTotal: 0 },
    });
    const outstanding = (await tilesFor(ADMIN)).find((t) => t.id === "outstanding");

    expect(outstanding?.hint).toBe("0 overdue");
  });

  it("a real zero renders the formatted zero, never omitted or '—'", async () => {
    mockTrust.mockResolvedValue({ state: "ok", data: { ...TRUST, balance: 0 } });
    mockUnbilled.mockResolvedValue({ ...UNBILLED, totalAmount: 0, totalHours: 0, entryCount: 1 });
    const tiles = await tilesFor(ADMIN);

    expect(tiles.find((t) => t.id === "trust")?.value).toBe(formatCurrency(0, "ZAR"));
    const wip = tiles.find((t) => t.id === "wip");
    expect(wip?.value).toBe(formatCurrency(0, "ZAR"));
    expect(wip?.hint).toBe(`${formatChartValue(0, "hours")} · 1 entry`);
  });

  it("tiles carry only serializable data across the RSC boundary (OQ #26)", async () => {
    const tiles = await tilesFor(ADMIN);
    // Non-vacuous: the overdue hint really is an element (allowed in the hint slot).
    expect(tiles.some((t) => isValidElement(t.hint))).toBe(true);

    expect(tiles.flatMap((tile, i) => unserializablePaths(tile, `tiles[${i}]`, false))).toEqual([]);
  });
});
