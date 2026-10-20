import fs from "node:fs";
import path from "node:path";
import ts from "typescript";
import { describe, it, expect } from "vitest";

// Phase 87 S4 (610E.1, §12.6.5 / §12.10.6): the org route segments are /matters and /clients
// (610D moved the directories; lib/legacy-redirects.ts answers the old URLs with a 308 for one
// release). No source file builds a /projects or /customers href, router.push, redirect or
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

const FRONTEND_ROOT = path.resolve(__dirname, "..");
const SCAN_ROOTS = ["app", "components", "lib", "hooks"];
const ALLOWED = new Set(["lib/legacy-redirects.ts"]);
const LEGACY_SEGMENT = /\/(projects|customers)(?=[/?#]|$)/;

function collectSourceFiles(dir: string, out: string[] = []): string[] {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === "__tests__" || entry.name === "node_modules") continue;
      collectSourceFiles(full, out);
    } else if (/\.tsx?$/.test(entry.name) && !/\.(test|spec)\.tsx?$/.test(entry.name)) {
      out.push(full);
    }
  }
  return out;
}

function isModuleSpecifier(node: ts.Node): boolean {
  const parent = node.parent;
  if (!parent) return false;
  if (ts.isImportDeclaration(parent) || ts.isExportDeclaration(parent)) {
    return parent.moduleSpecifier === node;
  }
  if (ts.isExternalModuleReference(parent)) return true;
  if (ts.isLiteralTypeNode(parent) && ts.isImportTypeNode(parent.parent)) return true;
  return ts.isCallExpression(parent) && parent.expression.kind === ts.SyntaxKind.ImportKeyword;
}

/** `file:line: text` for every non-comment, non-specifier literal chunk matching `pattern`. */
function findLiterals(fileName: string, source: string, pattern: RegExp): string[] {
  const kind = fileName.endsWith(".tsx") ? ts.ScriptKind.TSX : ts.ScriptKind.TS;
  const sf = ts.createSourceFile(fileName, source, ts.ScriptTarget.Latest, true, kind);
  const hits: string[] = [];
  const visit = (node: ts.Node) => {
    const isText =
      ts.isStringLiteralLike(node) || ts.isTemplateLiteralToken(node) || ts.isJsxText(node);
    if (isText && !isModuleSpecifier(node) && pattern.test(node.text)) {
      const { line } = sf.getLineAndCharacterOfPosition(node.getStart(sf));
      hits.push(`${fileName}:${line + 1}: ${node.text.trim()}`);
    }
    ts.forEachChild(node, visit);
  };
  visit(sf);
  return hits;
}

const sourceFiles = SCAN_ROOTS.flatMap((root) =>
  collectSourceFiles(path.join(FRONTEND_ROOT, root))
).map((full) => ({
  rel: path.relative(FRONTEND_ROOT, full).split(path.sep).join("/"),
  text: fs.readFileSync(full, "utf8"),
}));

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
