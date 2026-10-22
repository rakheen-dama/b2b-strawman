import fs from "node:fs";
import path from "node:path";
import { describe, it, expect } from "vitest";
import { LEGACY_REDIRECTS } from "@/lib/legacy-redirects";
import {
  SETTINGS_FAMILIES,
  getSettingsFamily,
  resolveSettingsTab,
  settingsAccessFrom,
  settingsHref,
  type SettingsTab,
} from "../settings-nav";

type Flags = Pick<SettingsTab, "adminOnly" | "requiredModule" | "requiredCapability">;

// Every tab's gate — 616E replaces 616A's frozen copy of the old settings-rail groups, deleted with
// the old rail and palette lists (this file is now the only pin). Orchestrator rulings are named
// where a gate differs from the old settings nav.
const TAB_GATES: Record<string, Flags> = {
  "firm/general": {},
  "firm/roles": { adminOnly: true },
  // D5: the backend gate is @RequiresCapability("TEAM_OVERSIGHT") (AuditEventController) and the
  // old page had no frontend role check; a member holding it sees the Home "Sensitive events"
  // widget (lib/dashboard-home.ts) whose links land here — adminOnly would have regressed them.
  "firm/audit-trail": { requiredCapability: "TEAM_OVERSIGHT" },
  "firm/data-protection": { adminOnly: true },
  "firm/data-requests": { adminOnly: true }, // D7: former page gate (isAdmin || isOwner)
  // D6: the old page denied non-admins; a member-visible tab whose only content is a denial is
  // the "disabled tab" §12.7.4 forbids (§12.11.4 also lists it admin-only).
  "firm/compliance": { adminOnly: true },
  "firm/features": { adminOnly: true },
  "firm/subscription": {}, // D6: GET /api/billing/subscription is ungated (ADR-335)
  // D18 (616B, D6 rule): the former naming, custom-fields, checklists, request-templates and
  // request-settings pages denied non-admins. Templates and Tags stay ungated (their former pages
  // rendered read-only for members).
  "matters/templates": {},
  "matters/naming": { adminOnly: true },
  "matters/custom-fields": { adminOnly: true },
  "matters/tags": {},
  "matters/checklists": { adminOnly: true },
  "matters/request-templates": { adminOnly: true },
  "matters/request-settings": { adminOnly: true },
  "documents/templates": {},
  "documents/clauses": {},
  "documents/acceptance": { adminOnly: true }, // D19 (616C, D6/D18 rule)
  "documents/email": { adminOnly: true },
  // D20 (616C): Tariffs is the Fees family's first tab and its former page let FINANCIAL_VISIBILITY
  // holders in, so it stays ungated and its loader keeps the in-page capability denial.
  "fees/tariffs": {},
  "fees/tax": { adminOnly: true }, // D19
  "fees/time-recording": { adminOnly: true }, // D19
  "fees/fee-runs": { adminOnly: true, requiredModule: "bulk_billing" },
  "fees/collections": { adminOnly: true },
  "fees/trust-accounting": { adminOnly: true, requiredModule: "trust_accounting" },
  "connections/xero": {},
  "connections/mcp": { requiredCapability: "INTEGRATION_MANAGE" }, // D7: integrations/mcp page gate
  "connections/ai": { adminOnly: true }, // D24 (616D)
  "connections/notifications": {},
  "connections/packs": { adminOnly: true },
  "connections/automations": { adminOnly: true }, // D24 (616D)
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

  it("pins every tab's gate — 31 rows (rulings D5–D7, D18–D20, D24)", () => {
    const actual = Object.fromEntries(
      SETTINGS_FAMILIES.flatMap((f) => f.tabs.map((t) => [tabKey(f.id, t.id), normalised(t)]))
    );
    const expected = Object.fromEntries(
      Object.entries(TAB_GATES).map(([key, flags]) => [key, normalised(flags)])
    );
    expect(Object.keys(TAB_GATES)).toHaveLength(31);
    expect(actual).toEqual(expected);
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

  it("has a page.tsx for every family (616D removed the pending flag)", () => {
    const settingsDir = path.resolve(__dirname, "../../app/(app)/org/[slug]/settings");
    for (const f of SETTINGS_FAMILIES) {
      expect(fs.existsSync(path.join(settingsDir, f.id, "page.tsx")), f.id).toBe(true);
      expect(f).not.toHaveProperty("pending");
    }
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

describe("Connections tab gating (D7/D24)", () => {
  const connections = getSettingsFamily("connections");

  it("falls a member back to Xero for the gated Connections tabs", () => {
    for (const tabId of ["mcp", "ai", "packs", "automations"]) {
      const { tab, forbidden } = resolveSettingsTab(connections, tabId, MEMBER);
      expect(tab.id, tabId).toBe("xero");
      expect(forbidden?.id, tabId).toBe(tabId);
    }
  });

  it("keeps Xero (the Connections first tab) and Notifications open to members", () => {
    expect(resolveSettingsTab(connections, "xero", MEMBER).forbidden).toBeNull();
    expect(resolveSettingsTab(connections, "notifications", MEMBER)).toEqual({
      tab: findTab("connections", "notifications"),
      forbidden: null,
    });
  });

  it("opens MCP to an INTEGRATION_MANAGE holder and every tab to an owner", () => {
    const integrationManager = settingsAccessFrom(
      { capabilities: ["INTEGRATION_MANAGE"], isAdmin: false, isOwner: false },
      []
    );
    expect(resolveSettingsTab(connections, "mcp", integrationManager)).toEqual({
      tab: findTab("connections", "mcp"),
      forbidden: null,
    });
    for (const t of connections.tabs)
      expect(resolveSettingsTab(connections, t.id, OWNER).tab.id).toBe(t.id);
  });
});

describe("settings redirect rows ↔ SETTINGS_FAMILIES (616E, §12.7.3)", () => {
  const SETTINGS = "/org/:slug/settings/";
  const settingsDir = path.resolve(__dirname, "../../app/(app)/org/[slug]/settings");
  // Scoped to settings rows (sources under /org/:slug/settings/) so the 610D/612A/613D/615E rows
  // cannot fail it.
  const settingsRows = LEGACY_REDIRECTS.filter((r) => r.source.startsWith(SETTINGS));

  it("resolves every settings row to a declared family tab or a kept route under that tab", () => {
    const rows = settingsRows.filter((r) => r.destination.startsWith(SETTINGS));
    expect(rows).toHaveLength(43);
    for (const r of rows) {
      const [target, query] = r.destination.slice(SETTINGS.length).split("?");
      const [familyId, ...rest] = target.split("/");
      const family = SETTINGS_FAMILIES.find((f) => f.id === familyId);
      expect(family, r.source).toBeDefined();
      const tabIds = family!.tabs.map((t) => t.id);
      if (query !== undefined) {
        expect(rest, r.source).toEqual([]);
        expect(tabIds, r.source).toContain(new URLSearchParams(query).get("tab"));
      } else {
        // A kept editor/log route: lives under the tab its first segment names (the settings rail
        // relies on this to mark the tab current on a sub-route) and has a page.tsx.
        expect(tabIds, r.source).toContain(rest[0]);
        const dir = target.replace(/:([A-Za-z]+)/g, "[$1]");
        expect(fs.existsSync(path.join(settingsDir, dir, "page.tsx")), r.source).toBe(true);
      }
    }
  });

  it("gives every declared tab at least one redirect row", () => {
    const tabTargets = new Set(
      settingsRows
        .filter((r) => r.destination.includes("?tab="))
        .map((r) => {
          const [target, query] = r.destination.slice(SETTINGS.length).split("?");
          return tabKey(target, new URLSearchParams(query).get("tab")!);
        })
    );
    expect([...tabTargets].sort()).toEqual(
      SETTINGS_FAMILIES.flatMap((f) => f.tabs.map((t) => tabKey(f.id, t.id))).sort()
    );
  });

  it("leaves settings only through the pre-Phase-87 /settings/team row", () => {
    expect(
      settingsRows
        .filter((r) => !r.destination.startsWith(SETTINGS))
        .map((r) => [r.source, r.destination])
    ).toEqual([["/org/:slug/settings/team", "/org/:slug/team"]]);
  });
});
