import { describe, it, expect } from "vitest";
import { SETTINGS_NAV_GROUPS } from "@/components/settings/settings-nav-groups";
import { LEGACY_REDIRECTS } from "@/lib/legacy-redirects";
import {
  SETTINGS_FAMILIES,
  getSettingsFamily,
  resolveSettingsTab,
  settingsAccessFrom,
  settingsHref,
  type SettingsTab,
} from "../settings-nav";

// Frozen copy of SETTINGS_NAV_GROUPS (components/settings/settings-nav-groups.ts at b2161bfc2) —
// the flag source for ADR-341's "flags reproduce one-to-one". 616E deletes settings-nav-groups.ts
// together with the `matches the live groups` assertion and this frozen copy (616A.1).
const FROZEN_NAV_ITEMS: { href: string; adminOnly?: boolean; requiredModule?: string }[] = [
  { href: "general" },
  { href: "billing" },
  { href: "notifications" },
  { href: "email", adminOnly: true },
  // { href: "", comingSoon: true } — "Security": dropped, no page
  { href: "time-tracking" },
  { href: "project-templates" },
  { href: "project-naming" },
  { href: "automations", adminOnly: true },
  { href: "templates" },
  { href: "clauses" },
  { href: "checklists" },
  { href: "acceptance" },
  { href: "rates" },
  { href: "tax" },
  { href: "batch-billing", adminOnly: true, requiredModule: "bulk_billing" },
  { href: "collections", adminOnly: true },
  { href: "trust-accounting", adminOnly: true, requiredModule: "trust_accounting" },
  { href: "custom-fields" },
  { href: "tags" },
  { href: "request-templates" },
  { href: "request-settings" },
  { href: "compliance" },
  { href: "data-protection", adminOnly: true },
  { href: "features", adminOnly: true },
  { href: "packs", adminOnly: true },
  { href: "roles", adminOnly: true },
  { href: "audit-log", adminOnly: true },
  { href: "integrations" },
  { href: "ai", adminOnly: true },
];

// Old settings page (SETTINGS_NAV_GROUPS href) → [family, tab id] — §12.7.1 / §12.7.2.
const OLD_TO_TAB: Record<string, [string, string]> = {
  general: ["firm", "general"],
  billing: ["firm", "subscription"],
  roles: ["firm", "roles"],
  "audit-log": ["firm", "audit-trail"],
  "data-protection": ["firm", "data-protection"],
  compliance: ["firm", "compliance"],
  features: ["firm", "features"],
  "project-templates": ["matters", "templates"],
  "project-naming": ["matters", "naming"],
  "custom-fields": ["matters", "custom-fields"],
  tags: ["matters", "tags"],
  checklists: ["matters", "checklists"],
  "request-templates": ["matters", "request-templates"],
  "request-settings": ["matters", "request-settings"],
  templates: ["documents", "templates"],
  clauses: ["documents", "clauses"],
  acceptance: ["documents", "acceptance"],
  email: ["documents", "email"],
  rates: ["fees", "tariffs"],
  tax: ["fees", "tax"],
  "time-tracking": ["fees", "time-recording"],
  "batch-billing": ["fees", "fee-runs"],
  collections: ["fees", "collections"],
  "trust-accounting": ["fees", "trust-accounting"],
  integrations: ["connections", "xero"],
  ai: ["connections", "ai"],
  notifications: ["connections", "notifications"],
  packs: ["connections", "packs"],
  automations: ["connections", "automations"],
};

type Flags = Pick<SettingsTab, "adminOnly" | "requiredModule" | "requiredCapability">;

// Named, orchestrator-ruled exceptions to the one-to-one flags (616A rulings D5/D6) — the flags
// the tab carries instead of the frozen entry's.
const RULED_EXCEPTIONS: Record<string, Flags> = {
  // D5: the backend gate is @RequiresCapability("TEAM_OVERSIGHT") (AuditEventController) and the
  // old page had no frontend role check; a non-admin member with FINANCIAL_VISIBILITY +
  // TEAM_OVERSIGHT sees the Home "Sensitive events" widget (lib/dashboard-home.ts) whose links land
  // here — adminOnly would have regressed them.
  "firm/audit-trail": { adminOnly: false, requiredCapability: "TEAM_OVERSIGHT" },
  // D6: the old page denied non-admins; a member-visible tab whose only content is a denial is
  // the "disabled tab" §12.7.4 forbids (§12.11.4 also lists it admin-only).
  "firm/compliance": { adminOnly: true },
  // D18 (616B, D6 rule): the former pages denied non-admins; member-visible tabs holding only a
  // denial message are the "disabled tab" §12.7.4 forbids. Templates and Tags stay ungated (their
  // former pages rendered read-only for members).
  "matters/naming": { adminOnly: true },
  "matters/custom-fields": { adminOnly: true },
  "matters/checklists": { adminOnly: true },
  "matters/request-templates": { adminOnly: true },
  "matters/request-settings": { adminOnly: true },
  // D19 (616C, D6/D18 rule): the former acceptance, tax and time-tracking pages denied non-admins;
  // member-visible tabs holding only a denial message are the "disabled tab" §12.7.4 forbids.
  // Tariffs stays ungated (D20): it is the Fees family's first tab and its former page let
  // FINANCIAL_VISIBILITY holders in, so its loader keeps the in-page capability denial.
  "documents/acceptance": { adminOnly: true },
  "fees/tax": { adminOnly: true },
  "fees/time-recording": { adminOnly: true },
};

