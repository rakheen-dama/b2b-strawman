import { describe, it, expect } from "vitest";
import fs from "node:fs";
import path from "node:path";
import { NAV_GROUPS, UTILITY_ITEMS } from "../nav-items";
import { canAccessNavItem, type NavAccess } from "../nav-filter";

// Same resolution as components/__tests__/breadcrumbs-routes.test.ts.
const ORG_ROUTES_DIR = path.resolve(__dirname, "../../app/(app)/org/[slug]");
const ALL_ITEMS = NAV_GROUPS.flatMap((g) => g.items);
const zone = (id: string) => NAV_GROUPS.find((g) => g.id === id)!;
const item = (label: string) => ALL_ITEMS.find((i) => i.label === label);

// Phase 87 S6a/S6b (612A/612B/613D, ADR-339 §12.6.1): 28 items = 3 + 5 + 3 + 6 + 7 + 4. Today is
// Home, My work, Diary (613D replaced the interim Calendar + Court calendar pair); Fees has 6
// (612B added "Unbilled work").
const ZONE_SIZES: Record<string, number> = {
  today: 3,
  matters: 5,
  clients: 3,
  fees: 6,
  trust: 7,
  firm: 4,
};

/** A plain (non-admin, non-owner) user holding exactly `caps`. */
function member(caps: string[]): NavAccess {
  const set = new Set(caps);
  return { hasCapability: (c) => set.has(c), isAdmin: false, isOwner: false };
}

