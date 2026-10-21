import { describe, it, expect } from "vitest";
import {
  absentWidgets,
  buildHomePlan,
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
    expect(plan.trustPosition).toBe(false);
    expect(plan.debtors).toBe(false);
    expect(plan.ficaOutstanding).toBe(false);
    expect(plan.sensitive).toBe(false);
    expect(plan.unbilledWipLink).toBe(false);
  });

  it("bookkeeper/admin: WIP + debtors + trust → diary + FICA → activity + sensitive", () => {
    const plan = buildHomePlan(ADMIN, ALL_MODULES);
    expect(plan.audience).toBe("firm");
    expect(plan.diaryScope).toBe("firm");
    expect(plan.rows).toEqual([
      ["unbilledWip", "debtors", "trustPosition"],
      ["diary", "ficaOutstanding"],
      ["activity", "sensitive"],
    ]);
    expect(plan.tasks).toBe(false);
    expect(plan.matterHealth).toBe(false);
    expect(plan.unbilledWipLink).toBe(true);
  });

  it("owner: diary + WIP → debtors + trust → FICA + health + activity + sensitive", () => {
    const plan = buildHomePlan(OWNER, ALL_MODULES);
    expect(plan.audience).toBe("owner");
    expect(plan.diaryScope).toBe("firm");
    expect(plan.rows).toEqual([
      ["diary", "unbilledWip"],
      ["debtors", "trustPosition"],
      ["ficaOutstanding", "matterHealth", "activity", "sensitive"],
    ]);
  });

  it("a non-admin bookkeeper's diary is their own (never firm-wide for members)", () => {
    expect(buildHomePlan(BOOKKEEPER, ALL_MODULES).diaryScope).toBe("me");
  });

  it("trust needs the trust_accounting module as well as VIEW_TRUST", () => {
    const plan = buildHomePlan(OWNER, ["disbursements"]);
    expect(plan.trustPosition).toBe(false);
    expect(plan.rows[1]).toEqual(["debtors"]);
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
    expect(plan.rows).toEqual([["unbilledWip"], ["diary"], ["activity"]]);

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

describe("withoutAbsent", () => {
  it("drops absent widgets and the rows they leave empty", () => {
    const rows = buildHomePlan(OWNER, ALL_MODULES).rows;
    expect(withoutAbsent(rows, new Set(["debtors", "trustPosition"]))).toEqual([
      ["diary", "unbilledWip"],
      ["ficaOutstanding", "matterHealth", "activity", "sensitive"],
    ]);
  });
});

describe("absentWidgets", () => {
  it("collects every absent WidgetResult by widget name; ok, error and non-results stay", () => {
    expect(
      absentWidgets({
        diary: [],
        tasks: { state: "absent" },
        unbilledWip: { state: "ok", data: {} },
        debtors: { state: "error" },
        trustPosition: { state: "absent" },
        activity: null,
        matterHealth: null,
      })
    ).toEqual(new Set(["tasks", "trustPosition"]));
  });
});

describe("rowSpanClass", () => {
  it("maps present-widget count to literal 12-column spans", () => {
    expect(rowSpanClass(1)).toBe("lg:col-span-12");
    expect(rowSpanClass(2)).toBe("lg:col-span-6");
    expect(rowSpanClass(3)).toBe("lg:col-span-4");
    expect(rowSpanClass(4)).toBe("lg:col-span-6"); // 4-up wraps 2×2, never quarter cells
  });
});