const tabKey = (familyId: string, tabId: string) => `${familyId}/${tabId}`;

function normalised(flags: Flags) {
  return {
    adminOnly: flags.adminOnly ?? false,
    requiredModule: flags.requiredModule,
    requiredCapability: flags.requiredCapability,
  };
}

function findTab(familyId: string, tabId: string): SettingsTab | undefined {
  return SETTINGS_FAMILIES.find((f) => f.id === familyId)?.tabs.find((t) => t.id === tabId);
}

const MEMBER = settingsAccessFrom({ capabilities: [], isAdmin: false, isOwner: false }, []);
const OWNER = settingsAccessFrom({ capabilities: [], isAdmin: false, isOwner: true }, []);

describe("SETTINGS_FAMILIES", () => {
  it("declares the five families in order", () => {
    expect(SETTINGS_FAMILIES.map((f) => f.id)).toEqual([
      "firm",
      "matters",
      "documents",
      "fees",
      "connections",
    ]);
    expect(SETTINGS_FAMILIES.map((f) => f.label)).toEqual([
      "Firm",
      "Matters",
      "Documents",
      "Fees & Trust",
      "Connections",
    ]);
  });

  it("declares 31 tabs, 8/7/4/6/6, with the §12.7.1 ids", () => {
    expect(SETTINGS_FAMILIES.map((f) => f.tabs.length)).toEqual([8, 7, 4, 6, 6]);
    expect(SETTINGS_FAMILIES.flatMap((f) => f.tabs)).toHaveLength(31);
    const ids = Object.fromEntries(SETTINGS_FAMILIES.map((f) => [f.id, f.tabs.map((t) => t.id)]));
    expect(ids).toEqual({
      firm: [
        "general",
        "roles",
        "audit-trail",
        "data-protection",
        "data-requests",
        "compliance",
        "features",
        "subscription",
      ],
      matters: [
        "templates",
        "naming",
        "custom-fields",
        "tags",
        "checklists",
        "request-templates",
        "request-settings",
      ],
      documents: ["templates", "clauses", "acceptance", "email"],
      fees: ["tariffs", "tax", "time-recording", "fee-runs", "collections", "trust-accounting"],
      connections: ["xero", "mcp", "ai", "notifications", "packs", "automations"],
    });
  });

  it("keeps tab ids unique within a family and kebab-case", () => {
    for (const family of SETTINGS_FAMILIES) {
      const ids = family.tabs.map((t) => t.id);
      expect(new Set(ids).size, family.id).toBe(ids.length);
      for (const id of ids) expect(id, `${family.id}/${id}`).toMatch(/^[a-z]+(-[a-z]+)*$/);
    }
  });

  it("uses sentence-case tab labels (acronyms AI and MCP allowed)", () => {
    for (const tab of SETTINGS_FAMILIES.flatMap((f) => f.tabs)) {
      const [first, ...rest] = tab.label.split(" ");
      expect(first[0], tab.label).toBe(first[0].toUpperCase());
      for (const word of rest) {
        expect(word === word.toLowerCase() || ["AI", "MCP"].includes(word), tab.label).toBe(true);
      }
    }
  });

  it("leaves every family's first tab ungated, so the ?tab= fallback always exists", () => {
    for (const family of SETTINGS_FAMILIES) {
      const [first] = family.tabs;
      expect(first.adminOnly, family.id).toBeUndefined();
      expect(first.requiredModule, family.id).toBeUndefined();
      expect(first.requiredCapability, family.id).toBeUndefined();
    }
  });

  it("keeps the frozen copy honest against the live SETTINGS_NAV_GROUPS (616E deletes both)", () => {
    const live = SETTINGS_NAV_GROUPS.flatMap((g) => g.items)
      .filter((i) => !i.comingSoon)
      .map((i) => ({
        href: i.href,
        ...(i.adminOnly ? { adminOnly: true } : {}),
        ...(i.requiredModule ? { requiredModule: i.requiredModule } : {}),
      }));
    expect(live).toEqual(FROZEN_NAV_ITEMS);
  });

  it("reproduces each frozen entry's flags on exactly one tab, except the named ruled exceptions", () => {
    const mapped = new Set<string>();
    for (const entry of FROZEN_NAV_ITEMS) {
      const target = OLD_TO_TAB[entry.href];
      expect(target, entry.href).toBeDefined();
      const [familyId, tabId] = target;
      const tab = findTab(familyId, tabId);
      expect(tab, `${entry.href} → ${familyId}/${tabId}`).toBeDefined();
      const key = tabKey(familyId, tabId);
      expect(mapped.has(key), key).toBe(false);
      mapped.add(key);
      const expected = RULED_EXCEPTIONS[key] ?? { ...entry, requiredCapability: undefined };
      expect(normalised(tab!), `${entry.href} → ${key}`).toEqual(normalised(expected));
    }
    expect(mapped.size).toBe(29);
    const unmapped = SETTINGS_FAMILIES.flatMap((f) => f.tabs.map((t) => tabKey(f.id, t.id))).filter(
      (k) => !mapped.has(k)
    );
    expect(unmapped).toEqual(["firm/data-requests", "connections/mcp"]);
    // Every ruled exception names a real, mapped tab.
    for (const key of Object.keys(RULED_EXCEPTIONS)) expect(mapped.has(key), key).toBe(true);
  });

  it("gates the two tabs without an old nav entry like their former pages (D7)", () => {
    expect(normalised(findTab("firm", "data-requests")!)).toEqual(normalised({ adminOnly: true }));
    expect(normalised(findTab("connections", "mcp")!)).toEqual(
      normalised({ requiredCapability: "INTEGRATION_MANAGE" })
    );
  });

  it("does not declare Security (comingSoon, no page)", () => {
    for (const tab of SETTINGS_FAMILIES.flatMap((f) => f.tabs)) {
      expect(tab.label).not.toMatch(/security/i);
      for (const kw of tab.keywords ?? []) expect(kw).not.toMatch(/security/i);
    }
  });

  it("marks every family without a page yet pending (Firm 616A, Matters 616B, Documents and Fees & Trust 616C have pages)", () => {
    expect(SETTINGS_FAMILIES.filter((f) => f.pending).map((f) => f.id)).toEqual(["connections"]);
  });
});

