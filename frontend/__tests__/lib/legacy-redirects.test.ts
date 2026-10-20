import { describe, it, expect } from "vitest";
import fs from "node:fs";
import path from "node:path";
import nextConfig from "@/next.config";
import { LEGACY_REDIRECTS } from "@/lib/legacy-redirects";

// Widened row view so the guards typecheck before the first `has` row lands (613D).
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
