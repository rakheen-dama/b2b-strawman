import { describe, it, expect } from "vitest";
import {
  NAV_ZONES,
  getActiveZone,
  isSubNavActive,
} from "@/lib/navigation";

const SLUG = "acme-corp";

describe("NAV_ZONES", () => {
  it("has 7 zones", () => {
    expect(NAV_ZONES).toHaveLength(7);
  });

  it("each zone has a unique id", () => {
    const ids = NAV_ZONES.map((z) => z.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("admin zone has empty subNav", () => {
    const admin = NAV_ZONES.find((z) => z.id === "admin");
    expect(admin?.subNav).toHaveLength(0);
  });
});

describe("getActiveZone", () => {
  it("returns home zone for /dashboard", () => {
    const zone = getActiveZone(`/org/${SLUG}/dashboard`, SLUG);
    expect(zone?.id).toBe("home");
  });

  it("returns home zone for /my-work", () => {
    const zone = getActiveZone(`/org/${SLUG}/my-work`, SLUG);
    expect(zone?.id).toBe("home");
  });

  it("returns work zone for /projects", () => {
    const zone = getActiveZone(`/org/${SLUG}/projects`, SLUG);
    expect(zone?.id).toBe("work");
  });

  it("returns work zone for /projects/123", () => {
    const zone = getActiveZone(`/org/${SLUG}/projects/123`, SLUG);
    expect(zone?.id).toBe("work");
  });

  it("returns work zone for /schedules", () => {
    const zone = getActiveZone(`/org/${SLUG}/schedules`, SLUG);
    expect(zone?.id).toBe("work");
  });

  it("returns clients zone for /customers", () => {
    const zone = getActiveZone(`/org/${SLUG}/customers`, SLUG);
    expect(zone?.id).toBe("clients");
  });

  it("returns clients zone for /customers/42", () => {
    const zone = getActiveZone(`/org/${SLUG}/customers/42`, SLUG);
    expect(zone?.id).toBe("clients");
  });

  it("returns money zone for /invoices", () => {
    const zone = getActiveZone(`/org/${SLUG}/invoices`, SLUG);
    expect(zone?.id).toBe("money");
  });

  it("returns money zone for /retainers", () => {
    const zone = getActiveZone(`/org/${SLUG}/retainers`, SLUG);
    expect(zone?.id).toBe("money");
  });

  it("returns docs zone for /documents", () => {
    const zone = getActiveZone(`/org/${SLUG}/documents`, SLUG);
    expect(zone?.id).toBe("docs");
  });

  it("returns reports zone for /profitability", () => {
    const zone = getActiveZone(`/org/${SLUG}/profitability`, SLUG);
    expect(zone?.id).toBe("reports");
  });

  it("returns reports zone for /reports", () => {
    const zone = getActiveZone(`/org/${SLUG}/reports`, SLUG);
    expect(zone?.id).toBe("reports");
  });

  it("returns admin zone for /settings", () => {
    const zone = getActiveZone(`/org/${SLUG}/settings`, SLUG);
    expect(zone?.id).toBe("admin");
  });

  it("returns admin zone for /team", () => {
    const zone = getActiveZone(`/org/${SLUG}/team`, SLUG);
    expect(zone?.id).toBe("admin");
  });

  it("returns admin zone for /compliance", () => {
    const zone = getActiveZone(`/org/${SLUG}/compliance`, SLUG);
    expect(zone?.id).toBe("admin");
  });

  it("returns admin zone for /notifications", () => {
    const zone = getActiveZone(`/org/${SLUG}/notifications`, SLUG);
    expect(zone?.id).toBe("admin");
  });

  it("returns undefined for unrecognized path", () => {
    const zone = getActiveZone(`/org/${SLUG}/unknown`, SLUG);
    expect(zone).toBeUndefined();
  });

  it("returns undefined for root org path", () => {
    const zone = getActiveZone(`/org/${SLUG}`, SLUG);
    expect(zone).toBeUndefined();
  });
});

describe("isSubNavActive", () => {
  it("matches exact path when exact is true", () => {
    const dashboardItem = NAV_ZONES[0].subNav[0]; // Dashboard, exact: true
    expect(
      isSubNavActive(dashboardItem, `/org/${SLUG}/dashboard`, SLUG),
    ).toBe(true);
  });

  it("does not match sub-path when exact is true", () => {
    const dashboardItem = NAV_ZONES[0].subNav[0]; // Dashboard, exact: true
    expect(
      isSubNavActive(dashboardItem, `/org/${SLUG}/dashboard/extra`, SLUG),
    ).toBe(false);
  });

  it("matches prefix path when exact is not set", () => {
    const projectsItem = NAV_ZONES[1].subNav[0]; // Projects, no exact
    expect(
      isSubNavActive(projectsItem, `/org/${SLUG}/projects/123`, SLUG),
    ).toBe(true);
  });

  it("matches exact path when exact is not set", () => {
    const projectsItem = NAV_ZONES[1].subNav[0]; // Projects, no exact
    expect(
      isSubNavActive(projectsItem, `/org/${SLUG}/projects`, SLUG),
    ).toBe(true);
  });

  it("does not match unrelated path", () => {
    const projectsItem = NAV_ZONES[1].subNav[0]; // Projects
    expect(
      isSubNavActive(projectsItem, `/org/${SLUG}/customers`, SLUG),
    ).toBe(false);
  });
});