describe("settingsHref / family href", () => {
  it("builds family and tab URLs", () => {
    expect(getSettingsFamily("fees").href("acme")).toBe("/org/acme/settings/fees");
    expect(settingsHref("acme", "firm", "data-requests")).toBe(
      "/org/acme/settings/firm?tab=data-requests"
    );
    expect(settingsHref("acme", "firm")).toBe("/org/acme/settings/firm");
  });
});

describe("resolveSettingsTab", () => {
  const firm = getSettingsFamily("firm");

  it("falls back to the first permitted tab for a missing or unknown id, silently", () => {
    expect(resolveSettingsTab(firm, undefined, MEMBER)).toMatchObject({
      tab: { id: "general" },
      forbidden: null,
    });
    expect(resolveSettingsTab(firm, "nope", OWNER)).toMatchObject({
      tab: { id: "general" },
      forbidden: null,
    });
  });

  it("falls back and reports a known-but-forbidden tab", () => {
    const { tab, forbidden } = resolveSettingsTab(firm, "roles", MEMBER);
    expect(tab.id).toBe("general");
    expect(forbidden?.id).toBe("roles");
  });

  it("returns a permitted tab", () => {
    expect(resolveSettingsTab(firm, "roles", OWNER)).toMatchObject({
      tab: { id: "roles" },
      forbidden: null,
    });
  });

  it("applies capability gates (admins and owners hold every capability)", () => {
    const overseer = settingsAccessFrom(
      { capabilities: ["TEAM_OVERSIGHT"], isAdmin: false, isOwner: false },
      []
    );
    expect(resolveSettingsTab(firm, "audit-trail", overseer).tab.id).toBe("audit-trail");
    expect(resolveSettingsTab(firm, "audit-trail", MEMBER).forbidden?.id).toBe("audit-trail");
    expect(resolveSettingsTab(firm, "audit-trail", OWNER).tab.id).toBe("audit-trail");
  });

  it("applies module gates", () => {
    const fees = getSettingsFamily("fees");
    const noModule = resolveSettingsTab(fees, "fee-runs", OWNER);
    expect(noModule.tab.id).toBe("tariffs");
    expect(noModule.forbidden?.id).toBe("fee-runs");
    const withModule = settingsAccessFrom({ capabilities: [], isAdmin: true, isOwner: false }, [
      "bulk_billing",
    ]);
    expect(resolveSettingsTab(fees, "fee-runs", withModule).tab.id).toBe("fee-runs");
  });
});

