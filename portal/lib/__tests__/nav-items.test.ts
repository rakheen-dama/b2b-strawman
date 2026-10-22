import { describe, it, expect } from "vitest";
import {
  PORTAL_NAV_ITEMS,
  PORTAL_NAV_LABELS,
  filterNavItems,
} from "@/lib/nav-items";

describe("PORTAL_NAV_LABELS", () => {
  // Phase 87 S10 (617A, arch §12.8): the legal-native portal labels.
  it("carries the Phase 87 legal labels", () => {
    expect(PORTAL_NAV_LABELS).toEqual({
      "portal.nav.home": "Home",
      "portal.nav.matters": "Matters",
      "portal.nav.trust": "Trust statement",
      "portal.nav.deadlines": "Diary dates",
      "portal.nav.invoices": "Fee notes",
      "portal.nav.requests": "Documents & requests",
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
  // GAP-P-07: "documents" entry removed — the /documents route is not implemented.
  // Phase 87 617A: "acceptance" dropped (no /acceptance page); "projects" → "matters".
  it("includes the 7 canonical entries in rail order", () => {
    expect(PORTAL_NAV_ITEMS).toHaveLength(7);
    expect(PORTAL_NAV_ITEMS.map((i) => i.id)).toEqual([
      "home",
      "matters",
      "trust",
      "deadlines",
      "invoices",
      "requests",
      "activity",
    ]);
  });

  it("routes Matters to /matters and keeps the other paths", () => {
    expect(
      Object.fromEntries(PORTAL_NAV_ITEMS.map((i) => [i.id, i.href])),
    ).toEqual({
      home: "/home",
      matters: "/matters",
      trust: "/trust",
      deadlines: "/deadlines",
      invoices: "/invoices",
      requests: "/requests",
      activity: "/activity",
    });
  });

  it("has no acceptance item and no /acceptance or /projects href", () => {
    expect(PORTAL_NAV_ITEMS.some((i) => i.id === "acceptance")).toBe(false);
    expect(PORTAL_NAV_ITEMS.some((i) => i.href === "/acceptance")).toBe(false);
    expect(PORTAL_NAV_ITEMS.some((i) => i.href.startsWith("/projects"))).toBe(
      false,
    );
  });

  it("carries no profile gate (legal-za is the only profile)", () => {
    for (const item of PORTAL_NAV_ITEMS) {
      expect(item).not.toHaveProperty("profiles");
    }
  });
});

describe("filterNavItems", () => {
  it("includes trust when trust_accounting is enabled", () => {
    const filtered = filterNavItems(PORTAL_NAV_ITEMS, {
      enabledModules: ["trust_accounting"],
    });
    expect(filtered.some((i) => i.id === "trust")).toBe(true);
  });

  it("filters out trust and deadlines when their modules are missing", () => {
    const filtered = filterNavItems(PORTAL_NAV_ITEMS, { enabledModules: [] });
    expect(filtered.some((i) => i.id === "trust")).toBe(false);
    expect(filtered.some((i) => i.id === "deadlines")).toBe(false);
    expect(filtered.some((i) => i.id === "requests")).toBe(false);
  });

  it("always includes home, matters, invoices, activity (no gates)", () => {
    const ids = filterNavItems(PORTAL_NAV_ITEMS, { enabledModules: [] }).map(
      (i) => i.id,
    );
    expect(ids).toEqual(["home", "matters", "invoices", "activity"]);
    // GAP-P-07: "documents" is no longer in the nav.
    expect(ids).not.toContain("documents");
  });

  it("shows all 7 when every module is enabled", () => {
    const filtered = filterNavItems(PORTAL_NAV_ITEMS, {
      enabledModules: [
        "trust_accounting",
        "deadlines",
        "information_requests",
        "document_acceptance",
      ],
    });
    expect(filtered).toHaveLength(7);
  });
});
