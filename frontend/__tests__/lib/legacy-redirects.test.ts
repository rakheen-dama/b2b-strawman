import { describe, it, expect } from "vitest";
import nextConfig from "@/next.config";
import { LEGACY_REDIRECTS } from "@/lib/legacy-redirects";

// Phase 87 §12.10.5: one committed redirect table, append-only across 610D/612A/613D/615E/616.
describe("LEGACY_REDIRECTS", () => {
  it("has unique sources", () => {
    const sources = LEGACY_REDIRECTS.map((r) => r.source);
    expect(new Set(sources).size).toBe(sources.length);
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
