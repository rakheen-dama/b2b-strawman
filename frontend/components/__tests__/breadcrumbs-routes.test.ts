import { describe, it, expect } from "vitest";
import fs from "node:fs";
import path from "node:path";
import {
  NON_ROUTABLE_PREFIXES,
  PARENT_SEGMENT_FALLBACKS,
  SEGMENT_LABELS,
  isLinkableBreadcrumbPrefix,
} from "@/components/breadcrumbs";

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

// Every literal (non-dynamic) segment of a route that has a page.tsx.
const segmentsOnDisk = new Set(
  [...routePatterns].flatMap((p) => p.split("/")).filter((s) => !s.startsWith("["))
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

describe("SEGMENT_LABELS vs app/(app)/org/[slug] routes (616E)", () => {
  it("labels only segments that exist on disk — no orphans for deleted routes", () => {
    expect(Object.keys(SEGMENT_LABELS).filter((k) => !segmentsOnDisk.has(k))).toEqual([]);
  });

  it("labels every settings family and kept settings sub-route segment", () => {
    const settingsSegments = new Set(
      [...routePatterns]
        .filter((p) => p === "settings" || p.startsWith("settings/"))
        .flatMap((p) => p.split("/"))
        .filter((s) => !s.startsWith("[") && s !== "new" && s !== "edit")
    );
    expect(settingsSegments.size).toBeGreaterThan(5);
    for (const s of settingsSegments) expect(SEGMENT_LABELS[s], s).toBeDefined();
  });
});

// Phase 88 628B (628.4, R-4): the crumb label is SEGMENT_LABELS[segment] ?? the parent's fallback
// ?? the raw segment. These two invariants make the raw-segment branch unreachable for any route on
// disk: every literal segment is labelled, and every [param] segment's parent has a fallback.
describe("every org/[slug]/** crumb has a label (628B)", () => {
  // The literal segment directly above each dynamic segment ("matters" for matters/[id]).
  const paramParents = new Set(
    [...routePatterns].flatMap((p) => {
      const parts = p.split("/");
      return parts.flatMap((part, i) => (part.startsWith("[") && i > 0 ? [parts[i - 1]] : []));
    })
  );

  it("finds the dynamic routes", () => {
    expect(paramParents.has("matters")).toBe(true);
    expect(paramParents.has("reports")).toBe(true);
  });

  it("labels every literal segment on disk", () => {
    expect([...segmentsOnDisk].filter((s) => SEGMENT_LABELS[s] === undefined).sort()).toEqual([]);
  });

  it("has a fallback for the parent of every [param] segment", () => {
    expect(
      [...paramParents].filter((s) => PARENT_SEGMENT_FALLBACKS[s] === undefined).sort()
    ).toEqual([]);
  });
});
