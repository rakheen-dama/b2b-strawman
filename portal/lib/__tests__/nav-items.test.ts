import { describe, it, expect } from "vitest";
import { PORTAL_NAV_ITEMS, filterNavItems } from "@/lib/nav-items";

describe("PORTAL_NAV_ITEMS", () => {
  // GAP-P-07: "documents" entry removed — the /documents route is not
  // implemented. Restore once the page exists (ideally behind a module).
  it("includes all 9 canonical entries", () => {
    expect(PORTAL_NAV_ITEMS).toHaveLength(9);
    expect(PORTAL_NAV_ITEMS.map((i) => i.id)).toEqual([
      "home",
      "projects",
      "trust",
      "retainer",
      "deadlines",
      "invoices",
      "proposals",
      "requests",
      "acceptance",
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

  it("filters out the trust entry for accounting-za (profile mismatch)", () => {
    const filtered = filterNavItems(PORTAL_NAV_ITEMS, {
      tenantProfile: "accounting-za",
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

  it("always includes home, projects, invoices, proposals (no gates)", () => {
    const filtered = filterNavItems(PORTAL_NAV_ITEMS, {
      enabledModules: [],
    });
    const ids = filtered.map((i) => i.id);
    expect(ids).toContain("home");
    expect(ids).toContain("projects");
    expect(ids).toContain("invoices");
    expect(ids).toContain("proposals");
    // GAP-P-07: "documents" is no longer in the nav; no assertion needed.
    expect(ids).not.toContain("documents");
  });

  it("includes retainer for consulting-za when module is enabled", () => {
    const filtered = filterNavItems(PORTAL_NAV_ITEMS, {
      tenantProfile: "consulting-za",
      enabledModules: ["retainer_agreements"],
    });
    expect(filtered.some((i) => i.id === "retainer")).toBe(true);
  });

  it("excludes profile-gated items when tenantProfile is undefined", () => {
    const filtered = filterNavItems(PORTAL_NAV_ITEMS, {
      enabledModules: [
        "trust_accounting",
        "retainer_agreements",
        "deadlines",
      ],
    });
    expect(filtered.some((i) => i.id === "trust")).toBe(false);
    expect(filtered.some((i) => i.id === "retainer")).toBe(false);
    expect(filtered.some((i) => i.id === "deadlines")).toBe(false);
  });
});
