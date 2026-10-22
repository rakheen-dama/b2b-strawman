import { describe, it, expect } from "vitest";
import fs from "node:fs";
import path from "node:path";
import nextConfig from "@/next.config";
import { LEGACY_REDIRECTS } from "@/lib/legacy-redirects";

// Widened row view: rows differ in shape (only some carry `has`).
type Row = {
  source: string;
  destination: string;
  permanent: boolean;
  has?: readonly unknown[];
};
const ROWS: readonly Row[] = LEGACY_REDIRECTS;

// Concrete sample values for the path params the table uses.
const SAMPLE_PARAMS: Record<string, string> = { slug: "x", id: "1", path: "a/b" };

/** Next-style source pattern → anchored regex (`:p` one segment, `/:p*` zero or more). */
function sourceRegex(source: string): RegExp {
  const body = source
    .split(/(\/:[A-Za-z]+\*?)/)
    .map((part) => {
      const m = /^\/:[A-Za-z]+(\*?)$/.exec(part);
      if (!m) return part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
      return m[1] ? "(?:/.+)?" : "/[^/]+";
    })
    .join("");
  return new RegExp(`^${body}$`);
}

/** Substitute sample params and drop the query string → the concrete pathname. */
function concretePath(pattern: string): string {
  return pattern.split("?")[0].replace(/:([A-Za-z]+)\*?/g, (_, name: string) => {
    const value = SAMPLE_PARAMS[name];
    if (value === undefined) throw new Error(`no sample value for :${name}`);
    return value;
  });
}

/** First row (in table order) Next would apply to a query-less pathname. */
function firstMatch(pathname: string): Row | undefined {
  return ROWS.find((r) => !r.has && sourceRegex(r.source).test(pathname));
}

