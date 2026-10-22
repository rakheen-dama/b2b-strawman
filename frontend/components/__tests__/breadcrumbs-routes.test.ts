import { describe, it, expect } from "vitest";
import fs from "node:fs";
import path from "node:path";
import { NON_ROUTABLE_PREFIXES, isLinkableBreadcrumbPrefix } from "@/components/breadcrumbs";

// Guard: breadcrumbs link every intermediate path segment, so every intermediate
// prefix under /org/[slug]/ must either have a page.tsx or be listed in
// NON_ROUTABLE_PREFIXES. Adding a nested route without an index page fails here.
const ORG_ROUTES_DIR = path.resolve(__dirname, "../../app/(app)/org/[slug]");

/** Route patterns (relative to /org/[slug]/, route groups stripped) that have a page.tsx. */
function collectRoutePatterns(dir: string, segments: string[] = []): string[] {
  const patterns: string[] = [];
  if (segments.length > 0 && fs.existsSync(path.join(dir, "page.tsx"))) {
    patterns.push(segments.join("/"));
  }
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const isRouteGroup = entry.name.startsWith("(") && entry.name.endsWith(")");
    patterns.push(
      ...collectRoutePatterns(
        path.join(dir, entry.name),
        isRouteGroup ? segments : [...segments, entry.name]
      )
    );
  }
  return patterns;
}

const routePatterns = new Set(collectRoutePatterns(ORG_ROUTES_DIR));

// Every proper, non-empty prefix of a route is rendered as an intermediate crumb.
const intermediatePrefixes = new Set(
  [...routePatterns].flatMap((pattern) => {
    const parts = pattern.split("/");
    return parts.slice(1).map((_, i) => parts.slice(0, i + 1).join("/"));
  })
);

const pagelessPrefixes = [...intermediatePrefixes].filter((p) => !routePatterns.has(p)).sort();
const routablePrefixes = [...intermediatePrefixes].filter((p) => routePatterns.has(p)).sort();

describe("breadcrumb prefixes vs app/(app)/org/[slug] routes", () => {
  it("finds the route tree", () => {
    expect(routePatterns.has("dashboard")).toBe(true);
    expect(routablePrefixes).toContain("settings");
  });

  it("NON_ROUTABLE_PREFIXES mirrors the pageless intermediate prefixes on disk", () => {
    expect([...NON_ROUTABLE_PREFIXES].sort()).toEqual(pagelessPrefixes);
  });

  it.each(pagelessPrefixes)("does not link pageless prefix %s", (prefix) => {
    expect(isLinkableBreadcrumbPrefix(prefix.split("/"))).toBe(false);
  });

  it.each(routablePrefixes)("links prefix with a page %s", (prefix) => {
    expect(isLinkableBreadcrumbPrefix(prefix.split("/"))).toBe(true);
  });

  it("matches dynamic pattern segments against concrete ids", () => {
    expect(
      isLinkableBreadcrumbPrefix([
        "settings",
        "documents",
        "templates",
        "3f1c2a4b-5d6e-4f70-8a9b-0c1d2e3f4a5b",
      ])
    ).toBe(false);
    expect(isLinkableBreadcrumbPrefix(["settings", "documents"])).toBe(true);
  });
});
