import { describe, it, expect } from "vitest";
import {
  PORTAL_NAV_ITEMS,
  PORTAL_NAV_LABELS,
  filterNavItems,
} from "@/lib/nav-items";

describe("PORTAL_NAV_LABELS", () => {
  // Phase 87 611E: rendered directly by PortalSidebar (the t() pass is gone). Today's labels —
  // "Fee Notes" is what legal-za tenants already saw via t("Invoices"). New labels are 617A.
  it("carries today's legal labels", () => {
    expect(PORTAL_NAV_LABELS).toEqual({
      "portal.nav.home": "Home",
      "portal.nav.matters": "Matters",
      "portal.nav.trust": "Trust",
      "portal.nav.deadlines": "Deadlines",
      "portal.nav.invoices": "Fee Notes",
      "portal.nav.requests": "Requests",
      "portal.nav.acceptance": "Acceptance",
      "portal.nav.activity": "Activity",
    });
  });

  it("has a label for every nav item", () => {
    for (const item of PORTAL_NAV_ITEMS) {
      expect(PORTAL_NAV_LABELS[item.labelKey]).toBeTruthy();
    }
  });
});

describe("PORTAL_NAV_ITEMS", () => {
  // GAP-P-07: "documents" entry removed — the /documents route is not
  // implemented. Restore once the page exists (ideally behind a module).
  // GAP-OBS-Portal-Activity (E4.3): "activity" entry added after "acceptance".
  it("includes all 8 canonical entries", () => {
    expect(PORTAL_NAV_ITEMS).toHaveLength(8);
    expect(PORTAL_NAV_ITEMS.map((i) => i.id)).toEqual([
      "home",
      "projects",
      "trust",
      "deadlines",
      "invoices",
      "requests",
      "acceptance",
      "activity",
    ]);
  });
});

describe("filterNavItems", () => {
  it("includes the trust entry for legal-za with trust_accounting module", () => {
    const filtered = filterNavItems(PORTAL_NAV_ITEMS, {
      tenantProfile: "legal-za",
      enabledModules: ["trust_accounting"],
    });
    expect(filtered.some((i) => i.id === "trust")).toBe(true);
  });

  // Phase 86: accounting-za is gone; any non-legal id still exercises the profile filter.
  it("filters out the trust entry for a non-legal profile (profile mismatch)", () => {
    const filtered = filterNavItems(PORTAL_NAV_ITEMS, {
      tenantProfile: "unknown-profile",
      enabledModules: ["trust_accounting"],
    });
    expect(filtered.some((i) => i.id === "trust")).toBe(false);
  });

  it("filters out the trust entry when module is missing", () => {
    const filtered = filterNavItems(PORTAL_NAV_ITEMS, {
      tenantProfile: "legal-za",
      enabledModules: [],
    });
    expect(filtered.some((i) => i.id === "trust")).toBe(false);
  });

  it("includes invoices even with no modules enabled (no module gate)", () => {
    const filtered = filterNavItems(PORTAL_NAV_ITEMS, {
      tenantProfile: "legal-za",
      enabledModules: [],
    });
    expect(filtered.some((i) => i.id === "invoices")).toBe(true);
  });

  it("always includes home, projects, invoices (no gates)", () => {
    const filtered = filterNavItems(PORTAL_NAV_ITEMS, {
      enabledModules: [],
    });
    const ids = filtered.map((i) => i.id);
    expect(ids).toContain("home");
    expect(ids).toContain("projects");
    expect(ids).toContain("invoices");
    // GAP-P-07: "documents" is no longer in the nav; no assertion needed.
    expect(ids).not.toContain("documents");
  });

  it("excludes profile-gated items when tenantProfile is undefined", () => {
    const filtered = filterNavItems(PORTAL_NAV_ITEMS, {
      enabledModules: ["trust_accounting", "deadlines"],
    });
    expect(filtered.some((i) => i.id === "trust")).toBe(false);
    expect(filtered.some((i) => i.id === "deadlines")).toBe(false);
  });
});