describe("Firm redirect rows", () => {
  it("land every /settings/firm?tab= destination on a declared Firm tab (precursor of 616E)", () => {
    const firmIds = getSettingsFamily("firm").tabs.map((t) => t.id);
    const tabs = LEGACY_REDIRECTS.filter((r) =>
      r.destination.startsWith("/org/:slug/settings/firm?tab=")
    ).map((r) => new URLSearchParams(r.destination.split("?")[1]).get("tab"));
    expect(tabs).toHaveLength(8);
    for (const tab of tabs) expect(firmIds).toContain(tab);
  });
});

describe("Matters redirect rows", () => {
  it("land every /settings/matters?tab= destination on a declared Matters tab (precursor of 616E)", () => {
    const mattersIds = getSettingsFamily("matters").tabs.map((t) => t.id);
    const tabs = LEGACY_REDIRECTS.filter((r) =>
      r.destination.startsWith("/org/:slug/settings/matters?tab=")
    ).map((r) => new URLSearchParams(r.destination.split("?")[1]).get("tab"));
    expect(tabs).toHaveLength(7);
    expect([...tabs].sort()).toEqual([...mattersIds].sort());
  });
});

describe("Matters tab gating (D18)", () => {
  it("falls a member back to Templates for an admin-only Matters tab", () => {
    const matters = getSettingsFamily("matters");
    const { tab, forbidden } = resolveSettingsTab(matters, "naming", MEMBER);
    expect(tab.id).toBe("templates");
    expect(forbidden?.id).toBe("naming");
  });

  it("lets a member open Tags and an owner open every Matters tab", () => {
    const matters = getSettingsFamily("matters");
    expect(resolveSettingsTab(matters, "tags", MEMBER)).toEqual({
      tab: findTab("matters", "tags"),
      forbidden: null,
    });
    for (const t of matters.tabs)
      expect(resolveSettingsTab(matters, t.id, OWNER).tab.id).toBe(t.id);
  });
});

describe("Documents redirect rows", () => {
  it("land every /settings/documents?tab= destination on a declared Documents tab (precursor of 616E)", () => {
    const ids = getSettingsFamily("documents").tabs.map((t) => t.id);
    const tabs = LEGACY_REDIRECTS.filter((r) =>
      r.destination.startsWith("/org/:slug/settings/documents?tab=")
    ).map((r) => new URLSearchParams(r.destination.split("?")[1]).get("tab"));
    expect(tabs).toHaveLength(4);
    expect([...tabs].sort()).toEqual([...ids].sort());
  });
});

describe("Fees & Trust redirect rows", () => {
  it("land every /settings/fees?tab= destination on a declared Fees tab (precursor of 616E)", () => {
    const ids = getSettingsFamily("fees").tabs.map((t) => t.id);
    const tabs = LEGACY_REDIRECTS.filter((r) =>
      r.destination.startsWith("/org/:slug/settings/fees?tab=")
    ).map((r) => new URLSearchParams(r.destination.split("?")[1]).get("tab"));
    expect(tabs).toHaveLength(6);
    expect([...tabs].sort()).toEqual([...ids].sort());
  });
});

describe("Documents + Fees tab gating (D19/D20)", () => {
  it("falls a member back to the first tab for the admin-only Documents/Fees tabs", () => {
    for (const [familyId, tabId, first] of [
      ["documents", "acceptance", "templates"],
      ["documents", "email", "templates"],
      ["fees", "tax", "tariffs"],
      ["fees", "time-recording", "tariffs"],
      ["fees", "collections", "tariffs"],
    ] as const) {
      const { tab, forbidden } = resolveSettingsTab(getSettingsFamily(familyId), tabId, MEMBER);
      expect(tab.id, `${familyId}/${tabId}`).toBe(first);
      expect(forbidden?.id, `${familyId}/${tabId}`).toBe(tabId);
    }
  });

  it("keeps Tariffs (the Fees first tab) open to members", () => {
    expect(resolveSettingsTab(getSettingsFamily("fees"), "tariffs", MEMBER).forbidden).toBeNull();
  });
});