describe("NAV_GROUPS — six zones around the attorney's week (ADR-339)", () => {
  it("has the six zones in order", () => {
    expect(NAV_GROUPS.map((g) => g.id)).toEqual([
      "today",
      "matters",
      "clients",
      "fees",
      "trust",
      "firm",
    ]);
    expect(NAV_GROUPS.map((g) => g.label)).toEqual([
      "Today",
      "Matters",
      "Clients",
      "Fees",
      "Trust",
      "Firm",
    ]);
  });

  it("has 28 items with the expected per-zone sizes", () => {
    expect(Object.fromEntries(NAV_GROUPS.map((g) => [g.id, g.items.length]))).toEqual(ZONE_SIZES);
    expect(ALL_ITEMS).toHaveLength(28);
  });

  it("keeps every zone at ≤ 6 items except trust (≤ 7)", () => {
    for (const g of NAV_GROUPS) {
      expect(g.items.length, g.id).toBeLessThanOrEqual(g.id === "trust" ? 7 : 6);
    }
  });

  it("expands Today, Matters and Clients; collapses Fees, Trust and Firm", () => {
    expect(NAV_GROUPS.filter((g) => g.defaultExpanded).map((g) => g.id)).toEqual([
      "today",
      "matters",
      "clients",
    ]);
  });

  it("is one level deep — no item has children", () => {
    for (const i of ALL_ITEMS) expect(i, i.label).not.toHaveProperty("children");
  });

  it("every href resolves to a page.tsx under app/(app)/org/[slug]", () => {
    for (const i of [...ALL_ITEMS, ...UTILITY_ITEMS]) {
      const href = i.href("__slug__");
      expect(href.startsWith("/org/__slug__/"), i.label).toBe(true);
      const rel = href.slice("/org/__slug__/".length).split(/[?#]/)[0];
      expect(
        fs.existsSync(path.join(ORG_ROUTES_DIR, rel, "page.tsx")),
        `${i.label} → ${href}`
      ).toBe(true);
    }
  });

  it("labels are sentence case (one capital; acronyms AI and FICA allowed)", () => {
    const ACRONYMS = new Set(["AI", "FICA"]);
    const labels = [
      ...NAV_GROUPS.map((g) => g.label),
      ...ALL_ITEMS.map((i) => i.label),
      ...UTILITY_ITEMS.map((i) => i.label),
    ];
    for (const label of labels) {
      const [first, ...rest] = label.split(" ");
      expect(/^[A-Z]/.test(first), label).toBe(true);
      for (const w of rest) expect(w === w.toLowerCase() || ACRONYMS.has(w), label).toBe(true);
    }
  });

  it("no zone label says AI", () => {
    expect(NAV_GROUPS.filter((g) => /\bAI\b/.test(g.label))).toEqual([]);
  });

  it("labels and hrefs are unique (the palette keys pages by label)", () => {
    const labels = [...ALL_ITEMS, ...UTILITY_ITEMS].map((i) => i.label);
    const hrefs = ALL_ITEMS.map((i) => i.href("acme"));
    expect(new Set(labels).size).toBe(labels.length);
    expect(new Set(hrefs).size).toBe(hrefs.length);
  });

  // Gate table — §12.6.1 with the orchestrator rulings: OI-1 (Data requests, no module gate),
  // OI-2 (New matter any-of PROJECT_MANAGEMENT | AI_EXECUTE), OI-5 (§12.6.1 gates exactly).
  it.each([
    ["Home", undefined, undefined],
    ["My work", undefined, undefined],
    ["Diary", undefined, undefined],
    ["Matters", undefined, undefined],
    ["New matter", ["PROJECT_MANAGEMENT", "AI_EXECUTE"], undefined],
    ["Conflict check", undefined, "conflict_check"],
    ["Adverse parties", undefined, "conflict_check"],
    ["AI reviews", "AI_REVIEW", undefined],
    ["Clients", "CUSTOMER_MANAGEMENT", undefined],
    ["FICA & compliance", "CUSTOMER_MANAGEMENT", undefined],
    ["Data requests", "CUSTOMER_MANAGEMENT", undefined],
    ["Unbilled work", "INVOICING", undefined],
    ["Fee notes", "INVOICING", undefined],
    ["Fee runs", "INVOICING", "bulk_billing"],
    ["Debtors", "INVOICING", undefined],
    ["Disbursements", undefined, "disbursements"],
    ["Tariffs", undefined, "lssa_tariff"],
    ["Team", undefined, undefined],
    ["Reports", "FINANCIAL_VISIBILITY", undefined],
    ["Audit trail", undefined, undefined],
    ["Settings", undefined, undefined],
  ])("%s is gated on capability %s / module %s", (label, cap, mod) => {
    expect(item(label), label).toBeDefined();
    expect(item(label)!.requiredCapability).toEqual(cap);
    expect(item(label)!.requiredModule).toBe(mod);
  });

  it("Fees: Unbilled work first, then Fee notes, Fee runs, Debtors, Disbursements, Tariffs (612B)", () => {
    expect(zone("fees").items.map((i) => i.label)).toEqual([
      "Unbilled work",
      "Fee notes",
      "Fee runs",
      "Debtors",
      "Disbursements",
      "Tariffs",
    ]);
    expect(item("Unbilled work")?.href("acme")).toBe("/org/acme/invoices/unbilled");
  });

  it("Data requests points at the POPIA data-requests list (OI-1)", () => {
    expect(item("Data requests")?.href("acme")).toBe("/org/acme/compliance/requests");
    expect(item("Information requests")).toBeUndefined();
  });

  it("Trust: seven items, all VIEW_TRUST + trust_accounting", () => {
    expect(zone("trust").items.map((i) => i.label)).toEqual([
      "Overview",
      "Transactions",
      "Client ledgers",
      "Reconciliation",
      "Interest",
      "Investments",
      "Trust reports",
    ]);
    for (const i of zone("trust").items) {
      expect(i.requiredCapability, i.label).toBe("VIEW_TRUST");
      expect(i.requiredModule, i.label).toBe("trust_accounting");
    }
  });

  it("Profitability is not a nav item — Firm › Reports is its single entry", () => {
    expect(
      ALL_ITEMS.some((i) => /profitab/i.test(i.label) || /\/profitability$/.test(i.href("x")))
    ).toBe(false);
    expect(
      zone("firm")
        .items.find((i) => i.label === "Reports")
        ?.href("acme")
    ).toBe("/org/acme/reports");
  });

  it("Firm › Audit trail and Settings point at the Firm settings family (616A)", () => {
    expect(item("Audit trail")?.href("acme")).toBe("/org/acme/settings/firm?tab=audit-trail");
    expect(item("Audit trail")?.adminOnly).toBe(true);
    expect(item("Settings")?.href("acme")).toBe("/org/acme/settings/firm");
    expect(zone("firm").items.map((i) => i.label)).toEqual([
      "Team",
      "Reports",
      "Audit trail",
      "Settings",
    ]);
    // Audit trail is the only admin-only nav item.
    expect(ALL_ITEMS.filter((i) => i.adminOnly).map((i) => i.label)).toEqual(["Audit trail"]);
  });

  it("Today is Home, My work, Diary — Diary is ungated and replaces both calendars (613D)", () => {
    expect(zone("today").items.map((i) => i.href("acme"))).toEqual([
      "/org/acme/dashboard",
      "/org/acme/my-work",
      "/org/acme/diary",
    ]);
    expect(item("Diary")?.requiredModule).toBeUndefined();
    expect(item("Diary")?.requiredCapability).toBeUndefined();
    const hrefs = ALL_ITEMS.map((i) => i.href("acme"));
    expect(hrefs.filter((h) => /\/(court-)?calendar(\/|$)/.test(h))).toEqual([]);
  });

  it("has no AI zone; AI intake = New matter, AI reviews = fifth Matters item", () => {
    expect(NAV_GROUPS.some((g) => g.id === "ai")).toBe(false);
    expect(item("New matter")?.href("acme")).toBe("/org/acme/matters/new");
    expect(zone("matters").items[4]?.label).toBe("AI reviews");
  });
});

/**
 * AIVERIFY-011 — members with AI_EXECUTE must keep an AI-intake sidebar entry.
 *
 * The default Member role holds AI_EXECUTE but not PROJECT_MANAGEMENT (tenant baseline grants).
 * Before PR #1448 members had no AI entry point at all; the AI zone is gone in Phase 87 (ADR-339),
 * so AI intake is reached through "New matter" (/matters/new, which self-gates intake on
 * AI_EXECUTE). The orchestrator ruled an any-of gate (OI-2) so this live-verified fix holds.
 */
describe("AIVERIFY-011 — New matter any-of gate (Phase 87 OI-2)", () => {
  const visibleLabels = (access: NavAccess) =>
    ALL_ITEMS.filter((i) => canAccessNavItem(i, access)).map((i) => i.label);

  it("a member with AI_EXECUTE but no PROJECT_MANAGEMENT still sees New matter", () => {
    expect(visibleLabels(member(["AI_EXECUTE"]))).toContain("New matter");
  });

  it("a member with PROJECT_MANAGEMENT only sees New matter", () => {
    expect(visibleLabels(member(["PROJECT_MANAGEMENT"]))).toContain("New matter");
  });

  it("a user with neither PROJECT_MANAGEMENT nor AI_EXECUTE does not see New matter", () => {
    expect(visibleLabels(member(["CUSTOMER_MANAGEMENT", "AI_REVIEW"]))).not.toContain("New matter");
  });

  it("a member with only AI_EXECUTE does NOT see the AI_REVIEW item", () => {
    expect(visibleLabels(member(["AI_EXECUTE"]))).not.toContain("AI reviews");
  });
});

describe("UTILITY_ITEMS", () => {
  it("is Notifications only — Settings lives in the Firm zone (ADR-339)", () => {
    expect(UTILITY_ITEMS.map((i) => i.label)).toEqual(["Notifications"]);
    expect(UTILITY_ITEMS[0].href("my-org")).toBe("/org/my-org/notifications");
  });
});

describe("matter/client hrefs (Phase 87 610E)", () => {
  it("use the renamed route segments", () => {
    const hrefs = NAV_GROUPS.flatMap((g) => g.items).map((i) => i.href("acme"));
    expect(hrefs).toEqual(
      expect.arrayContaining(["/org/acme/matters", "/org/acme/clients", "/org/acme/matters/new"])
    );
    expect(hrefs.filter((h) => /\/(projects|customers)(\/|$)/.test(h))).toEqual([]);
  });
});

describe("legal labels (Phase 87 611B)", () => {
  it("labels the matters zone Matters", () => {
    expect(zone("matters").label).toBe("Matters");
  });

  it("labels the matter, client and fee-note items in legal copy", () => {
    const labels = NAV_GROUPS.flatMap((g) => g.items).map((i) => i.label);
    expect(labels).toEqual(expect.arrayContaining(["Matters", "Clients", "Fee notes"]));
  });

  it("has no generic Projects/Customers/Invoices zone or item label", () => {
    const labels = [
      ...NAV_GROUPS.map((g) => g.label),
      ...NAV_GROUPS.flatMap((g) => g.items).map((i) => i.label),
    ];
    expect(labels.filter((l) => /^(Projects|Customers|Invoices)$/.test(l))).toEqual([]);
  });
});