// Phase 87 §12.10.5: one committed redirect table, append-only across 610D/612A/613D/615E/616.
describe("LEGACY_REDIRECTS", () => {
  it("has unique rows (source + has condition)", () => {
    const keys = ROWS.map((r) => JSON.stringify([r.source, r.has ?? null]));
    expect(new Set(keys).size).toBe(keys.length);
  });

  it("puts every `has` row before the plain row for the same source", () => {
    // Next applies the first matching row; a plain row listed first would shadow its `has` twin.
    ROWS.forEach((r, i) => {
      if (!r.has) return;
      const plainIndex = ROWS.findIndex((o) => o.source === r.source && !o.has);
      if (plainIndex !== -1) expect(i, r.source).toBeLessThan(plainIndex);
    });
  });

  it("never redirects a row back onto its own source", () => {
    for (const r of ROWS) {
      expect(r.destination, r.source).not.toBe(r.source);
      expect(sourceRegex(r.source).test(concretePath(r.destination)), r.source).toBe(false);
    }
  });

  it("resolves every chain (A → B → C allowed) without a cycle", () => {
    for (const r of ROWS) {
      const seen = new Set<string>();
      let path = concretePath(r.source);
      let row = firstMatch(path);
      while (row) {
        expect(seen.has(path), `redirect cycle via ${path}`).toBe(false);
        seen.add(path);
        path = concretePath(row.destination);
        row = firstMatch(path);
      }
    }
  });

  it("sends every row to an org-scoped destination", () => {
    for (const r of LEGACY_REDIRECTS) {
      expect(r.destination.startsWith("/org/:slug/"), r.source).toBe(true);
    }
  });

  it("makes every row permanent (HTTP 308)", () => {
    for (const r of LEGACY_REDIRECTS) {
      expect(r.permanent, r.source).toBe(true);
    }
  });

  it("redirects the renamed matter and client route segments (610D)", () => {
    expect(LEGACY_REDIRECTS).toContainEqual({
      source: "/org/:slug/projects/:path*",
      destination: "/org/:slug/matters/:path*",
      permanent: true,
    });
    expect(LEGACY_REDIRECTS).toContainEqual({
      source: "/org/:slug/customers/:path*",
      destination: "/org/:slug/clients/:path*",
      permanent: true,
    });
  });

  it("redirects /profitability to its single entry under Reports (612A)", () => {
    expect(LEGACY_REDIRECTS).toContainEqual({
      source: "/org/:slug/profitability",
      destination: "/org/:slug/reports/profitability",
      permanent: true,
    });
  });

  it("serves the real profitability page at /reports/profitability — no page-level loop back (612A)", () => {
    const orgDir = path.resolve(__dirname, "../../app/(app)/org/[slug]");
    // The old route has no page (the config redirect owns it) ...
    expect(fs.existsSync(path.join(orgDir, "profitability", "page.tsx"))).toBe(false);
    // ... and the destination renders the page instead of redirecting back to /profitability.
    const page = fs.readFileSync(path.join(orgDir, "reports", "profitability", "page.tsx"), "utf8");
    expect(page).not.toMatch(/\bredirect\(/);
    expect(page).toContain("ProfitabilityContent");
  });

  it("redirects both calendars to the Diary, the ?tab=prescription row first (613D)", () => {
    expect(ROWS).toContainEqual({
      source: "/org/:slug/calendar",
      destination: "/org/:slug/diary",
      permanent: true,
    });
    const prescription = {
      source: "/org/:slug/court-calendar",
      has: [{ type: "query", key: "tab", value: "prescription" }],
      destination: "/org/:slug/diary?types=PRESCRIPTION&view=list",
      permanent: true,
    };
    const plain = {
      source: "/org/:slug/court-calendar",
      destination: "/org/:slug/diary?types=COURT_DATE,PRESCRIPTION",
      permanent: true,
    };
    expect(ROWS).toContainEqual(prescription);
    expect(ROWS).toContainEqual(plain);
    const at = (row: Row) =>
      ROWS.findIndex(
        (r) =>
          r.source === row.source &&
          r.destination === row.destination &&
          JSON.stringify(r.has ?? null) === JSON.stringify(row.has ?? null)
      );
    expect(at(prescription)).toBeGreaterThanOrEqual(0);
    expect(at(prescription)).toBeLessThan(at(plain));
  });

  it("leaves the calendar routes without pages — the config redirect owns them (613D)", () => {
    const orgDir = path.resolve(__dirname, "../../app/(app)/org/[slug]");
    expect(fs.existsSync(path.join(orgDir, "calendar"))).toBe(false);
    expect(fs.existsSync(path.join(orgDir, "court-calendar"))).toBe(false);
    expect(fs.existsSync(path.join(orgDir, "diary", "page.tsx"))).toBe(true);
  });

  it("redirects the deleted matter expenses page to the Disbursements tab (615E)", () => {
    expect(LEGACY_REDIRECTS).toContainEqual({
      source: "/org/:slug/matters/:id/expenses",
      destination: "/org/:slug/matters/:id?tab=disbursements",
      permanent: true,
    });
  });

  it("chains the pre-610D /projects/:id/expenses URL through the route rename (615E)", () => {
    // Hop 1: the 610D row owns /projects/** (first match in table order) → /org/x/matters/1/expenses.
    expect(firstMatch("/org/x/projects/1/expenses")?.source).toBe("/org/:slug/projects/:path*");
    // Hop 2: that destination is owned by the 615E row → the matter's Disbursements tab.
    expect(firstMatch("/org/x/matters/1/expenses")?.destination).toBe(
      "/org/:slug/matters/:id?tab=disbursements"
    );
  });

  it("leaves matters/[id]/expenses without a page — the config redirect owns it (615E)", () => {
    const orgDir = path.resolve(__dirname, "../../app/(app)/org/[slug]");
    expect(fs.existsSync(path.join(orgDir, "matters", "[id]", "expenses"))).toBe(false);
  });

  // Phase 87 S9 (616A, ADR-341): old Firm settings page → its /settings/firm tab id.
  const FIRM_ROWS: [string, string][] = [
    ["general", "general"],
    ["features", "features"],
    ["roles", "roles"],
    ["audit-log", "audit-trail"],
    ["data-protection", "data-protection"],
    ["data-protection/requests", "data-requests"],
    ["compliance", "compliance"],
    ["billing", "subscription"],
  ];

  it("redirects the eight Firm settings pages to their /settings/firm tabs (616A)", () => {
    for (const [old, tab] of FIRM_ROWS) {
      expect(LEGACY_REDIRECTS).toContainEqual({
        source: `/org/:slug/settings/${old}`,
        destination: `/org/:slug/settings/firm?tab=${tab}`,
        permanent: true,
      });
    }
  });

  it("does not let /settings/data-protection swallow /settings/data-protection/requests (616A)", () => {
    expect(firstMatch("/org/x/settings/data-protection/requests")?.destination).toBe(
      "/org/:slug/settings/firm?tab=data-requests"
    );
    expect(firstMatch("/org/x/settings/firm")).toBeUndefined();
  });

  it("leaves the eight Firm pages deleted and serves /settings/firm (616A)", () => {
    const settingsDir = path.resolve(__dirname, "../../app/(app)/org/[slug]/settings");
    for (const [old] of FIRM_ROWS) {
      expect(fs.existsSync(path.join(settingsDir, old, "page.tsx")), old).toBe(false);
    }
    expect(fs.existsSync(path.join(settingsDir, "firm", "page.tsx"))).toBe(true);
  });

  // Phase 87 S9 (616B, ADR-341): old Matters settings page → its /settings/matters tab id …
  const MATTERS_TAB_ROWS: [string, string][] = [
    ["project-templates", "templates"],
    ["project-naming", "naming"],
    ["custom-fields", "custom-fields"],
    ["tags", "tags"],
    ["checklists", "checklists"],
    ["request-templates", "request-templates"],
    ["request-settings", "request-settings"],
  ];
  // … and old editor route → its route under /settings/matters (same param names).
  const MATTERS_ROUTE_ROWS: [string, string][] = [
    ["project-templates/:id", "matters/templates/:id"],
    ["checklists/new", "matters/checklists/new"],
    ["checklists/:id", "matters/checklists/:id"],
    ["checklists/:id/edit", "matters/checklists/:id/edit"],
    ["request-templates/new", "matters/request-templates/new"],
    ["request-templates/:id", "matters/request-templates/:id"],
  ];

  it("redirects the seven Matters settings pages to their /settings/matters tabs (616B)", () => {
    for (const [old, tab] of MATTERS_TAB_ROWS) {
      expect(LEGACY_REDIRECTS).toContainEqual({
        source: `/org/:slug/settings/${old}`,
        destination: `/org/:slug/settings/matters?tab=${tab}`,
        permanent: true,
      });
    }
  });

  it("redirects the six Matters editor routes to their routes under /settings/matters (616B)", () => {
    for (const [old, next] of MATTERS_ROUTE_ROWS) {
      expect(LEGACY_REDIRECTS).toContainEqual({
        source: `/org/:slug/settings/${old}`,
        destination: `/org/:slug/settings/${next}`,
        permanent: true,
      });
    }
  });

  it("routes each old Matters URL to the right row — :id never swallows /new or /edit (616B)", () => {
    expect(firstMatch("/org/x/settings/checklists")?.destination).toBe(
      "/org/:slug/settings/matters?tab=checklists"
    );
    expect(firstMatch("/org/x/settings/checklists/new")?.destination).toBe(
      "/org/:slug/settings/matters/checklists/new"
    );
    expect(firstMatch("/org/x/settings/checklists/1")?.destination).toBe(
      "/org/:slug/settings/matters/checklists/:id"
    );
    expect(firstMatch("/org/x/settings/checklists/1/edit")?.destination).toBe(
      "/org/:slug/settings/matters/checklists/:id/edit"
    );
    expect(firstMatch("/org/x/settings/request-templates/new")?.destination).toBe(
      "/org/:slug/settings/matters/request-templates/new"
    );
    expect(firstMatch("/org/x/settings/project-templates/new")?.destination).toBe(
      "/org/:slug/settings/matters/templates/:id"
    );
    // The new family page and its editor routes are not redirect sources.
    expect(firstMatch("/org/x/settings/matters")).toBeUndefined();
    expect(firstMatch("/org/x/settings/matters/checklists/1/edit")).toBeUndefined();
  });

  it("leaves the seven Matters pages deleted and serves /settings/matters + its editors (616B)", () => {
    const settingsDir = path.resolve(__dirname, "../../app/(app)/org/[slug]/settings");
    for (const [old] of MATTERS_TAB_ROWS) {
      expect(fs.existsSync(path.join(settingsDir, old, "page.tsx")), old).toBe(false);
    }
    expect(fs.existsSync(path.join(settingsDir, "matters", "page.tsx"))).toBe(true);
    for (const [, next] of MATTERS_ROUTE_ROWS) {
      const dir = next.replace(/:id/g, "[id]");
      expect(fs.existsSync(path.join(settingsDir, dir, "page.tsx")), next).toBe(true);
    }
    for (const [old] of MATTERS_ROUTE_ROWS) {
      const dir = old.replace(/:id/g, "[id]");
      expect(fs.existsSync(path.join(settingsDir, dir, "page.tsx")), old).toBe(false);
    }
  });

  // Phase 87 S9 (616C, ADR-341): old Documents settings page → its /settings/documents tab id …
  const DOCUMENTS_TAB_ROWS: [string, string][] = [
    ["templates", "templates"],
    ["clauses", "clauses"],
    ["acceptance", "acceptance"],
    ["email", "email"],
  ];
  // … and old document-template editor route → its route under /settings/documents.
  const DOCUMENTS_ROUTE_ROWS: [string, string][] = [
    ["templates/new", "documents/templates/new"],
    ["templates/:id/edit", "documents/templates/:id/edit"],
  ];
  // Old Fees & Trust settings page → its /settings/fees tab id.
  const FEES_TAB_ROWS: [string, string][] = [
    ["rates", "tariffs"],
    ["tax", "tax"],
    ["time-tracking", "time-recording"],
    ["batch-billing", "fee-runs"],
    ["collections", "collections"],
    ["trust-accounting", "trust-accounting"],
  ];

  it("redirects the four Documents settings pages to their /settings/documents tabs (616C)", () => {
    for (const [old, tab] of DOCUMENTS_TAB_ROWS) {
      expect(LEGACY_REDIRECTS).toContainEqual({
        source: `/org/:slug/settings/${old}`,
        destination: `/org/:slug/settings/documents?tab=${tab}`,
        permanent: true,
      });
    }
  });

  it("redirects the two document-template editor routes under /settings/documents (616C)", () => {
    for (const [old, next] of DOCUMENTS_ROUTE_ROWS) {
      expect(LEGACY_REDIRECTS).toContainEqual({
        source: `/org/:slug/settings/${old}`,
        destination: `/org/:slug/settings/${next}`,
        permanent: true,
      });
    }
  });

  it("redirects the six Fees & Trust settings pages to their /settings/fees tabs (616C)", () => {
    for (const [old, tab] of FEES_TAB_ROWS) {
      expect(LEGACY_REDIRECTS).toContainEqual({
        source: `/org/:slug/settings/${old}`,
        destination: `/org/:slug/settings/fees?tab=${tab}`,
        permanent: true,
      });
    }
  });

  it("routes each old Documents URL to the right row (616C)", () => {
    expect(firstMatch("/org/x/settings/templates")?.destination).toBe(
      "/org/:slug/settings/documents?tab=templates"
    );
    expect(firstMatch("/org/x/settings/templates/new")?.destination).toBe(
      "/org/:slug/settings/documents/templates/new"
    );
    expect(firstMatch("/org/x/settings/templates/1/edit")?.destination).toBe(
      "/org/:slug/settings/documents/templates/:id/edit"
    );
    // /settings/templates/:id never had a page — still no row (404 as before).
    expect(firstMatch("/org/x/settings/templates/1")).toBeUndefined();
    // The new family pages and editor routes are not redirect sources; Matters' templates editor
    // (/settings/matters/templates/:id) is untouched by the Documents rows.
    expect(firstMatch("/org/x/settings/documents")).toBeUndefined();
    expect(firstMatch("/org/x/settings/documents/templates/1/edit")).toBeUndefined();
    expect(firstMatch("/org/x/settings/matters/templates/1")).toBeUndefined();
    expect(firstMatch("/org/x/settings/fees")).toBeUndefined();
  });

  it("leaves the ten Documents/Fees pages deleted and serves both families + the editors (616C)", () => {
    const settingsDir = path.resolve(__dirname, "../../app/(app)/org/[slug]/settings");
    for (const [old] of [...DOCUMENTS_TAB_ROWS, ...FEES_TAB_ROWS]) {
      expect(fs.existsSync(path.join(settingsDir, old)), old).toBe(false);
    }
    expect(fs.existsSync(path.join(settingsDir, "documents", "page.tsx"))).toBe(true);
    expect(fs.existsSync(path.join(settingsDir, "fees", "page.tsx"))).toBe(true);
    for (const [, next] of DOCUMENTS_ROUTE_ROWS) {
      const dir = next.replace(/:id/g, "[id]");
      expect(fs.existsSync(path.join(settingsDir, dir, "page.tsx")), next).toBe(true);
    }
  });

  // Phase 87 S9 (616D, ADR-341): old Connections settings page → its /settings/connections tab id …
  const CONNECTIONS_TAB_ROWS: [string, string][] = [
    ["integrations", "xero"],
    ["integrations/xero", "xero"],
    ["integrations/mcp", "mcp"],
    ["ai", "ai"],
    ["notifications", "notifications"],
    ["packs", "packs"],
    ["automations", "automations"],
  ];
  // … and old log route → its route under /settings/connections.
  const CONNECTIONS_ROUTE_ROWS: [string, string][] = [
    ["integrations/xero/sync-log", "connections/xero/sync-log"],
    ["ai/history", "connections/ai/history"],
    ["automations/executions", "connections/automations/executions"],
  ];

  it("redirects the seven Connections settings pages to their /settings/connections tabs (616D)", () => {
    for (const [old, tab] of CONNECTIONS_TAB_ROWS) {
      expect(LEGACY_REDIRECTS).toContainEqual({
        source: `/org/:slug/settings/${old}`,
        destination: `/org/:slug/settings/connections?tab=${tab}`,
        permanent: true,
      });
    }
  });

  it("redirects the three Connections log routes under /settings/connections (616D)", () => {
    for (const [old, next] of CONNECTIONS_ROUTE_ROWS) {
      expect(LEGACY_REDIRECTS).toContainEqual({
        source: `/org/:slug/settings/${old}`,
        destination: `/org/:slug/settings/${next}`,
        permanent: true,
      });
    }
  });

  it("routes each old Connections URL to the right row (616D)", () => {
    // Whole-path matching: /settings/integrations does not swallow its former child pages.
    expect(firstMatch("/org/x/settings/integrations")?.destination).toBe(
      "/org/:slug/settings/connections?tab=xero"
    );
    expect(firstMatch("/org/x/settings/integrations/xero")?.destination).toBe(
      "/org/:slug/settings/connections?tab=xero"
    );
    expect(firstMatch("/org/x/settings/integrations/xero/sync-log")?.destination).toBe(
      "/org/:slug/settings/connections/xero/sync-log"
    );
    expect(firstMatch("/org/x/settings/ai/history")?.destination).toBe(
      "/org/:slug/settings/connections/ai/history"
    );
    expect(firstMatch("/org/x/settings/automations/executions")?.destination).toBe(
      "/org/:slug/settings/connections/automations/executions"
    );
    // The new family page and its kept routes are not redirect sources.
    expect(firstMatch("/org/x/settings/connections")).toBeUndefined();
    expect(firstMatch("/org/x/settings/connections/xero/sync-log")).toBeUndefined();
    expect(firstMatch("/org/x/settings/connections/ai/history")).toBeUndefined();
    expect(firstMatch("/org/x/settings/connections/automations/executions")).toBeUndefined();
  });

  it("leaves the seven Connections pages deleted and serves the family + its three routes (616D)", () => {
    const settingsDir = path.resolve(__dirname, "../../app/(app)/org/[slug]/settings");
    for (const dir of ["integrations", "ai", "notifications", "packs", "automations"]) {
      expect(fs.existsSync(path.join(settingsDir, dir)), dir).toBe(false);
    }
    expect(fs.existsSync(path.join(settingsDir, "connections", "page.tsx"))).toBe(true);
    for (const [, next] of CONNECTIONS_ROUTE_ROWS) {
      expect(fs.existsSync(path.join(settingsDir, next, "page.tsx")), next).toBe(true);
    }
  });

  it("holds the 43 Phase 87 settings rows — 32 tab + 11 route (616D, §12.7.2)", () => {
    const settingsRows = ROWS.filter(
      (r) =>
        r.source.startsWith("/org/:slug/settings/") &&
        r.destination.startsWith("/org/:slug/settings/")
    );
    expect(settingsRows).toHaveLength(43);
    expect(settingsRows.filter((r) => r.destination.includes("?tab="))).toHaveLength(32);
    expect(settingsRows.filter((r) => !r.destination.includes("?tab="))).toHaveLength(11);
  });

  it("leaves exactly 17 settings page.tsx — 5 families + 11 sub-routes + index (616D, §12.7.2)", () => {
    const settingsDir = path.resolve(__dirname, "../../app/(app)/org/[slug]/settings");
    const pages = fs
      .readdirSync(settingsDir, { recursive: true, encoding: "utf8" })
      .filter((entry) => path.basename(entry) === "page.tsx");
    expect(pages).toHaveLength(17);
  });

  it("keeps the pre-Phase-87 /settings/team row", () => {
    expect(LEGACY_REDIRECTS).toContainEqual({
      source: "/org/:slug/settings/team",
      destination: "/org/:slug/team",
      permanent: true,
    });
  });

  it("is exactly what next.config.ts redirects() returns", async () => {
    expect(await nextConfig.redirects!()).toEqual([...LEGACY_REDIRECTS]);
  });
});
