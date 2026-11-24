import { describe, it, expect } from "vitest";
import {
  absentWidgets,
  buildHomePlan,
  kpiWindow,
  resolveHomeAudience,
  rowSpanClass,
  withoutAbsent,
  type HomeCaps,
} from "@/lib/dashboard-home";

// Phase 87 614B — Home widget matrix (arch §12.6.3 / §12.11.2): audiences are capability bundles,
// widgets are present or absent, and each widget also needs its own gate.

const ALL_MODULES = ["trust_accounting", "disbursements", "court_calendar"];

/** Built-in member role (V1 seed): VIEW_LEGAL, VIEW_TRUST, MANAGE_DISBURSEMENTS, … — no financials. */
const MEMBER: HomeCaps = {
  capabilities: ["VIEW_LEGAL", "VIEW_TRUST", "MANAGE_DISBURSEMENTS", "AI_EXECUTE"],
  isAdmin: false,
  isOwner: false,
};
const ADMIN: HomeCaps = { capabilities: [], isAdmin: true, isOwner: false };
const OWNER: HomeCaps = { capabilities: [], isAdmin: false, isOwner: true };
const BOOKKEEPER: HomeCaps = {
  capabilities: ["FINANCIAL_VISIBILITY", "INVOICING", "VIEW_TRUST", "CUSTOMER_MANAGEMENT"],
  isAdmin: false,
  isOwner: false,
};

describe("resolveHomeAudience", () => {
  it("owner → owner, admin → firm, plain member → fee earner", () => {
    expect(resolveHomeAudience(OWNER)).toBe("owner");
    expect(resolveHomeAudience({ ...OWNER, isAdmin: true })).toBe("owner");
    expect(resolveHomeAudience(ADMIN)).toBe("firm");
    expect(resolveHomeAudience(MEMBER)).toBe("feeEarner");
  });

  it("a member with VIEW_TRUST (the built-in member role) stays a fee earner (D-3 regression)", () => {
    expect(
      resolveHomeAudience({ capabilities: ["VIEW_TRUST"], isAdmin: false, isOwner: false })
    ).toBe("feeEarner");
  });

  it("INVOICING or CUSTOMER_MANAGEMENT alone does not promote a member", () => {
    expect(
      resolveHomeAudience({
        capabilities: ["INVOICING", "CUSTOMER_MANAGEMENT", "VIEW_TRUST"],
        isAdmin: false,
        isOwner: false,
      })
    ).toBe("feeEarner");
  });

  it("FINANCIAL_VISIBILITY makes a member a bookkeeper (firm audience)", () => {
    expect(resolveHomeAudience(BOOKKEEPER)).toBe("firm");
  });
});

