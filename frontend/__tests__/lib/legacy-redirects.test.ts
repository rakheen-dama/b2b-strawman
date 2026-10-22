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
