import { describe, it, expect } from "vitest";
import { findLiterals, readSourceTree } from "./helpers/source-scan";

// Phase 87 S4 (610E.1, §12.10.2 hrefs row; scan shape per §12.10.5): the org route segments are
// /matters and /clients (610D moved the directories; lib/legacy-redirects.ts answers the old URLs
// with a 308 for one release). No source file builds a /projects or /customers href, router.push, redirect or
// revalidatePath any more — links go straight to the canonical URL.
//
// Flagged: a `/projects` or `/customers` path SEGMENT — followed by `/`, `?`, `#` or the end of the
// literal chunk — in every string literal ("…", '…'), every chunk of a template literal (head,
// middle, tail: `/org/${slug}/projects/${id}` is caught in its middle span) and JSX text, in app/,
// components/, lib/, hooks/.
// Not flagged: `/project-templates`, `/project-naming` (settings routes; renamed in 616); bare
// `"projects"` keys (terminology 611, tab ids 615E, ADR-342 EntityType); prose such as
// "projects/customers:"; comments; import / dynamic-import specifiers (`@/components/projects/…`).
// Not scanned: tests (`__tests__/`, `*.test.*`, `*.spec.*`) and e2e/ (Playwright URLs are 610G).
// Allowed: lib/legacy-redirects.ts — its `source` rows ARE the legacy URLs.

const SCAN_ROOTS = ["app", "components", "lib", "hooks"];
const ALLOWED = new Set(["lib/legacy-redirects.ts"]);
const LEGACY_SEGMENT = /\/(projects|customers)(?=[/?#]|$)/;

const sourceFiles = readSourceTree(SCAN_ROOTS);

describe("no legacy /projects | /customers route hrefs (Phase 87 610E)", () => {
  it("detector: catches every href form, skips specifiers, comments, settings routes and prose", () => {
    const sample = [
      'import { ProjectTabs } from "@/components/projects/project-tabs";',
      'const lazy = () => import("@/components/customers/client-header-card");',
      "// router.push(`/org/${slug}/projects/${id}`)",
      "const tpl = `/org/${slug}/settings/project-templates/${id}`;",
      'console.error("Failed to load projects/customers:", err);',
      'const labels = { projects: "Projects", customers: "Customers" };',
      "const list = `/org/${slug}/projects`;",
      "const detail = `/org/${slug}/customers/${id}?tab=invoices`;",
      "const plain = '/org/acme/projects?new=1';",
      "const rel = `/customers/${id}`;",
      'const el = <Link href="/org/acme/projects#top">Matters</Link>;',
      'const nested = `/org/${slug}/customers${qs ? `?${qs}` : ""}`;',
    ].join("\n");
    expect(findLiterals("sample.tsx", sample, LEGACY_SEGMENT).map((h) => h.split(":")[1])).toEqual([
      "7",
      "8",
      "9",
      "10",
      "11",
      "12",
    ]);
  });

  it("scans the real source tree", () => {
    expect(sourceFiles.length).toBeGreaterThan(500);
    expect(sourceFiles.some((f) => f.rel === "lib/legacy-redirects.ts")).toBe(true);
    expect(sourceFiles.some((f) => f.rel === "lib/nav-items.ts")).toBe(true);
  });

  it("the redirect table still carries the legacy sources (allowlist is live, not stale)", () => {
    const table = sourceFiles.find((f) => f.rel === "lib/legacy-redirects.ts")!;
    expect(findLiterals(table.rel, table.text, LEGACY_SEGMENT)).toHaveLength(2);
  });

  it("zero legacy route segments outside lib/legacy-redirects.ts", () => {
    const hits = sourceFiles
      .filter((f) => !ALLOWED.has(f.rel))
      .flatMap((f) => findLiterals(f.rel, f.text, LEGACY_SEGMENT));
    expect(hits).toEqual([]);
  });
});

// Phase 87 S6b (613D, ADR-339): /calendar and /court-calendar are deleted — one Diary at /diary
// (308 redirects in lib/legacy-redirects.ts). No source file links to either old route.
const LEGACY_CALENDAR = /\/(calendar|court-calendar)(?=[/?#]|$)/;

describe("no legacy /calendar | /court-calendar hrefs (Phase 87 613D)", () => {
  it("detector: catches both routes, skips the Diary and module ids", () => {
    const sample = [
      'const mod = "court_calendar";',
      "const diary = `/org/${slug}/diary?types=COURT_DATE,PRESCRIPTION`;",
      'import { X } from "@/components/legal/court-calendar-view";',
      "const a = `/org/${slug}/calendar`;",
      "const b = `/org/${slug}/court-calendar?projectId=${id}`;",
    ].join("\n");
    expect(findLiterals("sample.tsx", sample, LEGACY_CALENDAR).map((h) => h.split(":")[1])).toEqual(
      ["4", "5"]
    );
  });

  it("the redirect table carries exactly the three calendar sources", () => {
    const table = sourceFiles.find((f) => f.rel === "lib/legacy-redirects.ts")!;
    expect(findLiterals(table.rel, table.text, LEGACY_CALENDAR)).toHaveLength(3);
  });

  it("zero calendar route segments outside lib/legacy-redirects.ts", () => {
    const hits = sourceFiles
      .filter((f) => !ALLOWED.has(f.rel))
      .flatMap((f) => findLiterals(f.rel, f.text, LEGACY_CALENDAR));
    expect(hits).toEqual([]);
  });
});