describe("buildHomePlan", () => {
  it("fee earner: diary → tasks + my unbilled time → matter health + activity; no firm widgets", () => {
    const plan = buildHomePlan(MEMBER, ALL_MODULES);
    expect(plan.audience).toBe("feeEarner");
    expect(plan.diaryScope).toBe("me");
    expect(plan.rows).toEqual([["diary"], ["tasks", "unbilledWip"], ["matterHealth", "activity"]]);
    expect(plan.kpis).toBe(false); // B2 — the KPI endpoint is firm-wide
    expect(plan.trustPosition).toBe(false);
    expect(plan.debtors).toBe(false);
    expect(plan.ficaOutstanding).toBe(false);
    expect(plan.sensitive).toBe(false);
    expect(plan.unbilledWipLink).toBe(false);
  });

  it("bookkeeper/admin: KPIs → diary → WIP + debtors + trust → FICA + sensitive → activity", () => {
    const plan = buildHomePlan(ADMIN, ALL_MODULES);
    expect(plan.audience).toBe("firm");
    expect(plan.kpis).toBe(true);
    expect(plan.diaryScope).toBe("firm");
    expect(plan.rows).toEqual([
      ["kpis"],
      ["diary"],
      ["unbilledWip", "debtors", "trustPosition"],
      ["ficaOutstanding", "sensitive"],
      ["activity"],
    ]);
    expect(plan.tasks).toBe(false);
    expect(plan.matterHealth).toBe(false);
    expect(plan.unbilledWipLink).toBe(true);
  });

  it("owner: sign-off → KPIs → diary → WIP + debtors + trust → FICA + health + sensitive → activity", () => {
    const plan = buildHomePlan(OWNER, ALL_MODULES);
    expect(plan.audience).toBe("owner");
    expect(plan.kpis).toBe(true);
    expect(plan.diaryScope).toBe("firm");
    expect(plan.rows).toEqual([
      ["takeOnSignOff"],
      ["kpis"],
      ["diary"],
      ["unbilledWip", "debtors", "trustPosition"],
      ["ficaOutstanding", "matterHealth", "sensitive"],
      ["activity"],
    ]);
  });

  it("a non-admin bookkeeper's diary is their own (never firm-wide for members)", () => {
    expect(buildHomePlan(BOOKKEEPER, ALL_MODULES).diaryScope).toBe("me");
  });

  it("trust needs the trust_accounting module as well as VIEW_TRUST", () => {
    const plan = buildHomePlan(OWNER, ["disbursements"]);
    expect(plan.trustPosition).toBe(false);
    expect(plan.rows[2]).toEqual(["unbilledWip", "debtors"]);
  });

  it("each firm widget needs its own capability; sensitive needs TEAM_OVERSIGHT for non-admins", () => {
    const plan = buildHomePlan(
      { capabilities: ["FINANCIAL_VISIBILITY"], isAdmin: false, isOwner: false },
      ALL_MODULES
    );
    expect(plan.audience).toBe("firm");
    expect(plan.debtors).toBe(false);
    expect(plan.trustPosition).toBe(false);
    expect(plan.ficaOutstanding).toBe(false);
    expect(plan.sensitive).toBe(false);
    expect(plan.unbilledWipLink).toBe(false);
    expect(plan.kpis).toBe(true); // FINANCIAL_VISIBILITY alone → firm audience → KPI row
    expect(plan.rows).toEqual([["kpis"], ["diary"], ["unbilledWip"], ["activity"]]);

    const withOversight = buildHomePlan(
      { capabilities: ["FINANCIAL_VISIBILITY", "TEAM_OVERSIGHT"], isAdmin: false, isOwner: false },
      ALL_MODULES
    );
    expect(withOversight.sensitive).toBe(true);
  });

  it("a fee earner with TEAM_OVERSIGHT still gets no sensitive-events widget (not in their row)", () => {
    const plan = buildHomePlan({ ...MEMBER, capabilities: ["TEAM_OVERSIGHT"] }, ALL_MODULES);
    expect(plan.sensitive).toBe(false);
  });
});

// Phase 93 657C (R-10, FD-4): the trust take-on sign-off widget — owner-only SIGN_OFF_TRUST_TAKE_ON
// (or a role granted it) with VIEW_TRUST and the trust module. Never `hasCapability`: an admin does
// not inherit the capability.
describe("buildHomePlan — takeOnSignOff gate (657C)", () => {
  const PARTNER: HomeCaps = {
    capabilities: ["SIGN_OFF_TRUST_TAKE_ON", "VIEW_TRUST"],
    isAdmin: false,
    isOwner: false,
  };

  it("owner: shown, in the first row", () => {
    const plan = buildHomePlan(OWNER, ALL_MODULES);
    expect(plan.takeOnSignOff).toBe(true);
    expect(plan.rows[0]).toEqual(["takeOnSignOff"]);
  });

  it("admin: never — admins do not inherit the owner-only capability", () => {
    const plan = buildHomePlan(ADMIN, ALL_MODULES);
    expect(plan.takeOnSignOff).toBe(false);
    expect(plan.rows.flat()).not.toContain("takeOnSignOff");
  });

  it("member with VIEW_TRUST only: never", () => {
    const plan = buildHomePlan(MEMBER, ALL_MODULES);
    expect(plan.takeOnSignOff).toBe(false);
    expect(plan.rows.flat()).not.toContain("takeOnSignOff");
  });

  it("custom role with both capabilities: shown, first row of the fee-earner audience", () => {
    const plan = buildHomePlan(PARTNER, ALL_MODULES);
    expect(plan.audience).toBe("feeEarner");
    expect(plan.takeOnSignOff).toBe(true);
    expect(plan.rows).toEqual([
      ["takeOnSignOff"],
      ["diary"],
      ["tasks", "unbilledWip"],
      ["matterHealth", "activity"],
    ]);
  });

  it("a firm-audience role with both capabilities: first row, above the KPIs", () => {
    const plan = buildHomePlan(
      { ...PARTNER, capabilities: [...PARTNER.capabilities, "FINANCIAL_VISIBILITY"] },
      ALL_MODULES
    );
    expect(plan.audience).toBe("firm");
    expect(plan.rows.slice(0, 2)).toEqual([["takeOnSignOff"], ["kpis"]]);
  });

  it("either capability alone: never", () => {
    for (const capabilities of [["SIGN_OFF_TRUST_TAKE_ON"], ["VIEW_TRUST"]]) {
      expect(buildHomePlan({ ...PARTNER, capabilities }, ALL_MODULES).takeOnSignOff).toBe(false);
    }
  });

  it("trust module off: never, not even for the owner", () => {
    expect(buildHomePlan(OWNER, ["disbursements"]).takeOnSignOff).toBe(false);
    expect(buildHomePlan(PARTNER, ["disbursements"]).takeOnSignOff).toBe(false);
  });
});

describe("withoutAbsent", () => {
  it("drops absent widgets and the rows they leave empty", () => {
    const rows = buildHomePlan(OWNER, ALL_MODULES).rows;
    expect(withoutAbsent(rows, new Set(["debtors", "trustPosition"]))).toEqual([
      ["takeOnSignOff"],
      ["kpis"],
      ["diary"],
      ["unbilledWip"],
      ["ficaOutstanding", "matterHealth", "sensitive"],
      ["activity"],
    ]);
    // A KPI read that came back absent (403/404) drops the whole KPI row; an empty sign-off queue
    // (absent, 657C) drops the sign-off row the same way.
    expect(withoutAbsent(rows, new Set(["takeOnSignOff", "kpis"]))[0]).toEqual(["diary"]);
  });
});

describe("absentWidgets", () => {
  it("collects every absent WidgetResult by widget name; ok, error and non-results stay", () => {
    expect(
      absentWidgets({
        kpis: { state: "absent" },
        diary: [],
        tasks: { state: "absent" },
        unbilledWip: { state: "ok", data: {} },
        debtors: { state: "error" },
        trustPosition: { state: "absent" },
        activity: null,
        matterHealth: null,
      })
    ).toEqual(new Set(["kpis", "tasks", "trustPosition"]));
  });
});

describe("kpiWindow", () => {
  it("is the last 30 days including today (from = today − 29 days), zone-free", () => {
    expect(kpiWindow("2026-10-21")).toEqual({ from: "2026-09-22", to: "2026-10-21" });
    expect(kpiWindow("2027-01-05")).toEqual({ from: "2026-12-07", to: "2027-01-05" });
    expect(kpiWindow("2026-03-01")).toEqual({ from: "2026-01-31", to: "2026-03-01" });
  });
});

describe("rowSpanClass", () => {
  it("maps present-widget count to literal 12-column spans", () => {
    expect(rowSpanClass(1)).toBe("lg:col-span-12");
    expect(rowSpanClass(2)).toBe("lg:col-span-6");
    // 3-up wraps 2 + 1 (last cell full-width) below 2xl; three-across only from 2xl (640A OBS).
    expect(rowSpanClass(3)).toBe("lg:col-span-6 lg:max-2xl:last:col-span-12 2xl:col-span-4");
    expect(rowSpanClass(4)).toBe("lg:col-span-6"); // 4-up wraps 2×2, never quarter cells
    expect(rowSpanClass(5)).toBe("lg:col-span-12");
  });
});
